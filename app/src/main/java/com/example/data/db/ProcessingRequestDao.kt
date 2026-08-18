package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ProcessingRequestEntity

@Dao
interface ProcessingRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: ProcessingRequestEntity)

    @Query("SELECT * FROM processing_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getById(requestId: String): ProcessingRequestEntity?

    @Query("DELETE FROM processing_requests WHERE requestId = :requestId")
    suspend fun deleteById(requestId: String)
}
