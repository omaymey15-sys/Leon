package com.myschoolocr.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY name ASC")
    fun observeAll(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes ORDER BY name ASC")
    suspend fun getAllOnce(): List<ClassEntity>

    @Query("SELECT * FROM classes WHERE id = :classId")
    suspend fun getById(classId: Long): ClassEntity?

    @Insert
    suspend fun insert(cls: ClassEntity): Long

    @Update
    suspend fun update(cls: ClassEntity)

    @Delete
    suspend fun delete(cls: ClassEntity)
}

@Dao
interface ClassStudentDao {
    @Query("SELECT * FROM class_students WHERE classId = :classId ORDER BY name ASC")
    fun observeByClass(classId: Long): Flow<List<ClassStudentEntity>>

    @Query("SELECT * FROM class_students WHERE classId = :classId ORDER BY name ASC")
    suspend fun getByClassOnce(classId: Long): List<ClassStudentEntity>

    @Insert
    suspend fun insert(student: ClassStudentEntity): Long

    @Insert
    suspend fun insertAll(students: List<ClassStudentEntity>)

    @Update
    suspend fun update(student: ClassStudentEntity)

    @Delete
    suspend fun delete(student: ClassStudentEntity)
}
