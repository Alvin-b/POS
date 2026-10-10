# Counter POS for Android

This repository replaces the previous nightclub/bar-tabs demo with the CounterPOS beverage point-of-sale prototype as a **native Android app** built with Kotlin, Jetpack Compose, and Room. It is not a WebView wrapper. The current standalone HTML prototype is preserved in [`prototype/CounterPOSprototype-updated.html`](prototype/CounterPOSprototype-updated.html) as a design reference and catalog-import source.

## Screens and workflows

- **Scan** — search stocked products by name, brand, size, catalog code, or verified barcode; a USB/Bluetooth keyboard-wedge scanner can type into the same field. Every tile shows on-hand quantity and selling price. Tap an item to select quantity and record a sale.
- **Sales** — daily KSh sales and units sold, recent sale history, and an **Undo last sale** action that restores stock.
- **Stock → My stock** — product-line count and total units, search, receive stock, quick `+6` / `+24` restock actions, and optional photo override.
- **Stock → Library** — searchable/category-filtered beverage list. Add an item by setting the shop selling price and entering the actual quantity on hand. A product remains in My stock when its quantity reaches zero; it does not return to Library.

Inventory and sales persist locally in Room (`counterpos.db`). The initial catalog is seeded once from `app/src/main/assets/counterpos_catalog.json`.

## Seed catalog and product photos

The app contains **78 photographed products**: 19 initially in My stock (316 opening units) and 59 in Library. The list covers beer/cider/RTDs, spirits, wine, water, soft drinks, juice, energy/sports drinks, and mixers. Milk/dairy and categories 9–12 from the earlier scope were intentionally excluded.

**Scope caveat:** 78 is a researched starter catalog, not yet a complete SKU/size/flavour inventory of every beverage consumed or sold in Kenya. The Library can be extended as additional Kenyan products and variants are verified.

All 78 product photos are bundled in `app/src/main/res/drawable-nodpi/`, so they work offline. One AVIF source was converted to WebP for compatibility with minSdk 24. See [`docs/photo-sources.md`](docs/photo-sources.md) for product and image-source references. Retailer/listing images may have usage restrictions; confirm image rights before public or commercial use.

**Barcode note:** a barcode is stored only when verified. Numeric-looking starter codes and `LIB-…` values are catalog codes, not claims that an item has a verified barcode. Name search works regardless; barcode matching works only after a verified barcode is present in catalog data.

## Updating the imported catalog

The import script rebuilds the Android JSON seed and product photo resources from the bundled prototype:

```bash
python3 scripts/import_prototype_catalog.py prototype/CounterPOSprototype-updated.html
```

The script uses Python's standard library except when converting an AVIF image, in which case install Pillow (`python3 -m pip install Pillow`). It validates image signatures and expected counts before finishing.

## Build and test

Use JDK 21 and Android SDK platform 36 (including the SDK-installed 36.1 extension) with Build Tools 36.0.0. Set `ANDROID_HOME` to the SDK location, then run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug signing configuration intentionally preserves the AI Studio setup and expects an ignored root-level `debug.keystore`. If your development checkout does not provide one, create a **local development-only** default Android debug keystore (do not commit it):

```bash
keytool -genkeypair -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android \
  -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
```

The package/application ID is provisionally `com.alvinb.counterpos` based on the repository owner; confirm the final organization identifier before publishing. The existing AI Studio release-signing configuration is retained; do not use a local debug key for a release. The app currently uses local Room persistence and has no Lovable/Supabase backend connection yet; `CounterPosRepository` is the integration seam for a backend when one is ready.
