package com.tubetv.app.data.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.tubetv.app.data.model.ChannelSummary
import kotlinx.coroutines.flow.Flow

/** A channel the viewer marked as a favourite. */
@Entity(tableName = "favourite_channels")
data class FavouriteChannel(
    @PrimaryKey val url: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long,
    val addedAt: Long,
) {
    val summary get() = ChannelSummary(url, name, avatarUrl, subscriberCount)
}

/** A channel the viewer opened, remembered so it is one click away next time. */
@Entity(tableName = "browsed_channels")
data class BrowsedChannel(
    @PrimaryKey val url: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long,
    val lastVisitedAt: Long,
    val visits: Int,
) {
    val summary get() = ChannelSummary(url, name, avatarUrl, subscriberCount)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM favourite_channels ORDER BY addedAt DESC")
    fun favourites(): Flow<List<FavouriteChannel>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_channels WHERE url = :url)")
    fun isFavourite(url: String): Flow<Boolean>

    @Upsert
    suspend fun upsertFavourite(channel: FavouriteChannel)

    @Query("DELETE FROM favourite_channels WHERE url = :url")
    suspend fun removeFavourite(url: String)

    @Query("SELECT * FROM browsed_channels ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun browsed(limit: Int = 200): Flow<List<BrowsedChannel>>

    @Query("SELECT * FROM browsed_channels WHERE url = :url")
    suspend fun getBrowsed(url: String): BrowsedChannel?

    @Upsert
    suspend fun upsertBrowsed(channel: BrowsedChannel)

    @Query("DELETE FROM browsed_channels WHERE url = :url")
    suspend fun removeBrowsed(url: String)

    @Query("DELETE FROM browsed_channels")
    suspend fun clearBrowsed()
}
