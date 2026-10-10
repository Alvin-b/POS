#!/usr/bin/env python3
"""Extract CounterPOS prototype catalog data and embedded product photos for Android."""
from __future__ import annotations

import argparse
import base64
import json
import re
from collections import Counter
from pathlib import Path


def parse_json_after(text: str, marker: str):
    start = text.index(marker) + len(marker)
    value, consumed = json.JSONDecoder().raw_decode(text[start:])
    return value, start + consumed


def decode_data_uri(uri: str) -> tuple[bytes, str]:
    header, separator, encoded = uri.partition(",")
    if not separator or not header.startswith("data:") or ";base64" not in header:
        raise ValueError("Expected a base64 image data URI")
    mime = header[5:].split(";", 1)[0].lower()
    raw = base64.b64decode(encoded)
    if mime == "image/avif":
        try:
            from io import BytesIO
            from PIL import Image
        except ImportError as error:
            raise RuntimeError("Pillow is required to convert the AVIF catalog image to WebP") from error
        with Image.open(BytesIO(raw)) as image:
            image = image.convert("RGBA" if "A" in image.getbands() else "RGB")
            output = BytesIO()
            image.save(output, format="WEBP", quality=92, method=6)
        return output.getvalue(), "webp"
    extension = {
        "image/png": "png",
        "image/jpeg": "jpg",
        "image/jpg": "jpg",
        "image/webp": "webp",
    }.get(mime)
    if not extension:
        raise ValueError(f"Unsupported image MIME type: {mime}")
    if extension == "png" and not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("PNG signature mismatch")
    if extension == "jpg" and not raw.startswith(b"\xff\xd8"):
        raise ValueError("JPEG signature mismatch")
    if extension == "webp" and not (raw.startswith(b"RIFF") and raw[8:12] == b"WEBP"):
        raise ValueError("WebP signature mismatch")
    return raw, extension


def stock_brand(name: str) -> str:
    if name.startswith("Johnnie Walker"):
        return "Johnnie Walker"
    if name.startswith("Smirnoff Ice"):
        return "Smirnoff Ice"
    if name.startswith("Captain Morgan"):
        return "Captain Morgan"
    if name.startswith("Jameson"):
        return "Jameson"
    if name.startswith("Gilbey's"):
        return "Gilbey's"
    if name.startswith("Gordon's"):
        return "Gordon's"
    if name.startswith("Kenya Cane"):
        return "Kenya Cane"
    if name.startswith("White Cap"):
        return "White Cap"
    return name.split()[0] if name.split() else ""


def stock_category(name: str, kind: str) -> str:
    low = name.lower()
    if "cider" in low:
        return "Cider"
    if "smirnoff ice" in low:
        return "Alcoholic RTD"
    if kind == "spirit":
        return "Spirits"
    return "Beer"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("prototype", type=Path, help="CounterPOSprototype-updated.html")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()

    html = args.prototype.read_text(encoding="utf-8")
    photos, _ = parse_json_after(html, "var PRODUCT_PHOTOS=")
    starter_rows, _ = parse_json_after(html, "var P=")
    master_start = html.index("var MASTER_CATALOG=")
    library_start = html.index(".concat([", master_start) + len(".concat(")
    library_rows, _ = json.JSONDecoder().raw_decode(html[library_start:])

    resources = args.repo / "app/src/main/res/drawable-nodpi"
    assets = args.repo / "app/src/main/assets"
    resources.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    for old in resources.glob("product_*"):
        old.unlink()

    records: list[dict] = []

    def add_image(name: str, uri: str) -> None:
        if not uri:
            raise ValueError(f"Missing product photo for {name}")
        raw, extension = decode_data_uri(uri)
        (resources / f"{name}.{extension}").write_bytes(raw)

    for index, row in enumerate(starter_rows, start=1):
        code, name, size, kind, _body, _label, _text, price, stock = row
        image_name = f"product_stock_{index:03d}"
        add_image(image_name, photos.get(str(code), ""))
        records.append({
            "catalogCode": str(code),
            "verifiedBarcode": None,
            "name": name,
            "brand": stock_brand(name),
            "size": size,
            "category": stock_category(name, kind),
            "suggestedPriceKsh": int(price),
            "sellingPriceKsh": int(price),
            "stockQuantity": int(stock),
            "inMyStock": True,
            "lowStockThreshold": 5,
            "imageAssetName": image_name,
            "productSourceUrl": None,
            "imageSourceUrl": None,
            "verificationNote": "",
        })

    for item in library_rows:
        item_id = int(item["id"])
        image_name = f"product_lib_{item_id}"
        add_image(image_name, item.get("photo", ""))
        suggested = int(item.get("price") or 0)
        records.append({
            "catalogCode": str(item["code"]),
            "verifiedBarcode": item.get("barcode") or None,
            "name": item["name"],
            "brand": item.get("brand") or "",
            "size": item.get("size") or "",
            "category": item.get("category") or "Beverage",
            "suggestedPriceKsh": suggested,
            "sellingPriceKsh": suggested,
            "stockQuantity": 0,
            "inMyStock": False,
            "lowStockThreshold": 5,
            "imageAssetName": image_name,
            "productSourceUrl": item.get("productSourceUrl"),
            "imageSourceUrl": item.get("imageSourceUrl") or item.get("imageUrl"),
            "verificationNote": item.get("verificationNote") or "",
        })

    if len(starter_rows) != 19 or len(library_rows) != 59 or len(records) != 78:
        raise ValueError(f"Unexpected catalog sizes: {len(starter_rows)} starter, {len(library_rows)} library")
    if sum(bool(r["inMyStock"]) for r in records) != 19:
        raise ValueError("Expected exactly 19 initially stocked products")

    catalog_path = assets / "counterpos_catalog.json"
    catalog_path.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    image_files = list(resources.glob("product_*"))
    if len(image_files) != 78:
        raise ValueError(f"Expected 78 extracted photos, found {len(image_files)}")
    total_units = sum(r["stockQuantity"] for r in records if r["inMyStock"])
    print(f"Wrote {catalog_path}")
    print(f"Catalog: {len(records)} products; 19 stocked; 59 in Library; {total_units} opening units")
    print(f"Photos: {len(image_files)} verified image files; {sum(p.stat().st_size for p in image_files):,} bytes")
    print("Categories:", dict(Counter(r["category"] for r in records)))


if __name__ == "__main__":
    main()
