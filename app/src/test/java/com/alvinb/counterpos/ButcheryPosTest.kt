package com.alvinb.counterpos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.butchery.ButcheryCategory
import com.alvinb.counterpos.data.butchery.ButcheryPricing
import com.alvinb.counterpos.data.butchery.ButcheryTrendRange
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.local.entity.ButcheryConfigEntity
import com.alvinb.counterpos.data.local.entity.ButcherySaleEntity
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.ui.ButcheryDailySalesScreen
import com.alvinb.counterpos.ui.ButcheryPosScreen
import com.alvinb.counterpos.ui.ButcherySettingsScreen
import com.alvinb.counterpos.ui.PdfDocumentSession
import com.alvinb.counterpos.ui.buildButcheryReportPdf
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ButcheryPosTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class BitmapRecordingPdfSession : PdfDocumentSession {
        val pageBitmaps = mutableListOf<Bitmap>()
        var finishedPages = 0

        override fun beginPage(width: Int, height: Int, pageNumber: Int): Canvas {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            pageBitmaps += bmp
            return Canvas(bmp)
        }

        override fun endPage() {
            finishedPages += 1
        }

        override fun finishToByteArray(): ByteArray {
            return "%PDF-1.4\n%%Pages:${pageBitmaps.size}\n%%EOF".toByteArray(Charsets.US_ASCII)
        }
    }

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: CounterPosRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val remote = SupabaseRestClient(context)
        remote.clearSession()
        repository = CounterPosRepository(
            context = context,
            dao = database.counterPosDao(),
            assets = context.assets,
            remote = remote
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun digitalScaleCalculationsComputeExactTotalAndExactKg() {
        // Weight -> Total KES
        val steakSale = ButcheryPricing.computeFromWeightKg(1.50, pricePerKgKsh = 680)
        assertTrue(steakSale.isValid)
        assertEquals(1500, steakSale.weightGrams)
        assertEquals(1020, steakSale.totalKsh)

        // Price -> Weight kg (digital scale: customer asks for meat worth KES 150, 200, 100)
        val scale150 = ButcheryPricing.computeFromAmountKsh(150, pricePerKgKsh = 680)
        assertTrue(scale150.isValid)
        assertEquals(221, scale150.weightGrams) // 150 / 680 * 1000 = 220.58 -> 221 g
        assertEquals(150, scale150.totalKsh)

        val scale200 = ButcheryPricing.computeFromAmountKsh(200, pricePerKgKsh = 680)
        assertTrue(scale200.isValid)
        assertEquals(294, scale200.weightGrams)
        assertEquals(200, scale200.totalKsh)

        val scale100 = ButcheryPricing.computeFromAmountKsh(100, pricePerKgKsh = 680)
        assertTrue(scale100.isValid)
        assertEquals(147, scale100.weightGrams)
        assertEquals(100, scale100.totalKsh)

        val halfKgPrice = ButcheryPricing.computeFromAmountKsh(340, pricePerKgKsh = 680)
        assertEquals(500, halfKgPrice.weightGrams)
    }

    @Test
    fun beefOnlyModeHidesNonBeefCutsAndCategories() {
        val cuts = ButcheryPricing.defaultCuts(shopId = "shop-butcher-1", beefPricePerKgKsh = 720)
        val beefOnlyCategories = ButcheryPricing.visibleCategories(beefOnlyMode = true, cuts = cuts)
        assertEquals(listOf(ButcheryCategory.BEEF.label), beefOnlyCategories)

        val visibleInBeefOnly = ButcheryPricing.filterCuts(
            cuts = cuts,
            beefOnlyMode = true,
            selectedCategory = ButcheryCategory.ALL.label,
            searchQuery = ""
        )
        assertEquals(listOf("Beef"), visibleInBeefOnly.map { it.name })

        val visibleInMixed = ButcheryPricing.filterCuts(
            cuts = cuts,
            beefOnlyMode = false,
            selectedCategory = ButcheryCategory.ALL.label,
            searchQuery = ""
        )
        assertEquals(5, visibleInMixed.size)
    }

    @Test
    fun enrollButcheryRecordSalesUndoAndExportPdfAndCsvWorkEndToEnd() = runBlocking {
        val shopId = "shop-club-plus-butchery"
        repository.enrollOrUpdateButchery(
            shopId = shopId,
            enabled = true,
            butcheryName = "Savannah Prime Butchery",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 680
        )

        val config = repository.observeButcheryConfig(shopId).first()
        assertNotNull(config)
        assertTrue(config!!.enabled)
        assertEquals("Savannah Prime Butchery", config.butcheryName)

        val cuts = repository.observeButcheryCuts(shopId).first()
        val beefCut = cuts.first { it.name == "Beef" }
        assertEquals(680, beefCut.pricePerKgKsh)

        // Set 10.0 kg stock on Beef and update price to 700/kg
        assertTrue(repository.setButcheryCutStockKg(beefCut.id, 10.0))
        assertTrue(repository.updateButcheryCutPrice(beefCut.id, 700))

        // Customer 1: 1.50 kg via M-Pesa (no STK push)
        val calc1 = ButcheryPricing.computeFromWeightKg(1.50, 700)
        val sale1Id = repository.recordButcherySale(
            shopId = shopId,
            cutId = beefCut.id,
            weightGrams = calc1.weightGrams,
            totalKsh = calc1.totalKsh,
            paymentMethod = "mpesa",
            entryMode = "weight"
        )
        assertNotNull(sale1Id)

        // Customer 2: KES 150 worth of meat via Cash on digital scale
        val calc2 = ButcheryPricing.computeFromAmountKsh(150, 700)
        val sale2Id = repository.recordButcherySale(
            shopId = shopId,
            cutId = beefCut.id,
            weightGrams = calc2.weightGrams,
            totalKsh = calc2.totalKsh,
            paymentMethod = "cash",
            entryMode = "amount"
        )
        assertNotNull(sale2Id)

        val updatedBeef = repository.observeButcheryCuts(shopId).first().first { it.id == beefCut.id }
        assertEquals(10_000 - calc1.weightGrams - calc2.weightGrams, updatedBeef.stockGrams)

        val sales = repository.observeButcherySales(shopId).first()
        val window = ButcheryPricing.dayWindowMillis(0)
        val report = ButcheryPricing.buildDailyReport(sales, window.first, window.second)
        assertEquals(calc1.totalKsh.toLong() + 150L, report.totalSalesKsh)
        assertEquals(calc1.weightGrams.toLong() + calc2.weightGrams.toLong(), report.totalWeightGrams)
        assertEquals(calc1.totalKsh.toLong(), report.mpesaTotalKsh)
        assertEquals(150L, report.cashTotalKsh)

        // Export Excel CSV & Branded PDF
        val csv = ButcheryPricing.buildExcelCsv("Savannah Prime Butchery", report)
        assertTrue(csv.contains("Savannah Prime Butchery"))
        assertTrue(csv.contains("\"Beef\""))
        assertTrue(csv.contains("By Amount (KES)"))

        val pdfSession = BitmapRecordingPdfSession()
        val pdfBytes = buildButcheryReportPdf(
            context = context,
            butcheryName = "Savannah Prime Butchery",
            snapshot = report,
            beefOnlyMode = false,
            sessionFactory = { pdfSession }
        )
        assertTrue(pdfSession.pageBitmaps.isNotEmpty())
        assertEquals(pdfSession.pageBitmaps.size, pdfSession.finishedPages)
        assertEquals("%PDF", String(pdfBytes.copyOfRange(0, 4), Charsets.US_ASCII))

        // Undo latest sale (sale 2) restores exact stock grams
        assertTrue(repository.undoLatestButcherySale(shopId))
        val afterUndoBeef = repository.observeButcheryCuts(shopId).first().first { it.id == beefCut.id }
        assertEquals(10_000 - calc1.weightGrams, afterUndoBeef.stockGrams)
        val reportAfterUndo = ButcheryPricing.buildDailyReport(
            repository.observeButcherySales(shopId).first(),
            window.first,
            window.second
        )
        assertEquals(1, reportAfterUndo.sales.size)
        assertEquals(0L, reportAfterUndo.cashTotalKsh)
    }

    @Test
    fun migration12To13CreatesButcheryTablesCleanly() {
        val dbName = "migration_12_13_test.db"
        context.deleteDatabase(dbName)
        val v12Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS products (catalogCode TEXT NOT NULL PRIMARY KEY)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val v12Db = v12Helper.writableDatabase
        AppDatabase.MIGRATION_12_13.migrate(v12Db)
        val cursor = v12Db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('butchery_config','butchery_cuts','butchery_sales')"
        )
        val createdTables = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            createdTables.add(cursor.getString(0))
        }
        cursor.close()
        v12Db.close()
        context.deleteDatabase(dbName)

        assertEquals(setOf("butchery_config", "butchery_cuts", "butchery_sales"), createdTables)
    }

    @Test
    fun butcheryPosUiSupportsBeefOnlyDigitalScalePriceModeAndDirectPayments() {
        val shopId = "shop-ui-butchery"
        val config = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Kenyatta Beef & Cuts",
            beefOnlyMode = true,
            defaultBeefPricePerKgKsh = 680
        )
        val cuts = ButcheryPricing.defaultCuts(shopId = shopId, beefPricePerKgKsh = 680)
        val recordedPayments = mutableListOf<Triple<Int, Int, String>>()

        composeRule.setContent {
            ButcheryPosScreen(
                config = config,
                cuts = cuts,
                canUndoLastSale = false,
                onBack = {},
                onOpenSettings = {},
                onUndoLastSale = {},
                onRecordSale = { _, weightGrams, totalKsh, paymentMethod, _ ->
                    recordedPayments += Triple(weightGrams, totalKsh, paymentMethod)
                }
            )
        }
        composeRule.waitForIdle()

        // Beef-Only shows just Beef and hides non-beef products and old Beef Steak / Beef Cubes
        composeRule.onNodeWithText("Beef").assertIsDisplayed()
        composeRule.onNodeWithText("Beef Steak").assertDoesNotExist()
        composeRule.onNodeWithText("Beef Cubes").assertDoesNotExist()
        composeRule.onNodeWithText("Goat Meat").assertDoesNotExist()
        composeRule.onNodeWithText("Whole Chicken").assertDoesNotExist()
        composeRule.onNodeWithText("Pork Chops").assertDoesNotExist()

        // Opens directly in money-first mode (KES 0)
        composeRule.onNodeWithTag("butchery_keypad_display").assertIsDisplayed()
        composeRule.onNodeWithTag("butchery_keypad_display").assertTextEquals("KES 0")

        // Key in 150 KES directly on the keypad (digital scale: customer buys meat worth KES 150)
        composeRule.onNodeWithTag("butchery_key_1").performClick()
        composeRule.onNodeWithTag("butchery_key_5").performClick()
        composeRule.onNodeWithTag("butchery_key_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_keypad_display").assertTextEquals("KES 150")
        composeRule.onNodeWithText("KES 150 = 0.22 kg (221 g) @ KES 680/kg").assertIsDisplayed()

        // Record direct M-Pesa (no STK push) for KES 150 -> resets keypad to KES 0
        composeRule.onNodeWithTag("butchery_pay_mpesa_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, recordedPayments.size)
        assertEquals(Triple(221, 150, "mpesa"), recordedPayments[0])
        composeRule.onNodeWithTag("butchery_keypad_display").assertTextEquals("KES 0")

        // Select KES 200 preset and record Cash Payment directly -> resets keypad to KES 0
        composeRule.onNodeWithTag("butchery_preset_kes_200").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("KES 200 = 0.29 kg (294 g) @ KES 680/kg").assertIsDisplayed()
        composeRule.onNodeWithTag("butchery_pay_cash_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, recordedPayments.size)
        assertEquals(Triple(294, 200, "cash"), recordedPayments[1])
        composeRule.onNodeWithTag("butchery_keypad_display").assertTextEquals("KES 0")

        // Toggling to weight mode still computes kg -> KES accurately
        composeRule.onNodeWithTag("butchery_toggle_entry_mode").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_keypad_display").assertTextEquals("1.0 kg")
        composeRule.onNodeWithText("1.00 kg = KES 680").assertIsDisplayed()
    }

    @Test
    fun migration13To14AddsPaymentMethodsColumnCleanly() {
        val dbName = "migration_13_14_test.db"
        context.deleteDatabase(dbName)
        val v13Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS butchery_config (
                                shopId TEXT NOT NULL PRIMARY KEY,
                                enabled INTEGER NOT NULL,
                                butcheryName TEXT NOT NULL,
                                beefOnlyMode INTEGER NOT NULL,
                                defaultBeefPricePerKgKsh INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = v13Helper.writableDatabase
        AppDatabase.MIGRATION_13_14.migrate(db)
        val cursor = db.query("PRAGMA table_info(butchery_config)")
        val cols = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            val nameIdx = cursor.getColumnIndex("name")
            if (nameIdx >= 0) cols.add(cursor.getString(nameIdx))
        }
        cursor.close()
        db.close()
        context.deleteDatabase(dbName)

        assertTrue(cols.contains("paymentMethodsJson"))
    }

    @Test
    fun customPaymentMethodsPersistRecordAndAppearInReportsAndCsv() = runBlocking {
        val shopId = "shop-custom-pay"
        repository.enrollOrUpdateButchery(
            shopId = shopId,
            enabled = true,
            butcheryName = "Rift Valley Meats",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 800,
            paymentMethods = listOf("M-Pesa", "Cash Payment", "Till 8291", "Airtel Money")
        )

        val config = repository.observeButcheryConfig(shopId).first()
        assertNotNull(config)
        val methods = ButcheryPricing.parsePaymentMethods(config?.paymentMethodsJson)
        assertEquals(listOf("M-Pesa", "Cash Payment", "Till 8291", "Airtel Money"), methods)

        val cuts = repository.observeButcheryCuts(shopId).first()
        val beefCut = cuts.first { it.name == "Beef" }
        val calc = ButcheryPricing.computeFromAmountKsh(200, beefCut.pricePerKgKsh)
        assertEquals(250, calc.weightGrams) // 200 / 800 = 0.25 kg = 250 g

        val saleId = repository.recordButcherySale(
            shopId = shopId,
            cutId = beefCut.id,
            weightGrams = calc.weightGrams,
            totalKsh = calc.totalKsh,
            paymentMethod = "Till 8291",
            entryMode = "amount"
        )
        assertNotNull(saleId)

        val sales = repository.observeButcherySales(shopId).first()
        val window = ButcheryPricing.dayWindowMillis(0)
        val report = ButcheryPricing.buildDailyReport(sales, window.first, window.second)
        assertEquals(1, report.paymentBreakdown.size)
        assertEquals("Till 8291", report.paymentBreakdown.first().method)
        assertEquals(200L, report.paymentBreakdown.first().totalKsh)

        val csv = ButcheryPricing.buildExcelCsv("Rift Valley Meats", report)
        assertTrue(csv.contains("Till 8291 Total (KES)"))
        assertTrue(csv.contains("\"Till 8291\""))

        val pdfSession = BitmapRecordingPdfSession()
        val pdfBytes = buildButcheryReportPdf(
            context = context,
            butcheryName = "Rift Valley Meats",
            snapshot = report,
            beefOnlyMode = false,
            sessionFactory = { pdfSession }
        )
        assertTrue(pdfSession.pageBitmaps.isNotEmpty())
        assertEquals("%PDF", String(pdfBytes.copyOfRange(0, 4), Charsets.US_ASCII))
    }

    @Test
    fun butcherySettingsAndPosSupportCustomPaymentMethodsAndPricePerKgCalculation() {
        val shopId = "shop-custom-ui"
        val initialConfig = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Nyama Fresh",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 680,
            paymentMethodsJson = ButcheryPricing.serializePaymentMethods(listOf("M-Pesa", "Cash Payment"))
        )
        val cuts = ButcheryPricing.defaultCuts(shopId, 680)
        var savedMethods: List<String> = emptyList()
        var savedCategories: List<String> = emptyList()

        composeRule.setContent {
            ButcherySettingsScreen(
                config = initialConfig,
                cuts = cuts,
                businessName = "Club Mirage",
                ownerCanEdit = true,
                onBack = {},
                onSaveConfig = { _, _, _, _, methods, categories ->
                    savedMethods = methods
                    savedCategories = categories
                },
                onApplyCategoryPrice = { _, _ -> },
                onUpdateCutPrice = { _, _ -> },
                onUpdateCutStockKg = { _, _ -> },
                onToggleCutActive = { _, _ -> },
                onAddCut = { _, _, _, _, _ -> }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("butchery_custom_payment_input").performTextInput("Pochi 0712")
        composeRule.onNodeWithTag("butchery_add_payment_method_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_save_settings_button").performClick()
        composeRule.waitForIdle()

        assertTrue(savedMethods.contains("Pochi 0712"))
        assertTrue(savedMethods.contains("M-Pesa"))
        assertTrue(savedMethods.contains("Cash Payment"))
        assertEquals(ButcheryPricing.ALL_MEAT_CATEGORIES, savedCategories)
    }

    @Test
    fun butcheryPosDisplaysCustomPaymentButtonAndRecordsCustomMethod() {
        val shopId = "shop-custom-pos-ui"
        val config = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Nyama Fresh",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 680,
            paymentMethodsJson = ButcheryPricing.serializePaymentMethods(
                listOf("M-Pesa", "Cash Payment", "Pochi 0712", "Card")
            )
        )
        val cuts = ButcheryPricing.defaultCuts(shopId, 680)
        val recorded = mutableListOf<Triple<Int, Int, String>>()

        composeRule.setContent {
            ButcheryPosScreen(
                config = config,
                cuts = cuts,
                canUndoLastSale = false,
                onBack = {},
                onOpenSettings = {},
                onUndoLastSale = {},
                onRecordSale = { _, weightGrams, totalKsh, method, _ ->
                    recorded += Triple(weightGrams, totalKsh, method)
                }
            )
        }
        composeRule.waitForIdle()

        // Key in KES 150 directly in default money-first mode
        composeRule.onNodeWithTag("butchery_preset_kes_150").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("KES 150 = 0.22 kg (221 g) @ KES 680/kg").assertIsDisplayed()

        composeRule.onNodeWithTag("butchery_pay_method_2").assertIsDisplayed()
        composeRule.onNodeWithTag("butchery_pay_method_2").performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorded.size)
        assertEquals(Triple(221, 150, "Pochi 0712"), recorded.first())
    }

    @Test
    fun migration14To15AddsEnabledCategoriesColumnCleanly() {
        val dbName = "migration_14_15_test.db"
        context.deleteDatabase(dbName)
        val v14Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS butchery_config (
                                shopId TEXT NOT NULL PRIMARY KEY,
                                enabled INTEGER NOT NULL,
                                butcheryName TEXT NOT NULL,
                                beefOnlyMode INTEGER NOT NULL,
                                defaultBeefPricePerKgKsh INTEGER NOT NULL,
                                paymentMethodsJson TEXT NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = v14Helper.writableDatabase
        AppDatabase.MIGRATION_14_15.migrate(db)
        val cursor = db.query("PRAGMA table_info(butchery_config)")
        val cols = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            val nameIdx = cursor.getColumnIndex("name")
            if (nameIdx >= 0) cols.add(cursor.getString(nameIdx))
        }
        cursor.close()
        db.close()
        context.deleteDatabase(dbName)

        assertTrue(cols.contains("enabledCategoriesJson"))
    }

    @Test
    fun singleNonBeefMeatTypeAndSubsetFilteringWorkEndToEnd() = runBlocking {
        val shopId = "shop-goat-only"
        repository.enrollOrUpdateButchery(
            shopId = shopId,
            enabled = true,
            butcheryName = "Kajiado Mbuzi Corner",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 750,
            enabledCategories = listOf(ButcheryCategory.GOAT.label)
        )

        val config = repository.observeButcheryConfig(shopId).first()
        assertNotNull(config)
        val resolved = ButcheryPricing.resolveEnabledCategories(config)
        assertEquals(listOf(ButcheryCategory.GOAT.label), resolved)

        val cuts = repository.observeButcheryCuts(shopId).first()
        assertEquals(listOf(ButcheryCategory.GOAT.label), ButcheryPricing.visibleCategories(resolved, cuts))

        val filteredGoatOnly = ButcheryPricing.filterCuts(
            cuts = cuts,
            enabledCategories = resolved,
            selectedCategory = ButcheryCategory.ALL.label,
            searchQuery = ""
        )
        assertEquals(listOf("Goat Meat"), filteredGoatOnly.map { it.name })

        // Update to a 2-meat subset (Beef + Chicken)
        repository.enrollOrUpdateButchery(
            shopId = shopId,
            enabled = true,
            butcheryName = "Kajiado Mbuzi Corner",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 700,
            enabledCategories = listOf(ButcheryCategory.BEEF.label, ButcheryCategory.CHICKEN.label)
        )
        val subsetConfig = repository.observeButcheryConfig(shopId).first()
        val subsetResolved = ButcheryPricing.resolveEnabledCategories(subsetConfig)
        assertEquals(listOf(ButcheryCategory.BEEF.label, ButcheryCategory.CHICKEN.label), subsetResolved)
        val subsetCuts = ButcheryPricing.filterCuts(
            cuts = cuts,
            enabledCategories = subsetResolved,
            selectedCategory = ButcheryCategory.ALL.label,
            searchQuery = ""
        )
        assertTrue(subsetCuts.any { it.name == "Beef" })
        assertTrue(subsetCuts.any { it.name == "Whole Chicken" })
        assertTrue(subsetCuts.none { it.name == "Pork Chops" })
    }

    @Test
    fun butcherySettingsCanSelectSingleNonBeefMeatAndPosReflectsIt() {
        val shopId = "shop-single-pork-ui"
        val initialConfig = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Nyama Fresh",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 700,
            enabledCategoriesJson = ButcheryPricing.ALL_MEAT_CATEGORIES_JSON
        )
        val cuts = ButcheryPricing.defaultCuts(shopId, 700)
        var savedCategories: List<String> = emptyList()

        composeRule.setContent {
            ButcherySettingsScreen(
                config = initialConfig,
                cuts = cuts,
                businessName = "Club Mirage",
                ownerCanEdit = true,
                onBack = {},
                onSaveConfig = { _, _, _, _, _, categories -> savedCategories = categories },
                onApplyCategoryPrice = { _, _ -> },
                onUpdateCutPrice = { _, _ -> },
                onUpdateCutStockKg = { _, _ -> },
                onToggleCutActive = { _, _ -> },
                onAddCut = { _, _, _, _, _ -> }
            )
        }
        composeRule.waitForIdle()

        // Deselect all except Pork so only one meat type remains active
        composeRule.onNodeWithTag("butchery_meat_type_beef").performClick()
        composeRule.onNodeWithTag("butchery_meat_type_goat_mbuzi").performClick()
        composeRule.onNodeWithTag("butchery_meat_type_chicken").performClick()
        composeRule.onNodeWithTag("butchery_meat_type_liver_matumbo").performClick()
        // Clicking the last remaining meat type (Pork) keeps at least one selected
        composeRule.onNodeWithTag("butchery_meat_type_pork").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("butchery_save_settings_button").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ButcheryCategory.PORK.label), savedCategories)
    }

    @Test
    fun butcheryPosDisplaysSingleNonBeefMeatOnlyAndHidesOtherCategories() {
        val shopId = "shop-goat-pos-ui"
        val config = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Mbuzi Express",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 750,
            enabledCategoriesJson = ButcheryPricing.serializeEnabledCategories(listOf(ButcheryCategory.GOAT.label))
        )
        val cuts = ButcheryPricing.defaultCuts(shopId, 680)

        composeRule.setContent {
            ButcheryPosScreen(
                config = config,
                cuts = cuts,
                canUndoLastSale = false,
                onBack = {},
                onOpenSettings = {},
                onUndoLastSale = {},
                onRecordSale = { _, _, _, _, _ -> }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Mbuzi Express · Goat Meat Only").assertIsDisplayed()
        composeRule.onNodeWithText("Goat Meat").assertIsDisplayed()
        composeRule.onNodeWithText("Beef").assertDoesNotExist()
        composeRule.onNodeWithText("Whole Chicken").assertDoesNotExist()
        composeRule.onNodeWithText("Pork Chops").assertDoesNotExist()
    }

    @Test
    fun butcherySettingsSelectingBeefOnlyDisplaysOnlyBeefProduct() {
        val shopId = "shop-beef-settings-only"
        val initialConfig = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Kenyatta Butchery",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 680,
            enabledCategoriesJson = ButcheryPricing.ALL_MEAT_CATEGORIES_JSON
        )
        val cuts = ButcheryPricing.defaultCuts(shopId, 680)

        composeRule.setContent {
            ButcherySettingsScreen(
                config = initialConfig,
                cuts = cuts,
                businessName = "Kenyatta Club",
                ownerCanEdit = true,
                onBack = {},
                onSaveConfig = { _, _, _, _, _, _ -> },
                onApplyCategoryPrice = { _, _ -> },
                onUpdateCutPrice = { _, _ -> },
                onUpdateCutStockKg = { _, _ -> },
                onToggleCutActive = { _, _ -> },
                onAddCut = { _, _, _, _, _ -> }
            )
        }
        composeRule.waitForIdle()

        // Deselect all non-beef meat types so only Beef remains selected
        composeRule.onNodeWithTag("butchery_meat_type_goat_mbuzi").performClick()
        composeRule.onNodeWithTag("butchery_meat_type_chicken").performClick()
        composeRule.onNodeWithTag("butchery_meat_type_liver_matumbo").performClick()
        composeRule.onNodeWithTag("butchery_meat_type_pork").performClick()
        composeRule.waitForIdle()

        // Settings list below displays only the chosen Beef product
        composeRule.onNodeWithTag("butchery_settings_list")
            .performScrollToNode(hasTestTag("butchery_edit_cut_${shopId}_beef"))
        composeRule.onNodeWithTag("butchery_edit_cut_${shopId}_beef").assertIsDisplayed()
        composeRule.onNodeWithTag("butchery_edit_cut_${shopId}_goat_meat").assertDoesNotExist()
        composeRule.onNodeWithTag("butchery_edit_cut_${shopId}_whole_chicken").assertDoesNotExist()
        composeRule.onNodeWithTag("butchery_edit_cut_${shopId}_matumbo").assertDoesNotExist()
        composeRule.onNodeWithTag("butchery_edit_cut_${shopId}_pork_chops").assertDoesNotExist()
    }

    @Test
    fun migration15To16NormalizesBeefCutNameAndRemovesUnusedBeefCubes() {
        val dbName = "migration_15_16_test.db"
        context.deleteDatabase(dbName)
        val v15Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS butchery_cuts (
                                id TEXT NOT NULL PRIMARY KEY,
                                shopId TEXT NOT NULL,
                                name TEXT NOT NULL,
                                category TEXT NOT NULL,
                                pricePerKgKsh INTEGER NOT NULL,
                                stockGrams INTEGER NOT NULL,
                                iconKey TEXT NOT NULL,
                                active INTEGER NOT NULL,
                                sortOrder INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO butchery_cuts VALUES ('s1_beef_steak','s1','Beef Steak','Beef',680,0,'beef_steak',1,1)"
                        )
                        db.execSQL(
                            "INSERT INTO butchery_cuts VALUES ('s1_beef_cubes','s1','Beef Cubes','Beef',620,0,'beef_cubes',1,4)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = v15Helper.writableDatabase
        AppDatabase.MIGRATION_15_16.migrate(db)
        val cursor = db.query("SELECT id, name, iconKey FROM butchery_cuts ORDER BY sortOrder")
        val rows = mutableListOf<Triple<String, String, String>>()
        while (cursor.moveToNext()) {
            rows += Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
        }
        cursor.close()
        db.close()
        context.deleteDatabase(dbName)

        assertEquals(listOf(Triple("s1_beef_steak", "Beef", "beef")), rows)
    }

    @Test
    fun dailySalesTrendCalculatesDailyRevenueAndMeatQuantityAccurately() {
        val noonCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val refMs = noonCal.timeInMillis
        val dayMs = 86_400_000L

        val sales = listOf(
            // 5 days ago: Beef 2.0 kg = 1,360
            ButcherySaleEntity(
                id = 1L, shopId = "s1", cutId = "s1_beef", cutName = "Beef", category = "Beef",
                pricePerKgKsh = 680, weightGrams = 2_000, totalKsh = 1_360,
                paymentMethod = "mpesa", entryMode = "weight", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs - (5 * dayMs), undone = false
            ),
            // 2 days ago: Goat 1.5 kg = 1,125 + Beef 1.0 kg = 680
            ButcherySaleEntity(
                id = 2L, shopId = "s1", cutId = "s1_goat", cutName = "Goat Meat", category = "Goat/Mbuzi",
                pricePerKgKsh = 750, weightGrams = 1_500, totalKsh = 1_125,
                paymentMethod = "cash", entryMode = "weight", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs - (2 * dayMs), undone = false
            ),
            ButcherySaleEntity(
                id = 3L, shopId = "s1", cutId = "s1_beef", cutName = "Beef", category = "Beef",
                pricePerKgKsh = 680, weightGrams = 1_000, totalKsh = 680,
                paymentMethod = "mpesa", entryMode = "weight", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs - (2 * dayMs), undone = false
            ),
            // Today: Beef 3.5 kg = 2,380 (peak day) + undone sale that must be ignored
            ButcherySaleEntity(
                id = 4L, shopId = "s1", cutId = "s1_beef", cutName = "Beef", category = "Beef",
                pricePerKgKsh = 680, weightGrams = 3_500, totalKsh = 2_380,
                paymentMethod = "mpesa", entryMode = "amount", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs, undone = false
            ),
            ButcherySaleEntity(
                id = 5L, shopId = "s1", cutId = "s1_beef", cutName = "Beef", category = "Beef",
                pricePerKgKsh = 680, weightGrams = 10_000, totalKsh = 6_800,
                paymentMethod = "cash", entryMode = "weight", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs, undone = true
            )
        )

        val allTrend = ButcheryPricing.buildDailySalesTrend(
            allSales = sales,
            range = ButcheryTrendRange.SEVEN_DAYS,
            categoryFilter = ButcheryCategory.ALL.label,
            referenceMs = refMs
        )
        assertEquals(7, allTrend.days.size)
        assertEquals(5_545L, allTrend.totalRevenueKsh)
        assertEquals(8_000L, allTrend.totalWeightGrams)
        assertEquals(4, allTrend.totalTransactions)
        assertEquals(3, allTrend.activeDaysCount)
        assertEquals(2_380L, allTrend.bestRevenueDay?.revenueKsh)
        assertEquals(3_500L, allTrend.bestVolumeDay?.weightGrams)
        assertTrue((allTrend.revenueChangePercent ?: 0.0) > 0.0)
        assertTrue((allTrend.quantityChangePercent ?: 0.0) > 0.0)

        // Category filter isolates Beef only
        val beefTrend = ButcheryPricing.buildDailySalesTrend(
            allSales = sales,
            range = ButcheryTrendRange.SEVEN_DAYS,
            categoryFilter = ButcheryCategory.BEEF.label,
            referenceMs = refMs
        )
        assertEquals(4_420L, beefTrend.totalRevenueKsh)
        assertEquals(6_500L, beefTrend.totalWeightGrams)
        assertEquals(3, beefTrend.totalTransactions)
    }

    @Test
    fun dailySalesScreenRendersVicoRevenueAndMeatQuantityChartsAndRespondsToFilters() {
        val noonCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val refMs = noonCal.timeInMillis
        val dayMs = 86_400_000L

        val config = ButcheryConfigEntity(
            shopId = "s-trend",
            enabled = true,
            butcheryName = "Savannah Prime Butchery",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 680
        )
        val sales = listOf(
            ButcherySaleEntity(
                id = 1L, shopId = "s-trend", cutId = "c1", cutName = "Beef", category = "Beef",
                pricePerKgKsh = 680, weightGrams = 2_500, totalKsh = 1_700,
                paymentMethod = "mpesa", entryMode = "weight", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs - (10 * dayMs), undone = false
            ),
            ButcherySaleEntity(
                id = 2L, shopId = "s-trend", cutId = "c2", cutName = "Goat Meat", category = "Goat/Mbuzi",
                pricePerKgKsh = 750, weightGrams = 2_000, totalKsh = 1_500,
                paymentMethod = "cash", entryMode = "weight", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs - (1 * dayMs), undone = false
            ),
            ButcherySaleEntity(
                id = 3L, shopId = "s-trend", cutId = "c1", cutName = "Beef", category = "Beef",
                pricePerKgKsh = 680, weightGrams = 4_000, totalKsh = 2_720,
                paymentMethod = "mpesa", entryMode = "amount", stockDeductedGrams = 0,
                attendantName = "Owner", createdAt = refMs, undone = false
            )
        )

        composeRule.setContent {
            ButcheryDailySalesScreen(
                config = config,
                sales = sales,
                onBack = {},
                referenceMs = refMs
            )
        }
        composeRule.waitForIdle()

        // Default 14D range includes all 3 sales: KES 5,920 & 8.5 kg
        composeRule.onNodeWithTag("butchery_daily_sales_total_revenue").assertTextEquals("KES 5,920")
        composeRule.onNodeWithTag("butchery_daily_sales_total_kg").assertTextEquals("8.5 kg")
        composeRule.onNodeWithTag("butchery_daily_revenue_vico_chart").assertIsDisplayed()

        // Scroll to verify Vico quantity chart and daily performance breakdown card
        composeRule.onNodeWithTag("butchery_daily_sales_list")
            .performScrollToNode(hasTestTag("butchery_daily_quantity_vico_chart"))
        composeRule.onNodeWithTag("butchery_daily_quantity_vico_chart").assertIsDisplayed()

        composeRule.onNodeWithTag("butchery_daily_sales_list")
            .performScrollToNode(hasTestTag("butchery_daily_breakdown_card"))
        composeRule.onNodeWithTag("butchery_daily_breakdown_card").assertIsDisplayed()

        // Scroll back up to filter bar and switch to 7D range (excludes the 10-day-old sale)
        composeRule.onNodeWithTag("butchery_daily_sales_list")
            .performScrollToNode(hasTestTag("butchery_trend_range_7"))
        composeRule.onNodeWithTag("butchery_trend_range_7").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_daily_sales_total_revenue").assertTextEquals("KES 4,220")
        composeRule.onNodeWithTag("butchery_daily_sales_total_kg").assertTextEquals("6.0 kg")

        // Filter to Beef only inside 7D
        composeRule.onNodeWithTag("butchery_trend_category_beef").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_daily_sales_total_revenue").assertTextEquals("KES 2,720")
        composeRule.onNodeWithTag("butchery_daily_sales_total_kg").assertTextEquals("4.0 kg")
    }

    @Test
    fun butcheryPosShowsOnlySelectedSetupSubsetAndSavesKeyedSalesIntoReport() {
        val shopId = "shop-subset-quick-sale"
        val config = ButcheryConfigEntity(
            shopId = shopId,
            enabled = true,
            butcheryName = "Savannah Grill & Chops",
            beefOnlyMode = false,
            defaultBeefPricePerKgKsh = 700,
            enabledCategoriesJson = ButcheryPricing.serializeEnabledCategories(
                listOf(ButcheryCategory.GOAT.label, ButcheryCategory.PORK.label)
            )
        )
        runBlocking {
            repository.enrollOrUpdateButchery(
                shopId = shopId,
                enabled = true,
                butcheryName = config.butcheryName,
                beefOnlyMode = false,
                defaultBeefPricePerKgKsh = 700,
                enabledCategories = listOf(ButcheryCategory.GOAT.label, ButcheryCategory.PORK.label)
            )
        }
        val cuts = runBlocking { repository.observeButcheryCuts(shopId).first() }

        composeRule.setContent {
            ButcheryPosScreen(
                config = config,
                cuts = cuts,
                canUndoLastSale = false,
                onBack = {},
                onOpenSettings = {},
                onUndoLastSale = {},
                onRecordSale = { cut, weightGrams, totalKsh, paymentMethod, entryMode ->
                    runBlocking {
                        repository.recordButcherySale(
                            shopId = shopId,
                            cutId = cut.id,
                            weightGrams = weightGrams,
                            totalKsh = totalKsh,
                            paymentMethod = paymentMethod,
                            entryMode = entryMode
                        )
                    }
                }
            )
        }
        composeRule.waitForIdle()

        // Only setup-selected products (Goat/Mbuzi + Pork) appear on the sale screen
        composeRule.onNodeWithText("Goat Meat").assertIsDisplayed()
        composeRule.onNodeWithText("Pork Chops").assertIsDisplayed()
        composeRule.onNodeWithText("Beef").assertDoesNotExist()
        composeRule.onNodeWithText("Whole Chicken").assertDoesNotExist()
        composeRule.onNodeWithText("Matumbo").assertDoesNotExist()

        // Key in KES 300 -> M-Pesa -> saved into sales report
        composeRule.onNodeWithTag("butchery_key_3").performClick()
        composeRule.onNodeWithTag("butchery_key_0").performClick()
        composeRule.onNodeWithTag("butchery_key_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_pay_mpesa_button").performClick()
        composeRule.waitForIdle()

        // Select Pork Chops, key in KES 350 -> Cash Payment -> saved into sales report
        composeRule.onNodeWithText("Pork Chops").performClick()
        composeRule.onNodeWithTag("butchery_key_3").performClick()
        composeRule.onNodeWithTag("butchery_key_5").performClick()
        composeRule.onNodeWithTag("butchery_key_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("butchery_pay_cash_button").performClick()
        composeRule.waitForIdle()

        val savedSales = runBlocking { repository.observeButcherySales(shopId).first() }
        assertEquals(2, savedSales.size)
        val window = ButcheryPricing.dayWindowMillis(0)
        val report = ButcheryPricing.buildDailyReport(savedSales, window.first, window.second)
        assertEquals(650L, report.totalSalesKsh)
        assertEquals(300L, report.mpesaTotalKsh)
        assertEquals(350L, report.cashTotalKsh)
        assertEquals(900L, report.totalWeightGrams) // 300/750 = 400g goat + 350/700 = 500g pork
    }
}
