# Counter POS for Android

This repository contains the native Kotlin/Jetpack Compose mobile app for the CounterPOS beverage point-of-sale system. The standalone prototype is preserved at [`prototype/CounterPOSprototype-updated.html`](prototype/CounterPOSprototype-updated.html) as a design reference and source for bundled product images.

## System architecture

The mobile app connects directly to the Lovable-built [Supabase backend repository](https://github.com/Alvin-b/pos-backbone) and follows its [API contract](https://github.com/Alvin-b/pos-backbone/blob/main/api.md). The live project is [pvcqfoycrqtnirbwtccm.supabase.co](https://pvcqfoycrqtnirbwtccm.supabase.co). The app uses the backend's Auth, PostgREST, RPC, and Storage interfaces; it does **not** contain a Supabase service-role key. The public project URL and publishable key are configured in `app/build.gradle.kts` as BuildConfig values. A publishable key is not a secret; access to shop data is governed by the signed-in user's JWT and backend row-level security (RLS).

At startup, the app restores the saved Supabase session. Users can sign in or create an account, then create a shop or select one of their shop memberships. Email confirmation follows the backend's Supabase Auth configuration. Each shop has its own inventory, sales, and product-photo overrides.

The app uses the backend as the **source of truth**:

- **Scan** searches the selected shop's stock by product name, brand, size, catalog code, or verified barcode; each tile shows the server-backed on-hand quantity and selling price.
- **Sales** reads shop sales and the backend's daily summary. Sale, undo, add-from-library, and receive-stock mutations use transactional backend RPCs, then the app reloads the shop snapshot.
- **Stock → My stock** displays the selected shop's products and quantities.
- **Stock → Library** loads the shared beverage catalog; adding an item calls the backend catalog RPC and records the user's actual quantity and selling price.
- **Tables** lets the cashier create and name tables, open a running check, add in-stock products using their configured sale units, review the items served, and print a table receipt through Android's print dialog. Table/check records are persisted in the device's Room database in this app revision; they are not shared between devices.
- **Product images** use bundled verified photos as the offline/catalog fallback. Owner-uploaded shop photos go to the private Supabase Storage bucket and are shown through short-lived signed URLs. Only owners are offered the photo-change control.

Room (`counterpos.db`) is a local **cache**, not a second inventory authority. It is replaced with the selected shop's latest products and sales after refresh. Stock and sales mutations require a working network connection; this version does not queue offline writes. Signing out clears the shop cache from the device. To switch between several shop memberships, sign out and sign back in to open the shop chooser.

The Android API integration lives in `data/remote/SupabaseRestClient.kt` and `data/CounterPosRepository.kt`. The app calls `getUser`/password Auth, reads `shop_members`, `catalog_items`, `products`, and `sales`, and uses `create_shop`, `add_from_catalog`, `record_sale`, `receive_stock`, `undo_sale`, and `daily_summary`. Product photo upload uses the backend's private `product-photos` storage bucket and `products.photo_path` field.

## Catalog and product photos

The app bundles **78 photographed beverage products**: 19 starter/demo-stock rows and 59 Library rows in the prototype dataset, covering beer/cider/RTDs, spirits, wine, water, soft drinks, juice, energy/sports drinks, and mixers. Milk/dairy and categories 9–12 from the earlier scope were intentionally excluded.

The live backend seed and Android image catalog currently share **all 78 stable catalog codes**. Every one of the 78 Android catalog rows has a bundled drawable image, so the mobile app can show a verified local image even when a hosted catalog image is unavailable. The 78-row list is a researched starter catalog, not yet a complete SKU/size/flavour inventory of every beverage consumed or sold in Kenya. Additional Kenyan products and variants can be added to both the backend catalog and the Android photo pack.

See [`docs/photo-sources.md`](docs/photo-sources.md) for the product and image-source register. Retailer/listing images may have usage restrictions; confirm image rights before public or commercial use.

**Barcode note:** a barcode is stored only when verified. Numeric-looking starter codes and `LIB-…` values are catalog codes, not claims that an item has a verified barcode. Name search works regardless; barcode matching works only after a verified barcode is present in catalog data.

## Updating catalog images

The import script rebuilds the Android JSON photo/metadata fallback and image resources from the bundled prototype:

```bash
python3 scripts/import_prototype_catalog.py prototype/CounterPOSprototype-updated.html
```

This script does not alter the production Supabase catalog. To add shared products, update the backend catalog through its reviewed backend workflow and add matching product-image assets/code mappings to the mobile pack. Do not place secret Supabase keys in Android source or configuration.

## Build and test

Use JDK 21 and Android SDK platform 36 (including the SDK-installed 36.1 extension) with Build Tools 36.0.0. Set `ANDROID_HOME` to the SDK location, then run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug signing configuration preserves the AI Studio setup and expects an ignored root-level `debug.keystore`. If your development checkout does not provide one, create a **local development-only** default Android debug keystore (do not commit it):

```bash
keytool -genkeypair -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android \
  -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
```

The package/application ID is provisionally `com.alvinb.counterpos` based on the repository owner; confirm the final organization identifier before publishing. The existing AI Studio release-signing configuration is retained; do not use a local debug key for a release.
