#!/usr/bin/env python3
"""Submit a signed Counter POS APK as an update to an existing Galaxy Store listing."""
from __future__ import annotations

import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

import jwt
import requests

API_ROOT = "https://devapi.samsungapps.com"
EXPECTED_PACKAGE = "com.alvinb.counterpos"
REQUEST_TIMEOUT = (15, 90)
PRESERVED_METADATA_FIELDS = (
    "appTitle",
    "longDescription",
    "shortDescription",
    "newFeature",
    "ageLimit",
    "chinaAgeLimit",
    "exceptionAppYn",
    "majorChangeYn",
    "majorChangeDesc",
    "majorChangeDateTime",
    "openSourceURL",
    "privatePolicyURL",
    "youTubeURL",
    "copyrightHolder",
    "supportEMail",
    "supportedSiteUrl",
    "standardPrice",
    "autoAddCountry",
    "startPublicationDate",
    "stopPublicationDate",
    "reviewComment",
    "notifyResult",
    "supportedLanguages",
    "miitData",
    "miitDataLang",
)


class PublishError(RuntimeError):
    """A safe-to-display error that never includes credentials or access tokens."""


def _safe_message(response: requests.Response) -> str:
    """Return only documented, non-secret error fields from a Samsung response."""
    try:
        body = response.json()
    except ValueError:
        return f"HTTP {response.status_code}"
    if not isinstance(body, dict):
        return f"HTTP {response.status_code}"
    parts = []
    for key in ("errorCode", "errorMsg", "resultCode", "resultMessage"):
        value = body.get(key)
        if value not in (None, "", "0000"):
            parts.append(f"{key}={str(value)[:300]}")
    return "; ".join(parts) or f"HTTP {response.status_code}"


def _decode_response(response: requests.Response, operation: str):
    if not 200 <= response.status_code < 300:
        raise PublishError(f"Samsung {operation} failed: {_safe_message(response)}")
    if response.status_code == 204 or not response.content:
        return None
    try:
        body = response.json()
    except ValueError as exc:
        raise PublishError(f"Samsung {operation} returned non-JSON content (HTTP {response.status_code}).") from exc
    if isinstance(body, dict):
        code = body.get("errorCode") or body.get("resultCode")
        if code not in (None, "", "0000"):
            raise PublishError(f"Samsung {operation} failed: {_safe_message(response)}")
        if body.get("ok") is False:
            raise PublishError(f"Samsung {operation} failed: {_safe_message(response)}")
    return body


def _request(session: requests.Session, operation: str, method: str, url: str, **kwargs):
    try:
        response = session.request(method, url, timeout=REQUEST_TIMEOUT, **kwargs)
    except requests.RequestException as exc:
        raise PublishError(
            f"Samsung {operation} request failed ({type(exc).__name__}). The result may be uncertain; "
            "check Seller Portal before retrying this release."
        ) from None
    return _decode_response(response, operation)


def _load_config() -> dict[str, str]:
    values = {
        "service_account_id": os.environ.get("SAMSUNG_SERVICE_ACCOUNT_ID", "").strip(),
        "private_key": os.environ.get("SAMSUNG_SERVICE_ACCOUNT_PRIVATE_KEY", "").strip(),
        "content_id": os.environ.get("SAMSUNG_CONTENT_ID", "").strip(),
        "apk_path": os.environ.get("GALAXY_APK_PATH", "app/build/outputs/apk/release/CounterPOS.apk").strip(),
        "version_code": os.environ.get("ANDROID_VERSION_CODE", "").strip(),
        "version_name": os.environ.get("ANDROID_VERSION_NAME", "").strip(),
    }
    required_environment = {
        "SAMSUNG_SERVICE_ACCOUNT_ID": values["service_account_id"],
        "SAMSUNG_SERVICE_ACCOUNT_PRIVATE_KEY": values["private_key"],
        "SAMSUNG_CONTENT_ID": values["content_id"],
        "ANDROID_VERSION_CODE": values["version_code"],
    }
    for env_name, value in required_environment.items():
        if not value:
            raise PublishError(f"Missing required GitHub Actions secret or environment value: {env_name}.")

    if not re.fullmatch(r"\d{12}", values["content_id"]):
        raise PublishError("SAMSUNG_CONTENT_ID must be the 12-digit ID of an app already registered in Seller Portal.")
    try:
        version_code = int(values["version_code"])
    except ValueError as exc:
        raise PublishError("ANDROID_VERSION_CODE must be an integer.") from exc
    if version_code <= 0:
        raise PublishError("ANDROID_VERSION_CODE must be greater than zero.")

    key_text = values["private_key"].replace("\\n", "\n")
    if key_text.startswith("{"):
        try:
            key_object = json.loads(key_text)
        except json.JSONDecodeError as exc:
            raise PublishError("SAMSUNG_SERVICE_ACCOUNT_PRIVATE_KEY is not valid PEM or JSON key data.") from exc
        key_text = key_object.get("private_key") or key_object.get("privateKey") or key_object.get("key") or ""
    if "BEGIN" not in key_text or "PRIVATE KEY" not in key_text:
        raise PublishError("SAMSUNG_SERVICE_ACCOUNT_PRIVATE_KEY must contain the service-account PEM private key.")
    values["private_key"] = key_text
    values["version_code"] = str(version_code)

    apk_path = Path(values["apk_path"])
    if not apk_path.is_file() or apk_path.stat().st_size == 0:
        raise PublishError(f"Signed APK was not found or is empty: {apk_path}.")
    values["apk_path"] = str(apk_path)
    return values


