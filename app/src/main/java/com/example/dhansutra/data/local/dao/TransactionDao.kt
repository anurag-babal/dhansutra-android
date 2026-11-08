package com.example.dhansutra.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.TransactionNature
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
        SELECT t.*, c.name AS categoryName, c.iconName AS categoryIcon, l.counterparty AS loanName, l.direction AS loanDirection
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        LEFT JOIN loans l ON l.id = t.loanId
        WHERE t.dateMillis BETWEEN :startMillis AND :endMillis
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:query = '' OR t.notes LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%'
               OR l.counterparty LIKE '%' || :query || '%')
        ORDER BY t.dateMillis DESC, t.id DESC
        """,
    )
    fun observeFiltered(
        startMillis: Long,
        endMillis: Long,
        categoryId: Long?,
        query: String,
    ): Flow<List<TransactionWithCategory>>

    /** Transactions of one [nature] in the range, newest first (e.g. the periodic breakdown). */
    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.iconName AS categoryIcon, NULL AS loanName, NULL AS loanDirection
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.type = 'EXPENSE' AND t.nature = :nature
          AND t.dateMillis BETWEEN :startMillis AND :endMillis
        ORDER BY t.dateMillis DESC, t.id DESC
        """,
    )
    fun observeByNature(
        nature: TransactionNature,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TransactionWithCategory>>

    /**
     * Net outflow (expenses minus incomes) of one [nature] in the range. For INVESTMENT this is the net
     * amount invested; use 0..Long.MAX_VALUE for all time.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE -amount END), 0) FROM transactions
        WHERE nature = :nature AND dateMillis BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeNetOutflow(nature: TransactionNature, startMillis: Long, endMillis: Long): Flow<Long>

    /** Total of [type] over all time, regardless of nature: the basis of the real cash balance. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type")
    fun observeAllTimeTotal(type: TransactionType): Flow<Long>

    /** [nature] null means all expense types. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
          AND (:nature IS NULL OR nature = :nature)
        """,
    )
    fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long, nature: TransactionNature?): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
          AND (:nature IS NULL OR nature = :nature)
        """,
    )
    suspend fun getTotal(type: TransactionType, startMillis: Long, endMillis: Long, nature: TransactionNature?): Long

    /** Total per category for the given type (typically EXPENSE) in the range. [nature] null means all. */
    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
          AND (:nature IS NULL OR nature = :nature)
        GROUP BY categoryId
        """,
    )
    fun observeTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        nature: TransactionNature?,
    ): Flow<List<CategorySum>>

    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM transactions
        WHERE type = :type AND dateMillis BETWEEN :startMillis AND :endMillis
          AND (:nature IS NULL OR nature = :nature)
        GROUP BY categoryId
        """,
    )
    suspend fun getTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        nature: TransactionNature?,
    ): List<CategorySum>

    /** Per-month totals (device local time) from [startMillis] onwards, split by type and expense type. */
    @Query(
        """
        SELECT strftime('%Y-%m', dateMillis / 1000, 'unixepoch', 'localtime') AS monthYear,
               type, nature, SUM(amount) AS total
        FROM transactions
        WHERE dateMillis >= :startMillis
        GROUP BY monthYear, type, nature
        ORDER BY monthYear
        """,
    )
    fun observeMonthTotals(startMillis: Long): Flow<List<MonthTypeTotal>>

    /** Per-month, per-category totals for [type] up to [endMillis]. [nature] null means all. */
    @Query(
        """
        SELECT strftime('%Y-%m', dateMillis / 1000, 'unixepoch', 'localtime') AS monthYear,
               categoryId, SUM(amount) AS total
        FROM transactions
        WHERE type = :type AND dateMillis <= :endMillis
          AND (:nature IS NULL OR nature = :nature)
        GROUP BY monthYear, categoryId
        """,
    )
    fun observeMonthCategoryTotals(
        type: TransactionType,
        endMillis: Long,
        nature: TransactionNature?,
    ): Flow<List<MonthCategorySum>>
}
