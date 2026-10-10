package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ShiftRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubPosDao {

    // === Menu Items ===
    @Query("SELECT * FROM menu_items ORDER BY popularSpeedRail DESC, category ASC, name ASC")
    fun getAllMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE category = :category ORDER BY popularSpeedRail DESC, name ASC")
    fun getMenuItemsByCategory(category: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE name LIKE '%' || :query || '%' OR alcoholType LIKE '%' || :query || '%'")
    fun searchMenuItems(query: String): Flow<List<MenuItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItems(items: List<MenuItemEntity>)

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun getMenuItemCount(): Int

    @Query("UPDATE menu_items SET stockQuantity = stockQuantity + :amount WHERE id = :itemId")
    suspend fun updateStock(itemId: Long, amount: Int)

    // === Club Orders ===
    @Query("SELECT * FROM club_orders WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun getOpenOrders(): Flow<List<ClubOrderEntity>>

    @Query("SELECT * FROM club_orders WHERE status = 'PAID' ORDER BY closedAt DESC LIMIT 50")
    fun getRecentPaidOrders(): Flow<List<ClubOrderEntity>>

    @Query("SELECT * FROM club_orders WHERE id = :orderId")
    fun getOrderById(orderId: Long): Flow<ClubOrderEntity?>

    @Query("SELECT * FROM club_orders WHERE id = :orderId")
    suspend fun getOrderByIdDirect(orderId: Long): ClubOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: ClubOrderEntity): Long

    @Update
    suspend fun updateOrder(order: ClubOrderEntity)

    @Query("UPDATE club_orders SET status = 'VOIDED' WHERE id = :orderId")
    suspend fun voidOrder(orderId: Long)

    // === Order Items ===
    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY addedAt ASC")
    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY addedAt ASC")
    suspend fun getItemsForOrderDirect(orderId: Long): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity): Long

    @Query("DELETE FROM order_items WHERE id = :itemId")
    suspend fun deleteOrderItem(itemId: Long)

    @Query("UPDATE order_items SET quantity = :quantity WHERE id = :itemId")
    suspend fun updateOrderItemQuantity(itemId: Long, quantity: Int)

    // === Shift Records ===
    @Query("SELECT * FROM shift_records WHERE isShiftOpen = 1 ORDER BY startTime DESC LIMIT 1")
    fun getActiveShift(): Flow<ShiftRecordEntity?>

    @Query("SELECT * FROM shift_records WHERE isShiftOpen = 1 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveShiftDirect(): ShiftRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftRecordEntity): Long

    @Update
    suspend fun updateShift(shift: ShiftRecordEntity)

    @Query("SELECT * FROM shift_records ORDER BY startTime DESC LIMIT 10")
    fun getRecentShifts(): Flow<List<ShiftRecordEntity>>
}
