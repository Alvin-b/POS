# Counter POS store publishing

## Galaxy Store: one-time setup

Samsung's Content Publish API manages apps that are already registered; it cannot create the first listing. Before automated updates can work, the Seller Portal account must have commercial-seller status and the owner must register Counter POS in Seller Portal. Confirm the package ID is `com.alvinb.counterpos`, complete the listing and policy fields, choose the distribution countries, and copy the listing's 12-digit `contentId` from the Apps page. The first listing and its initial review remain a manual Seller Portal step.

Create a Seller Portal API service account with the **Publishing & ITEM** scope and download its private key. Samsung says the private key is not stored in Seller Portal after the creation window closes, so keep the downloaded key in a secure password manager. Do not commit it or paste it into chat.

Add these repository Actions secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `ANDROID_SIGNING_BUNDLE` | The existing release-signing bundle required by the signed APK workflow; keep using the same keystore for every update. |
| `SAMSUNG_SERVICE_ACCOUNT_ID` | The service-account ID from Seller Portal. |
| `SAMSUNG_SERVICE_ACCOUNT_PRIVATE_KEY` | The service-account PEM private key. A JSON key file is also accepted if it contains a `private_key`, `privateKey`, or `key` field. |
| `SAMSUNG_CONTENT_ID` | The registered app's 12-digit content ID. |

Never use Daraja/M-Pesa credentials here. Samsung credentials belong only in GitHub Actions secrets and are never included in the APK.

## Automated release behavior

The Galaxy Store submission runs only after a successful, signed `v*` release build. It verifies the Seller Portal listing is currently **For Sale**, confirms that its package ID matches Counter POS, and refuses to proceed if another update is already in progress or if the APK version code is not greater than the current store version. It copies the existing binary's GMS and device-group settings, preserves the listing's countries, screenshots, languages, paid/free status, and publication type, registers the signed APK, and submits the update for Samsung review.

The release workflow uses a high, monotonically increasing Android version code (`1,000,000 + GitHub release-workflow run number`) so store versions remain higher than the debug-build run numbers. Debug APKs and production APKs use different signing keys: users moving from a debug APK to the first signed/store APK must uninstall the debug app first. After that migration, every production update must keep using the same private release key or Android will reject it.

Submitting through the API does **not** bypass Samsung's review. The listing's existing publication setting is preserved: automatic publication follows Samsung's configured review stage; a scheduled setting waits for its configured date; a manual setting still requires the seller to publish it in Seller Portal after review. If an API call times out after changing listing state, check Seller Portal before rerunning the same release.

Run the local, no-network checks with:

```sh
python3 -m pip install -r scripts/requirements-galaxy-store.txt
python3 -m unittest scripts.test_galaxy_store_publisher
```

## Softonic

No Softonic upload workflow is configured. Softonic's official Android publishing guidance says Android apps must be distributed through Google Play; it does not provide a direct APK submission route for this app's GitHub-release distribution model. See [Softonic's Android publishing guidance](https://developers.softonic.com/publish-android-app/) and [publisher version-management help](https://developers.softonic.com/help/manage-versions-and-content/).

## Official Samsung references

- [Developer API prerequisites and supported API base URL](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/get-started-with-the-gsd-api.html)
- [Create an access token](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/create-an-access-token.html)
- [Update submission sequence](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/user-guide-submit-an-update.html)
- [Status values](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/status-parameters-mapping.html)
- [Upload a binary](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/file-upload.html)
- [Register a new binary](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/add-new-binary.html)
- [Submit for review](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/submit-app.html)