def _create_access_token(session: requests.Session, service_account_id: str, private_key: str) -> str:
    now = int(time.time())
    try:
        signed_jwt = jwt.encode(
            {"iss": service_account_id, "scopes": ["publishing"], "iat": now, "exp": now + 1200},
            private_key,
            algorithm="RS256",
            headers={"typ": "JWT"},
        )
    except Exception as exc:  # PyJWT/cryptography errors can vary by key format.
        raise PublishError(f"Could not sign Samsung's short-lived service-account JWT ({type(exc).__name__}).") from None

    body = _request(
        session,
        "access-token request",
        "POST",
        f"{API_ROOT}/auth/accessToken",
        headers={"Authorization": f"Bearer {signed_jwt}", "Content-Type": "application/json"},
        json={},
    )
    token = None
    if isinstance(body, dict):
        item = body.get("createdItem") or {}
        if isinstance(item, dict):
            token = item.get("accessToken")
        token = token or body.get("accessToken")
    if not isinstance(token, str) or not token:
        raise PublishError("Samsung's access-token response did not include an access token.")
    return token


def _headers(service_account_id: str, access_token: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {access_token}",
        "service-account-id": service_account_id,
        "Accept": "application/json",
    }


def _content_details(body) -> dict:
    if isinstance(body, list) and body and isinstance(body[0], dict):
        return body[0]
    if isinstance(body, dict):
        for key in ("data", "contentInfo", "contents"):
            value = body.get(key)
            if isinstance(value, list) and value and isinstance(value[0], dict):
                return value[0]
            if isinstance(value, dict) and value.get("contentId"):
                return value
        return body
    raise PublishError("Samsung contentInfo returned no app listing details.")


def _yn(value, field: str) -> str:
    if value is True:
        return "Y"
    if value is False:
        return "N"
    text = str(value).strip().upper()
    if text in {"Y", "N"}:
        return text
    raise PublishError(f"The registered app listing is missing a valid {field} value.")


def _binary_metadata(details: dict) -> dict:
    status = str(details.get("contentStatus", "")).strip().upper().replace(" ", "_")
    if status not in {"FOR_SALE", "SALE"}:
        if not status and str(details.get("appStatus", "")).strip().upper() == "SALE":
            status = "FOR_SALE"
        else:
            raise PublishError(
                f"Galaxy Store listing status is {status or 'unknown'}, not For Sale. "
                "An update may already be in progress; check Seller Portal before retrying."
            )

    binaries = details.get("binaryList")
    if not isinstance(binaries, list) or not binaries:
        raise PublishError("Samsung contentInfo returned no existing binary to use for the app update.")

    matching = [item for item in binaries if isinstance(item, dict) and item.get("packageName") == EXPECTED_PACKAGE]
    if not matching:
        raise PublishError(f"The Seller Portal listing does not contain Android package {EXPECTED_PACKAGE}.")
    try:
        current = max(matching, key=lambda item: int(str(item.get("versionCode", ""))))
        current_code = int(str(current["versionCode"]))
    except (KeyError, TypeError, ValueError) as exc:
        raise PublishError("The existing Galaxy Store binary does not expose a valid versionCode.") from exc

    gms = _yn(current.get("gms"), "GMS")
    binary_seq = current.get("binarySeq")
    return {"current_version_code": current_code, "gms": gms, "binary_seq": str(binary_seq) if binary_seq else ""}


