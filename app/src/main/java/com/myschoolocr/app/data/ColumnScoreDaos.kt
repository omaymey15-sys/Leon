package com.myschoolocr.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GridColumnDao {
    @Query("SELECT * FROM grid_columns")
    fun observeAll(): Flow<List<GridColumnEntity>>

    @Query("SELECT * FROM grid_columns WHERE gridId = :gridId ORDER BY position ASC")
    fun observeByGrid(gridId: Long): Flow<List<GridColumnEntity>>

    @Query("SELECT * FROM grid_columns WHERE gridId = :gridId ORDER BY position ASC")
    suspend fun getByGridOnce(gridId: Long): List<GridColumnEntity>

    @Insert
    suspend fun insert(column: GridColumnEntity): Long

    @Insert
    suspend fun insertAll(columns: List<GridColumnEntity>)

    @Update
    suspend fun update(column: GridColumnEntity)

    @Delete
    suspend fun delete(column: GridColumnEntity)
}

@Dao
interface StudentScoreDao {
    @Query("SELECT * FROM student_scores")
    fun observeAll(): Flow<List<StudentScoreEntity>>

    @Query(
        """
        SELECT student_scores.* FROM student_scores
        INNER JOIN students ON students.id = student_scores.studentId
        WHERE students.gridId = :gridId
        """
    )
    fun observeByGrid(gridId: Long): Flow<List<StudentScoreEntity>>

    @Query(
        """
        SELECT student_scores.* FROM student_scores
        INNER JOIN students ON students.id = student_scores.studentId
        WHERE students.gridId = :gridId
        """
    )
    suspend fun getByGridOnce(gridId: Long): List<StudentScoreEntity>

    /** Upsert : grâce à l'index unique (studentId, columnId), REPLACE écrase l'ancienne valeur. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: StudentScoreEntity)
}
