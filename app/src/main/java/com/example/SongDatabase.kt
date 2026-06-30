package com.example

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val timestamp: Long = System.currentTimeMillis(),
    val artworkUrl: String? = null
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY timestamp DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Query("SELECT * FROM songs ORDER BY timestamp DESC")
    suspend fun getRecentSongs(): List<Song>

    @Query("DELETE FROM songs WHERE id NOT IN (SELECT id FROM songs ORDER BY timestamp DESC LIMIT 5)")
    suspend fun keepOnlyLatest5()

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
}

@Database(entities = [Song::class], version = 2, exportSchema = false)
abstract class SongDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
