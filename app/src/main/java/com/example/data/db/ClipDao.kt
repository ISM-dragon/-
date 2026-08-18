package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Clip
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY viralityScore DESC")
    fun getClipsForProject(projectId: Long): Flow<List<Clip>>

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY viralityScore DESC")
    suspend fun getClipsForProjectSync(projectId: Long): List<Clip>

    @Query("SELECT * FROM clips WHERE id = :id")
    fun getClipById(id: Long): Flow<Clip?>

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun getClipByIdSync(id: Long): Clip?

    @Query("SELECT * FROM clips WHERE isFavorite = 1 ORDER BY viralityScore DESC")
    fun getFavoriteClips(): Flow<List<Clip>>

    @Query("SELECT * FROM clips ORDER BY viralityScore DESC")
    fun getAllClips(): Flow<List<Clip>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: Clip): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<Clip>)

    @Update
    suspend fun updateClip(clip: Clip)

    @Delete
    suspend fun deleteClip(clip: Clip)

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: Long)

    @Query("UPDATE clips SET isFavorite = :isFavorite WHERE id = :clipId")
    suspend fun setFavorite(clipId: Long, isFavorite: Boolean)

    @Query("UPDATE clips SET layoutType = :layoutType WHERE id = :clipId")
    suspend fun updateLayoutType(clipId: Long, layoutType: String)

    @Query("UPDATE clips SET animatedCaptionsJson = :captionsJson, transcript = :transcript WHERE id = :clipId")
    suspend fun updateCaptions(clipId: Long, captionsJson: String, transcript: String)

    @Query("UPDATE clips SET exportPath = :exportPath WHERE id = :clipId")
    suspend fun updateExportPath(clipId: Long, exportPath: String)
}