def _update_payload(details: dict, content_id: str) -> dict:
    try:
        default_language = details["defaultLanguageCode"]
        paid = _yn(details["paid"], "paid")
        publication_type = str(details["publicationType"])
        privacy_url = details.get("privatePolicyURL", "")
        privacy_url_flag = details.get("privatePolicyURLYN")
        if privacy_url_flag is None:
            privacy_url_flag = "Y" if str(privacy_url).strip() else "N"
        privacy_url_flag = _yn(privacy_url_flag, "privatePolicyURLYN")
        export_laws = details["usExportLaws"]
    except KeyError as exc:
        raise PublishError(f"Samsung contentInfo is missing required listing field {exc.args[0]}.") from exc

    if not isinstance(default_language, str) or not default_language:
        raise PublishError("The existing listing has no defaultLanguageCode.")
    if publication_type not in {"01", "02", "03"}:
        raise PublishError("The existing listing has an unsupported publicationType; refusing to change it.")
    if isinstance(export_laws, str):
        normalized = export_laws.strip().upper()
        if normalized not in {"Y", "N"}:
            raise PublishError("The existing listing has an invalid usExportLaws value.")
        export_laws = normalized == "Y"
    if not isinstance(export_laws, bool):
        raise PublishError("The existing listing does not expose a valid usExportLaws value.")

    # Keep writable listing metadata unchanged; Samsung documents these three "null" values
    # as the way to retain current languages, screenshots, and distribution countries.
    payload = {name: details[name] for name in PRESERVED_METADATA_FIELDS if name in details}
    payload.update({
        "contentId": content_id,
        "defaultLanguageCode": default_language,
        "paid": paid,
        "publicationType": publication_type,
        "privatePolicyURLYN": privacy_url_flag,
        "usExportLaws": export_laws,
        "sellCountryList": "null",
        "addLanguage": "null",
        "screenshots": "null",
    })
    return payload


def _publication_description(publication_type: str) -> str:
    return {
        "01": "automatic publication after Samsung's pre-review phase",
        "02": "publication on the date already configured in Seller Portal",
        "03": "manual publication after review; a seller action will still be required",
    }[publication_type]


def _write_summary(message: str) -> None:
    print(message)
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as output:
            output.write(f"\n{message}\n")


def publish() -> None:
    config = _load_config()
    session = requests.Session()
    access_token = _create_access_token(
        session, config["service_account_id"], config["private_key"]
    )
    headers = _headers(config["service_account_id"], access_token)

    info_body = _request(
        session,
        "contentInfo lookup",
        "GET",
        f"{API_ROOT}/seller/contentInfo",
        headers=headers,
        params={"contentId": config["content_id"]},
    )
    details = _content_details(info_body)
    binary = _binary_metadata(details)
    new_version_code = int(config["version_code"])
    if new_version_code <= binary["current_version_code"]:
        raise PublishError(
            f"APK versionCode {new_version_code} is not greater than the current Galaxy Store versionCode "
            f"{binary['current_version_code']}. No update was submitted."
        )
    update = _update_payload(details, config["content_id"])

    session_body = _request(
        session,
        "upload-session creation",
        "POST",
        f"{API_ROOT}/seller/createUploadSessionId",
        headers=headers,
    )
    if not isinstance(session_body, dict) or not session_body.get("sessionId") or not session_body.get("url"):
        raise PublishError("Samsung did not return a valid upload session ID and URL.")
    upload_url = str(session_body["url"])
    parsed_upload_url = urlparse(upload_url)
    if parsed_upload_url.scheme != "https" or parsed_upload_url.hostname != "seller.samsungapps.com" or parsed_upload_url.path != "/galaxyapi/fileUpload":
        raise PublishError("Samsung returned an unexpected upload host or path; refusing to send the APK or credentials.")

    apk_path = Path(config["apk_path"])
    try:
        with apk_path.open("rb") as apk_file:
            upload_response = session.post(
                upload_url,
                headers=headers,
                data={"sessionId": session_body["sessionId"]},
                files={"file": (apk_path.name, apk_file, "application/vnd.android.package-archive")},
                timeout=REQUEST_TIMEOUT,
            )
    except requests.RequestException as exc:
        raise PublishError(
            f"Samsung APK upload failed ({type(exc).__name__}). Check Seller Portal before retrying."
        ) from None
    upload_body = _decode_response(upload_response, "APK upload")
    if not isinstance(upload_body, dict) or not upload_body.get("fileKey"):
        raise PublishError("Samsung's upload response did not include a fileKey.")

    _request(
        session,
        "contentUpdate registration",
        "POST",
        f"{API_ROOT}/seller/contentUpdate",
        headers={**headers, "Content-Type": "application/json"},
        json=update,
    )

    binary_payload = {
        "contentId": config["content_id"],
        "gms": binary["gms"],
        "filekey": upload_body["fileKey"],
    }
    if binary["binary_seq"]:
        binary_payload["binarySeqForDeviceInfo"] = binary["binary_seq"]
    _request(
        session,
        "new binary registration",
        "POST",
        f"{API_ROOT}/seller/v2/content/binary",
        headers={**headers, "Content-Type": "application/json"},
        json=binary_payload,
    )

    _request(
        session,
        "review submission",
        "POST",
        f"{API_ROOT}/seller/contentSubmit",
        headers={**headers, "Content-Type": "application/json"},
        json={"contentId": config["content_id"]},
    )

    version_name = config["version_name"] or "(tagged release)"
    _write_summary(
        "Galaxy Store update submitted for Samsung review: "
        f"version {version_name}, versionCode {new_version_code}. "
        f"Existing publication setting retained: {_publication_description(update['publicationType'])}. "
        "This workflow does not bypass Samsung review."
    )


def main() -> int:
    try:
        publish()
        return 0
    except PublishError as exc:
        print(f"Galaxy Store publishing stopped: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
