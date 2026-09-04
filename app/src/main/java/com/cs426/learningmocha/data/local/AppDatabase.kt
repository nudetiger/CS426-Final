package com.cs426.learningmocha.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cs426.learningmocha.data.local.dao.NodeDao
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeConverters

@Database(
    entities = [Node::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(NodeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "learning_mocha.db",
            ).build()
        }
    }
}
