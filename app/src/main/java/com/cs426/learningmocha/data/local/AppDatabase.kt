package com.cs426.learningmocha.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cs426.learningmocha.data.local.dao.KnowledgeDao
import com.cs426.learningmocha.data.local.dao.NodeDao
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.Link
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeConverters
import com.cs426.learningmocha.data.local.entity.PostFts
import com.cs426.learningmocha.data.local.entity.PostTag
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.Tag

@Database(
    entities = [
        Node::class,
        Link::class,
        Tag::class,
        PostTag::class,
        DictionaryEntry::class,
        ResourceItem::class,
        PostFts::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(NodeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `links` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromPostId` INTEGER NOT NULL,
                        `toPostId` INTEGER NOT NULL,
                        `anchorText` TEXT NOT NULL,
                        FOREIGN KEY(`fromPostId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`toPostId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_fromPostId` ON `links` (`fromPostId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_toPostId` ON `links` (`toPostId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `post_tags` (
                        `postId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`postId`, `tagId`),
                        FOREIGN KEY(`postId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_post_tags_tagId` ON `post_tags` (`tagId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dictionary` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `postId` INTEGER,
                        `term` TEXT NOT NULL,
                        `definition` TEXT NOT NULL,
                        `meaningVi` TEXT NOT NULL,
                        FOREIGN KEY(`postId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_postId` ON `dictionary` (`postId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_term` ON `dictionary` (`term`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `resources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `postId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        FOREIGN KEY(`postId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_resources_postId` ON `resources` (`postId`)")

                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `posts_fts` USING FTS4(`title` TEXT NOT NULL, `content` TEXT, content=`nodes`)",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_posts_fts_BEFORE_UPDATE BEFORE UPDATE ON `nodes` BEGIN DELETE FROM `posts_fts` WHERE `docid`=OLD.`rowid`; END",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_posts_fts_BEFORE_DELETE BEFORE DELETE ON `nodes` BEGIN DELETE FROM `posts_fts` WHERE `docid`=OLD.`rowid`; END",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_posts_fts_AFTER_UPDATE AFTER UPDATE ON `nodes` BEGIN INSERT INTO `posts_fts`(`docid`, `title`, `content`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END",
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_posts_fts_AFTER_INSERT AFTER INSERT ON `nodes` BEGIN INSERT INTO `posts_fts`(`docid`, `title`, `content`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END",
                )
                db.execSQL("INSERT INTO `posts_fts`(`posts_fts`) VALUES('rebuild')")
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "learning_mocha.db",
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
