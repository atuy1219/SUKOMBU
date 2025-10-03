package com.atuy.scomb.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClassCellDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassCell(classCell: ClassCell)

    @Delete
    suspend fun removeClassCell(classCell: ClassCell)

    @Query("SELECT * FROM class_cell")
    suspend fun getAllClasses(): List<ClassCell>

    @Query("SELECT * FROM class_cell WHERE timetableTitle = :timetableTitle")
    suspend fun getCells(timetableTitle: String): List<ClassCell>

    @Query("DELETE FROM class_cell WHERE timetableTitle = :timetableTitle")
    suspend fun removeTimetable(timetableTitle: String)
}