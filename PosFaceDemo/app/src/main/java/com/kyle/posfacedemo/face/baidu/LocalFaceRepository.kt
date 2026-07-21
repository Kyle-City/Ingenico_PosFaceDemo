package com.kyle.posfacedemo.face.baidu

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class LocalFaceRepository(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TEST_USER (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_key TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                feature BLOB NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateToVersion2(db)
        }
    }

    private fun migrateToVersion2(db: SQLiteDatabase) {
        val columns = db.rawQuery("PRAGMA table_info($TABLE_TEST_USER)", null).use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names += cursor.getString(nameIndex)
            }
            names
        }
        if (COLUMN_USER_KEY !in columns) {
            db.execSQL("ALTER TABLE $TABLE_TEST_USER ADD COLUMN $COLUMN_USER_KEY TEXT")
        }
        if (COLUMN_DISPLAY_NAME !in columns) {
            db.execSQL("ALTER TABLE $TABLE_TEST_USER ADD COLUMN $COLUMN_DISPLAY_NAME TEXT")
        }
        db.execSQL(
            """
            UPDATE $TABLE_TEST_USER
            SET $COLUMN_USER_KEY = COALESCE($COLUMN_USER_KEY, $COLUMN_TEST_USER_ID, 'test-user-' || $COLUMN_ID)
            WHERE $COLUMN_USER_KEY IS NULL OR $COLUMN_USER_KEY = ''
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE $TABLE_TEST_USER
            SET $COLUMN_DISPLAY_NAME = COALESCE($COLUMN_DISPLAY_NAME, '测试用户 ' || $COLUMN_ID)
            WHERE $COLUMN_DISPLAY_NAME IS NULL OR $COLUMN_DISPLAY_NAME = ''
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${TABLE_TEST_USER}_${COLUMN_USER_KEY} ON $TABLE_TEST_USER($COLUMN_USER_KEY)")
    }

    @Throws(SQLiteConstraintException::class)
    fun createUser(displayName: String, feature: ByteArray): LocalUserSummary? {
        val normalizedDisplayName = displayName.trim()
        if (normalizedDisplayName.isEmpty() || isDisplayNameExists(normalizedDisplayName)) {
            return null
        }
        val internalUserKey = UUID.randomUUID().toString()
        val featureCopy = feature.copyOf()
        return try {
            writableDatabase.use { db ->
                val schema = getTableSchema(db)
                val values = ContentValues().apply {
                    if (schema.hasUserKeyColumn) {
                        put(COLUMN_USER_KEY, internalUserKey)
                    }
                    if (schema.hasLegacyTestUserIdColumn) {
                        put(COLUMN_TEST_USER_ID, internalUserKey)
                    }
                    if (schema.hasDisplayNameColumn) {
                        put(COLUMN_DISPLAY_NAME, normalizedDisplayName)
                    }
                    put(COLUMN_FEATURE, featureCopy)
                    put(COLUMN_CREATED_AT, System.currentTimeMillis())
                }
                val localId = db.insert(TABLE_TEST_USER, null, values)
                if (localId > 0L) {
                    getUserSummary(db, localId.toInt())
                } else {
                    null
                }
            }
        } finally {
            featureCopy.fill(0)
        }
    }

    private fun getTableSchema(db: SQLiteDatabase): TableSchema {
        val columns = db.rawQuery("PRAGMA table_info($TABLE_TEST_USER)", null).use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names += cursor.getString(nameIndex)
            }
            names
        }
        return TableSchema(
            hasLegacyTestUserIdColumn = COLUMN_TEST_USER_ID in columns,
            hasUserKeyColumn = COLUMN_USER_KEY in columns,
            hasDisplayNameColumn = COLUMN_DISPLAY_NAME in columns
        )
    }

    fun isDisplayNameExists(displayName: String): Boolean {
        val normalizedDisplayName = displayName.trim()
        if (normalizedDisplayName.isEmpty()) return false
        readableDatabase.query(
            TABLE_TEST_USER,
            arrayOf(COLUMN_ID),
            "$COLUMN_DISPLAY_NAME = ? COLLATE NOCASE",
            arrayOf(normalizedDisplayName),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun getUserCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TEST_USER", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun getAllUsersSummary(): List<LocalUserSummary> {
        val users = mutableListOf<LocalUserSummary>()
        readableDatabase.query(
            TABLE_TEST_USER,
            arrayOf(COLUMN_ID, COLUMN_DISPLAY_NAME, COLUMN_CREATED_AT),
            null,
            null,
            null,
            null,
            COLUMN_ID
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val displayNameIndex = cursor.getColumnIndexOrThrow(COLUMN_DISPLAY_NAME)
            val createdAtIndex = cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)
            while (cursor.moveToNext()) {
                users += LocalUserSummary(
                    localId = cursor.getInt(idIndex),
                    displayName = cursor.getString(displayNameIndex),
                    createdAt = cursor.getLong(createdAtIndex)
                )
            }
        }
        return users
    }

    fun getUserSummary(localId: Int): LocalUserSummary? {
        return getUserSummary(readableDatabase, localId)
    }

    private fun getUserSummary(db: SQLiteDatabase, localId: Int): LocalUserSummary? {
        db.query(
            TABLE_TEST_USER,
            arrayOf(COLUMN_ID, COLUMN_DISPLAY_NAME, COLUMN_CREATED_AT),
            "$COLUMN_ID = ?",
            arrayOf(localId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return LocalUserSummary(
                localId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISPLAY_NAME)),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            )
        }
    }

    fun getFeatureRecordsForSearch(): List<LocalUserFeatureRecord> {
        val users = mutableListOf<LocalUserFeatureRecord>()
        readableDatabase.query(
            TABLE_TEST_USER,
            arrayOf(COLUMN_ID, COLUMN_FEATURE),
            null,
            null,
            null,
            null,
            COLUMN_ID
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val featureIndex = cursor.getColumnIndexOrThrow(COLUMN_FEATURE)
            while (cursor.moveToNext()) {
                users += LocalUserFeatureRecord(
                    localId = cursor.getInt(idIndex),
                    feature = cursor.getBlob(featureIndex)
                )
            }
        }
        return users
    }

    fun deleteUser(localId: Int): Int {
        return writableDatabase.delete(TABLE_TEST_USER, "$COLUMN_ID = ?", arrayOf(localId.toString()))
    }

    fun deleteAll(): Int {
        return writableDatabase.delete(TABLE_TEST_USER, null, null)
    }

    fun getNextDefaultDisplayName(): String {
        val prefix = "测试用户 "
        val usedNumbers = getAllUsersSummary().mapNotNull { summary ->
            if (summary.displayName.startsWith(prefix)) {
                summary.displayName.removePrefix(prefix).toIntOrNull()
            } else {
                null
            }
        }.toSet()
        var nextNumber = 1
        while (nextNumber in usedNumbers) {
            nextNumber++
        }
        return "$prefix$nextNumber"
    }

    companion object {
        private const val DB_NAME = "pos_face_local.db"
        private const val DB_VERSION = 2
        private const val TABLE_TEST_USER = "test_user"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TEST_USER_ID = "test_user_id"
        private const val COLUMN_USER_KEY = "user_key"
        private const val COLUMN_DISPLAY_NAME = "display_name"
        private const val COLUMN_FEATURE = "feature"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}

private data class TableSchema(
    val hasLegacyTestUserIdColumn: Boolean,
    val hasUserKeyColumn: Boolean,
    val hasDisplayNameColumn: Boolean
)

data class LocalUserSummary(
    val localId: Int,
    val displayName: String,
    val createdAt: Long
)

data class LocalUserFeatureRecord(
    val localId: Int,
    val feature: ByteArray
)