package com.myschoolocr.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GridDao {
    @Query("SELECT * FROM grids ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GridEntity>>

    @Query("SELECT * FROM grids ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<GridEntity>

    @Query("SELECT * FROM grids WHERE id = :gridId")
    suspend fun getById(gridId: Long): GridEntity?

    @Insert
    suspend fun insert(grid: GridEntity): Long

    @Update
    suspend fun update(grid: GridEntity)

    @Delete
    suspend fun delete(grid: GridEntity)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE gridId = :gridId ORDER BY name ASC")
    fun observeByGrid(gridId: Long): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE gridId = :gridId ORDER BY name ASC")
    suspend fun getByGridOnce(gridId: Long): List<StudentEntity>

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Insert
    suspend fun insertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity)

    @Delete
    suspend fun delete(student: StudentEntity)
}
