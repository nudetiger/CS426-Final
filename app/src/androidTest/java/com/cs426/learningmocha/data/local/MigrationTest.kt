package com.cs426.learningmocha.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path a user with an installed build actually takes. Data loss here is
 * unrecoverable, so every migration is exercised against real data rather than an empty file.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * v1 predates `exportSchema`, so there is no 1.json for MigrationTestHelper to build from —
     * the v1 file is recreated by hand instead. Opening the result through Room still validates
     * the end state: Room compares the live schema against v3 and throws on any mismatch.
     */
    @Test
    fun migrates1To3KeepingPosts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        val v1 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName),
            null,
        )
        v1.use {
            it.version = 1
            it.execSQL(
                "CREATE TABLE IF NOT EXISTS `nodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                    "NOT NULL, `parentId` INTEGER, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`content` TEXT, `status` TEXT NOT NULL, `favorite` INTEGER NOT NULL, " +
                    "`orderIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, FOREIGN KEY(`parentId`) REFERENCES `nodes`" +
                    "(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            it.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_parentId` ON `nodes` (`parentId`)")
            it.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_title` ON `nodes` (`title`)")
            it.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_updatedAt` ON `nodes` (`updatedAt`)")
            it.execSQL("CREATE INDEX IF NOT EXISTS `index_nodes_type` ON `nodes` (`type`)")
            it.execSQL(
                """
                INSERT INTO nodes (id, parentId, type, title, content, status, favorite,
                    orderIndex, createdAt, updatedAt)
                VALUES (1, NULL, 'BRANCH', 'Backend', NULL, 'NONE', 0, 0, 100, 100)
                """.trimIndent(),
            )
            it.execSQL(
                """
                INSERT INTO nodes (id, parentId, type, title, content, status, favorite,
                    orderIndex, createdAt, updatedAt)
                VALUES (2, 1, 'POST', 'Spring Boot', '# Spring Boot', 'FINISHED', 1, 0, 200, 200)
                """.trimIndent(),
            )
        }

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName,
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            db.query("SELECT title, status, favorite FROM nodes WHERE id = 2", null).use { c ->
                assertTrue("the post survived the upgrade", c.moveToFirst())
                assertEquals("Spring Boot", c.getString(0))
                assertEquals("FINISHED", c.getString(1))
                assertEquals(1, c.getInt(2))
            }
            // Phase 2 rebuilt the FTS index from existing rows rather than starting empty.
            db.query("SELECT COUNT(*) FROM posts_fts WHERE posts_fts MATCH 'spring'", null)
                .use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(1, c.getInt(0))
                }
            db.query("SELECT COUNT(*) FROM chat_sessions", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrates2To3AddingChatTables() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO nodes (id, parentId, type, title, content, status, favorite,
                    orderIndex, createdAt, updatedAt)
                VALUES (1, NULL, 'POST', 'Raft', '# Raft', 'READING', 0, 0, 1, 1)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'consensus')")
            db.execSQL("INSERT INTO post_tags (postId, tagId) VALUES (1, 1)")
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT name FROM tags").use { c ->
            assertTrue("tags survived", c.moveToFirst())
            assertEquals("consensus", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM chat_messages").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }
}
