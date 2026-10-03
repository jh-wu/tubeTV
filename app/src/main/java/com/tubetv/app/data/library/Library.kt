package com.tubetv.app.data.library

import com.tubetv.app.data.model.ChannelSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The viewer's favourite channels and the channels they have browsed, kept on the device. */
class ChannelLibrary(private val dao: ChannelDao, private val now: () -> Long = System::currentTimeMillis) {

    val favourites: Flow<List<ChannelSummary>> = dao.favourites().map { list -> list.map { it.summary } }
    val browsed: Flow<List<BrowsedChannel>> = dao.browsed()

    fun isFavourite(url: String): Flow<Boolean> = dao.isFavourite(url)

    suspend fun addFavourite(channel: ChannelSummary) =
        dao.upsertFavourite(FavouriteChannel(channel.url, channel.name, channel.avatarUrl, channel.subscriberCount, now()))

    suspend fun removeFavourite(url: String) = dao.removeFavourite(url)

    /** Records a visit to [channel], moving it to the front of the browsing history. */
    suspend fun visited(channel: ChannelSummary) {
        val visits = (dao.getBrowsed(channel.url)?.visits ?: 0) + 1
        dao.upsertBrowsed(BrowsedChannel(channel.url, channel.name, channel.avatarUrl, channel.subscriberCount, now(), visits))
    }

    suspend fun forget(url: String) = dao.removeBrowsed(url)

    suspend fun clearBrowsed() = dao.clearBrowsed()
}
