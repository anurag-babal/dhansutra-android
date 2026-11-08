package com.example.dhansutra.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dhansutra.data.local.entity.LoanEntity
import com.example.dhansutra.domain.model.LoanDirection
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Insert
    suspend fun insert(loan: LoanEntity): Long

    /** Existing loan with the same person and direction (case-insensitive), if any. */
    @Query("SELECT * FROM loans WHERE counterparty = :counterparty COLLATE NOCASE AND direction = :direction LIMIT 1")
    suspend fun find(counterparty: String, direction: LoanDirection): LoanEntity?

    @Query("SELECT * FROM loans ORDER BY counterparty COLLATE NOCASE")
    fun observeAll(): Flow<List<LoanEntity>>

    /** Every loan with the net money out (expenses - incomes) of its linked transactions. */
    @Query(
        """
        SELECT l.id AS id, l.counterparty AS counterparty, l.direction AS direction,
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE -t.amount END), 0) AS net
        FROM loans l
        LEFT JOIN transactions t ON t.loanId = l.id
        GROUP BY l.id
        ORDER BY l.counterparty COLLATE NOCASE
        """,
    )
    fun observeNets(): Flow<List<LoanNet>>
}
