package com.example.data.repository

import com.example.data.local.dao.ClubPosDao
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ShiftRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ClubPosRepository(private val dao: ClubPosDao) {

    val allMenuItems: Flow<List<MenuItemEntity>> = dao.getAllMenuItems()
    val openOrders: Flow<List<ClubOrderEntity>> = dao.getOpenOrders()
    val recentPaidOrders: Flow<List<ClubOrderEntity>> = dao.getRecentPaidOrders()
    val activeShift: Flow<ShiftRecordEntity?> = dao.getActiveShift()
    val recentShifts: Flow<List<ShiftRecordEntity>> = dao.getRecentShifts()

    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>> =
        dao.getItemsForOrder(orderId)

    fun getOrderById(orderId: Long): Flow<ClubOrderEntity?> =
        dao.getOrderById(orderId)

    suspend fun initializeDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        val count = dao.getMenuItemCount()
        if (count == 0) {
            val defaultItems = listOf(
                // Cocktails
                MenuItemEntity(
                    name = "Vodka Red Bull",
                    category = "Cocktails",
                    price = 16.00,
                    alcoholType = "Vodka",
                    stockQuantity = 85,
                    popularSpeedRail = true,
                    volumeOrSize = "Highball (12oz)",
                    description = "Grey Goose Vodka + Ice Cold Red Bull Energy Can"
                ),
                MenuItemEntity(
                    name = "Espresso Martini",
                    category = "Cocktails",
                    price = 19.00,
                    alcoholType = "Vodka",
                    stockQuantity = 60,
                    popularSpeedRail = true,
                    volumeOrSize = "Coupe Glass",
                    description = "Fresh pulled espresso, Kahlúa, vanilla vodka, 3 coffee beans"
                ),
                MenuItemEntity(
                    name = "Spicy Mezcalita",
                    category = "Cocktails",
                    price = 18.00,
                    alcoholType = "Tequila",
                    stockQuantity = 48,
                    popularSpeedRail = true,
                    volumeOrSize = "Rocks Glass",
                    description = "400 Conejos Mezcal, fresh lime, agave, jalapeño, Tajín rim"
                ),
                MenuItemEntity(
                    name = "Long Island Iced Tea",
                    category = "Cocktails",
                    price = 18.00,
                    alcoholType = "Multi-Spirit",
                    stockQuantity = 75,
                    popularSpeedRail = true,
                    volumeOrSize = "Pint Glass",
                    description = "Vodka, gin, rum, tequila, triple sec, sour, splash cola"
                ),
                MenuItemEntity(
                    name = "Tokyo Tea",
                    category = "Cocktails",
                    price = 18.00,
                    alcoholType = "Multi-Spirit",
                    stockQuantity = 50,
                    popularSpeedRail = false,
                    volumeOrSize = "Pint Glass",
                    description = "Electric neon green Long Island variation with Midori melon"
                ),
                MenuItemEntity(
                    name = "Casamigos Paloma",
                    category = "Cocktails",
                    price = 17.00,
                    alcoholType = "Tequila",
                    stockQuantity = 55,
                    popularSpeedRail = true,
                    volumeOrSize = "Collins Glass",
                    description = "Casamigos Blanco, fresh grapefruit juice, lime, soda, salt rim"
                ),

                // Shots
                MenuItemEntity(
                    name = "Casamigos Reposado Shot",
                    category = "Shots",
                    price = 15.00,
                    alcoholType = "Tequila",
                    stockQuantity = 70,
                    popularSpeedRail = true,
                    volumeOrSize = "1.5 oz Shot",
                    description = "Chilled premium reposado served with fresh lime & orange slice"
                ),
                MenuItemEntity(
                    name = "Don Julio 1942 Neat Shot",
                    category = "Shots",
                    price = 35.00,
                    alcoholType = "Tequila",
                    stockQuantity = 24,
                    popularSpeedRail = false,
                    isVipExclusive = true,
                    volumeOrSize = "2.0 oz Snifter",
                    description = "Añejo tequila aged for minimum 2.5 years in oak barrels"
                ),
                MenuItemEntity(
                    name = "Green Tea Shot",
                    category = "Shots",
                    price = 12.00,
                    alcoholType = "Whiskey",
                    stockQuantity = 90,
                    popularSpeedRail = true,
                    volumeOrSize = "1.5 oz Shot",
                    description = "Jameson Irish Whiskey, peach schnapps, sour mix, splash Sprite"
                ),
                MenuItemEntity(
                    name = "Jägerbomb",
                    category = "Shots",
                    price = 14.00,
                    alcoholType = "Liqueur",
                    stockQuantity = 65,
                    popularSpeedRail = true,
                    volumeOrSize = "Bomb Shot",
                    description = "Chilled Jägermeister shot dropped into cold Red Bull"
                ),
                MenuItemEntity(
                    name = "Lemon Drop Shot",
                    category = "Shots",
                    price = 12.00,
                    alcoholType = "Vodka",
                    stockQuantity = 80,
                    popularSpeedRail = true,
                    volumeOrSize = "1.5 oz Shot",
                    description = "Citrus vodka, triple sec, simple syrup, sugared lemon wheel"
                ),
                MenuItemEntity(
                    name = "Tequila Rose Shot",
                    category = "Shots",
                    price = 11.00,
                    alcoholType = "Tequila",
                    stockQuantity = 40,
                    popularSpeedRail = false,
                    volumeOrSize = "1.5 oz Shot",
                    description = "Strawberry cream liqueur with a splash of premium tequila"
                ),

                // VIP Bottle Service
                MenuItemEntity(
                    name = "Don Julio 1942 (750ml)",
                    category = "Bottle Service",
                    price = 650.00,
                    alcoholType = "Tequila",
                    stockQuantity = 8,
                    isVipExclusive = true,
                    volumeOrSize = "750 ml Bottle",
                    description = "Served with 3 premium carafes, ice bucket & bottle sparklers"
                ),
                MenuItemEntity(
                    name = "Clase Azul Reposado (750ml)",
                    category = "Bottle Service",
                    price = 750.00,
                    alcoholType = "Tequila",
                    stockQuantity = 6,
                    isVipExclusive = true,
                    volumeOrSize = "750 ml Bottle",
                    description = "Handcrafted iconic ceramic decanter + VIP server presentation"
                ),
                MenuItemEntity(
                    name = "Dom Pérignon Brut (750ml)",
                    category = "Bottle Service",
                    price = 550.00,
                    alcoholType = "Champagne",
                    stockQuantity = 10,
                    isVipExclusive = true,
                    volumeOrSize = "750 ml Bottle",
                    description = "Prestige Champagne with glowing champagne chill bucket"
                ),
                MenuItemEntity(
                    name = "Moët & Chandon Impérial Brut",
                    category = "Bottle Service",
                    price = 325.00,
                    alcoholType = "Champagne",
                    stockQuantity = 18,
                    isVipExclusive = true,
                    volumeOrSize = "750 ml Bottle",
                    description = "Vibrant, elegant French champagne with crystal flutes"
                ),
                MenuItemEntity(
                    name = "Grey Goose Magnum (1.75L)",
                    category = "Bottle Service",
                    price = 600.00,
                    alcoholType = "Vodka",
                    stockQuantity = 5,
                    isVipExclusive = true,
                    volumeOrSize = "1.75 L Magnum",
                    description = "Giant bottle + 5 unlimited mixers (Red Bull, Tonic, Cranberry)"
                ),

                // Beers & Seltzers
                MenuItemEntity(
                    name = "Corona Extra",
                    category = "Beer & Seltzers",
                    price = 9.00,
                    alcoholType = "Beer",
                    stockQuantity = 140,
                    popularSpeedRail = true,
                    volumeOrSize = "12 oz Bottle",
                    description = "Crisp Mexican lager served with fresh lime wedge"
                ),
                MenuItemEntity(
                    name = "Heineken Silver",
                    category = "Beer & Seltzers",
                    price = 9.00,
                    alcoholType = "Beer",
                    stockQuantity = 120,
                    popularSpeedRail = true,
                    volumeOrSize = "12 oz Bottle",
                    description = "Extra-refreshing lager brewed at -1°C"
                ),
                MenuItemEntity(
                    name = "Stella Artois Draft",
                    category = "Beer & Seltzers",
                    price = 10.00,
                    alcoholType = "Beer",
                    stockQuantity = 95,
                    popularSpeedRail = false,
                    volumeOrSize = "16 oz Pint",
                    description = "Belgian pilsner with full floral aroma"
                ),
                MenuItemEntity(
                    name = "High Noon Black Cherry Seltzer",
                    category = "Beer & Seltzers",
                    price = 11.00,
                    alcoholType = "Seltzer",
                    stockQuantity = 110,
                    popularSpeedRail = true,
                    volumeOrSize = "12 oz Can",
                    description = "Real vodka, real juice, sparkling water, 100 calories"
                ),
                MenuItemEntity(
                    name = "White Claw Mango Seltzer",
                    category = "Beer & Seltzers",
                    price = 10.00,
                    alcoholType = "Seltzer",
                    stockQuantity = 115,
                    popularSpeedRail = true,
                    volumeOrSize = "12 oz Can",
                    description = "Crisp hard seltzer with sweet ripe mango essence"
                ),

                // Non-Alcoholic & Energy
                MenuItemEntity(
                    name = "Red Bull Energy (Can)",
                    category = "Non-Alc & Mixers",
                    price = 7.00,
                    alcoholType = "Non-Alc",
                    stockQuantity = 150,
                    popularSpeedRail = true,
                    volumeOrSize = "8.4 oz Can",
                    description = "Original or Sugarfree ice cold can with straw"
                ),
                MenuItemEntity(
                    name = "Liquid Death Sparkling Water",
                    category = "Non-Alc & Mixers",
                    price = 6.00,
                    alcoholType = "Non-Alc",
                    stockQuantity = 80,
                    popularSpeedRail = false,
                    volumeOrSize = "16.9 oz Tallboy",
                    description = "Mountain water from the Austrian Alps"
                ),
                MenuItemEntity(
                    name = "Night Owl Zero Mocktail",
                    category = "Non-Alc & Mixers",
                    price = 12.00,
                    alcoholType = "Non-Alc",
                    stockQuantity = 60,
                    popularSpeedRail = false,
                    volumeOrSize = "Highball Glass",
                    description = "Seedlip spice, muddled blackberries, fresh lime, ginger beer"
                ),

                // Late Night Bites
                MenuItemEntity(
                    name = "Truffle Parmesan Fries",
                    category = "Late Night Bites",
                    price = 14.00,
                    alcoholType = "Food",
                    stockQuantity = 50,
                    popularSpeedRail = false,
                    volumeOrSize = "Basket",
                    description = "Crispy golden shoestring fries, white truffle oil, shaved parmesan"
                ),
                MenuItemEntity(
                    name = "Spicy Crispy Chicken Sliders",
                    category = "Late Night Bites",
                    price = 16.00,
                    alcoholType = "Food",
                    stockQuantity = 45,
                    popularSpeedRail = false,
                    volumeOrSize = "3 Sliders",
                    description = "Nashville hot fried chicken, sweet brioche bun, house pickles"
                )
            )
            dao.insertMenuItems(defaultItems)
        }

        // Initialize active shift if none open
        val activeShiftRecord = dao.getActiveShiftDirect()
        if (activeShiftRecord == null) {
            val initialShift = ShiftRecordEntity(
                bartenderName = "Elena V. (Lead)",
                stationName = "Main Bar Station 1",
                startTime = System.currentTimeMillis() - (1000 * 60 * 95), // 95 min into shift
                totalSales = 842.00,
                totalTips = 168.00,
                cashCollected = 210.00,
                cardCollected = 800.00,
                compAmount = 32.00,
                ordersCount = 14,
                drinksPouredCount = 38,
                isShiftOpen = true
            )
            dao.insertShift(initialShift)
        }

        // Initialize sample active tabs if none exist
        val currentOpenOrders = dao.getOpenOrders()
        // If no open orders, create 2 sample tabs to demonstrate bar workflow
        val sampleOrderCheck = dao.getOrderByIdDirect(1)
        if (sampleOrderCheck == null) {
            val tab1Id = dao.insertOrder(
                ClubOrderEntity(
                    tabName = "VIP Booth 3 - Jordan",
                    tableNumber = "VIP-03",
                    bartenderName = "Elena V. (Lead)",
                    status = "OPEN",
                    createdAt = System.currentTimeMillis() - (1000 * 60 * 45),
                    subtotal = 650.00,
                    tax = 53.63,
                    tip = 130.00,
                    total = 833.63,
                    cardLast4 = "7749",
                    isVipTable = true,
                    notes = "Birthday celebration - bring sparklers!"
                )
            )
            dao.insertOrderItem(
                OrderItemEntity(
                    orderId = tab1Id,
                    menuItemId = 13,
                    itemName = "Don Julio 1942 (750ml)",
                    itemCategory = "Bottle Service",
                    unitPrice = 650.00,
                    quantity = 1,
                    modifiers = "Sparklers Included, 3x Red Bull Mixers"
                )
            )

            val tab2Id = dao.insertOrder(
                ClubOrderEntity(
                    tabName = "Dance Floor Bar - Chris",
                    tableNumber = "BAR-04",
                    bartenderName = "Elena V. (Lead)",
                    status = "OPEN",
                    createdAt = System.currentTimeMillis() - (1000 * 60 * 18),
                    subtotal = 48.00,
                    tax = 3.96,
                    tip = 0.0,
                    total = 51.96,
                    cardLast4 = "3312",
                    isVipTable = false
                )
            )
            dao.insertOrderItem(
                OrderItemEntity(
                    orderId = tab2Id,
                    menuItemId = 1,
                    itemName = "Vodka Red Bull",
                    itemCategory = "Cocktails",
                    unitPrice = 16.00,
                    quantity = 2,
                    modifiers = "Double (+4.00), Extra Ice"
                )
            )
            dao.insertOrderItem(
                OrderItemEntity(
                    orderId = tab2Id,
                    menuItemId = 7,
                    itemName = "Casamigos Reposado Shot",
                    itemCategory = "Shots",
                    unitPrice = 15.00,
                    quantity = 1,
                    modifiers = "Chilled, Lime & Salt"
                )
            )
        }
    }

    suspend fun createNewTab(
        tabName: String,
        tableNumber: String,
        bartenderName: String,
        isVipTable: Boolean,
        cardLast4: String?,
        notes: String
    ): Long = withContext(Dispatchers.IO) {
        val newOrder = ClubOrderEntity(
            tabName = tabName.ifBlank { "Quick Bar Sale" },
            tableNumber = tableNumber,
            bartenderName = bartenderName,
            status = "OPEN",
            createdAt = System.currentTimeMillis(),
            isVipTable = isVipTable,
            cardLast4 = cardLast4?.takeIf { it.isNotBlank() },
            notes = notes
        )
        dao.insertOrder(newOrder)
    }

    suspend fun addItemToOrder(
        orderId: Long,
        menuItem: MenuItemEntity,
        modifiers: String = "",
        extraModifierCharge: Double = 0.0,
        quantity: Int = 1
    ) = withContext(Dispatchers.IO) {
        val unitPrice = menuItem.price + extraModifierCharge
        val orderItem = OrderItemEntity(
            orderId = orderId,
            menuItemId = menuItem.id,
            itemName = menuItem.name,
            itemCategory = menuItem.category,
            unitPrice = unitPrice,
            quantity = quantity,
            modifiers = modifiers
        )
        dao.insertOrderItem(orderItem)
        // Deduct inventory
        dao.updateStock(menuItem.id, -quantity)
        // Recalculate totals
        recalculateOrderTotals(orderId)
    }

    suspend fun removeItemFromOrder(orderItemId: Long, orderId: Long) = withContext(Dispatchers.IO) {
        dao.deleteOrderItem(orderItemId)
        recalculateOrderTotals(orderId)
    }

    suspend fun updateItemQuantity(orderItemId: Long, orderId: Long, newQuantity: Int) = withContext(Dispatchers.IO) {
        if (newQuantity <= 0) {
            dao.deleteOrderItem(orderItemId)
        } else {
            dao.updateOrderItemQuantity(orderItemId, newQuantity)
        }
        recalculateOrderTotals(orderId)
    }

    suspend fun recalculateOrderTotals(orderId: Long) = withContext(Dispatchers.IO) {
        val order = dao.getOrderByIdDirect(orderId) ?: return@withContext
        val items = dao.getItemsForOrderDirect(orderId)
        val subtotal = items.sumOf { it.unitPrice * it.quantity }
        val taxRate = 0.0825 // 8.25% standard liquor & sales tax
        val tax = (subtotal * taxRate)
        val tip = order.tip
        val total = subtotal + tax + tip

        val updatedOrder = order.copy(
            subtotal = (subtotal * 100).toLong() / 100.0,
            tax = (tax * 100).toLong() / 100.0,
            tip = (tip * 100).toLong() / 100.0,
            total = (total * 100).toLong() / 100.0
        )
        dao.updateOrder(updatedOrder)
    }

    suspend fun updateOrderTip(orderId: Long, tipAmount: Double) = withContext(Dispatchers.IO) {
        val order = dao.getOrderByIdDirect(orderId) ?: return@withContext
        val total = order.subtotal + order.tax + tipAmount
        dao.updateOrder(
            order.copy(
                tip = tipAmount,
                total = total
            )
        )
    }

    suspend fun processPayment(
        orderId: Long,
        paymentMethod: String,
        tipAmount: Double,
        cardLast4: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val order = dao.getOrderByIdDirect(orderId) ?: return@withContext false
        val items = dao.getItemsForOrderDirect(orderId)
        val subtotal = items.sumOf { it.unitPrice * it.quantity }
        val tax = (subtotal * 0.0825)
        val finalTip = tipAmount
        val total = if (paymentMethod == "VIP_COMP") 0.0 else subtotal + tax + finalTip

        val updatedOrder = order.copy(
            status = "PAID",
            closedAt = System.currentTimeMillis(),
            subtotal = subtotal,
            tax = tax,
            tip = finalTip,
            total = total,
            paymentMethod = paymentMethod,
            cardLast4 = cardLast4 ?: order.cardLast4
        )
        dao.updateOrder(updatedOrder)

        // Update active shift stats
        val activeShift = dao.getActiveShiftDirect()
        if (activeShift != null) {
            val isCash = paymentMethod == "CASH"
            val isComp = paymentMethod == "VIP_COMP"
            val drinksCount = items.sumOf { it.quantity }

            val updatedShift = activeShift.copy(
                totalSales = activeShift.totalSales + (if (isComp) 0.0 else total),
                totalTips = activeShift.totalTips + finalTip,
                cashCollected = activeShift.cashCollected + (if (isCash) total else 0.0),
                cardCollected = activeShift.cardCollected + (if (!isCash && !isComp) total else 0.0),
                compAmount = activeShift.compAmount + (if (isComp) subtotal else 0.0),
                ordersCount = activeShift.ordersCount + 1,
                drinksPouredCount = activeShift.drinksPouredCount + drinksCount
            )
            dao.updateShift(updatedShift)
        }
        true
    }

    suspend fun voidOrder(orderId: Long) = withContext(Dispatchers.IO) {
        dao.voidOrder(orderId)
    }

    suspend fun restockItem(itemId: Long, amount: Int) = withContext(Dispatchers.IO) {
        dao.updateStock(itemId, amount)
    }

    suspend fun startNewShift(bartenderName: String, stationName: String): Long = withContext(Dispatchers.IO) {
        // Close previous open shift if any
        val current = dao.getActiveShiftDirect()
        if (current != null) {
            dao.updateShift(current.copy(isShiftOpen = false, endTime = System.currentTimeMillis()))
        }
        val newShift = ShiftRecordEntity(
            bartenderName = bartenderName,
            stationName = stationName,
            startTime = System.currentTimeMillis()
        )
        dao.insertShift(newShift)
    }

    suspend fun closeActiveShift(): ShiftRecordEntity? = withContext(Dispatchers.IO) {
        val current = dao.getActiveShiftDirect() ?: return@withContext null
        val closed = current.copy(
            isShiftOpen = false,
            endTime = System.currentTimeMillis()
        )
        dao.updateShift(closed)
        closed
    }
}
