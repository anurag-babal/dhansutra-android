package com.example.dhansutra.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    /**
     * Filtered list. [startMillis]/[endMillis] are inclusive bounds, [categoryId] null means all
     * categories, [query] blank means no text filter (matches notes or category name).
     */
    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.iconName AS categoryIcon
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.dateMillis BETWEEN :startMillis AND :endMillis
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:query = '' OR t.notes LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%')
        ORDER BY t.dateMillis DESC, t.id DESC
        """,
    )
    fun observeFiltered(
        startMillis: Long,
        endMillis: Long,
        categoryId: Long?,
        query: String,
    ): Flow<List<TransactionWithCategory>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
        """,
    )
    suspend fun getTotal(type: TransactionType, startMillis: Long, endMillis: Long): Long

    /** Total per category for the given type (typically EXPENSE) in the range. */
    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
        GROUP BY categoryId
        """,
    )
    fun observeTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<CategorySum>>

    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
        GROUP BY categoryId
        """,
    )
    suspend fun getTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
    ): List<CategorySum>

    /** Per-month totals (device local time) from [startMillis] onwards, used for trend charts. */
    @Query(
        """
        SELECT strftime('%Y-%m', dateMillis / 1000, 'unixepoch', 'localtime') AS monthYear,
               type, SUM(amount) AS total
        FROM transactions
        WHERE dateMillis >= :startMillis
        GROUP BY monthYear, type
        ORDER BY monthYear
        """,
    )
    fun observeMonthTotals(startMillis: Long): Flow<List<MonthTypeTotal>>

    /** Per-month, per-category totals for [type] up to [endMillis]; used for savings (sweep) calculation. */
    @Query(
        """
        SELECT strftime('%Y-%m', dateMillis / 1000, 'unixepoch', 'localtime') AS monthYear,
               categoryId, SUM(amount) AS total
        FROM transactions
        WHERE type = :type AND dateMillis <= :endMillis
        GROUP BY monthYear, categoryId
        """,
    )
    fun observeMonthCategoryTotals(type: TransactionType, endMillis: Long): Flow<List<MonthCategorySum>>
}
