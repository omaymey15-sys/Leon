package com.myschoolocr.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingScanDao {
    @Query("SELECT * FROM pending_scans WHERE gridId = :gridId ORDER BY createdAt ASC")
    fun observeByGrid(gridId: Long): Flow<List<PendingScanEntity>>

    @Insert
    suspend fun insert(pendingScan: PendingScanEntity): Long

    @Delete
    suspend fun delete(pendingScan: PendingScanEntity)
}
