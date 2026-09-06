package com.atuy.scomb.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClassCellDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<ClassCell>)

    @Query("DELETE FROM class_cell WHERE timetableTitle = :title AND isUserClassCell = 0")
    suspend fun removeApiTimetable(title: String)

    @Query("DELETE FROM class_cell")
    suspend fun clearAll()

    @Query("UPDATE class_cell SET note = :note, customColorInt = :color WHERE classId = :classId AND timetableTitle = :title")
    suspend fun updateDetails(classId: String, title: String, note: String?, color: Int?)

    @Query("UPDATE class_cell SET customLinksJson = :json WHERE classId = :classId AND timetableTitle = :title")
    suspend fun updateLinks(classId: String, title: String, json: String)


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

    @Query("SELECT * FROM class_cell WHERE classId = :classId")
    suspend fun getClassCellsById(classId: String): List<ClassCell>
}
