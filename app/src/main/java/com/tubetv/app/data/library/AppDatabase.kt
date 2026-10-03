package com.tubetv.app.data.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WatchRecord::class, FavouriteChannel::class, BrowsedChannel::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistory(): WatchHistoryDao
    abstract fun channels(): ChannelDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tubetv.db").build()
    }
}
