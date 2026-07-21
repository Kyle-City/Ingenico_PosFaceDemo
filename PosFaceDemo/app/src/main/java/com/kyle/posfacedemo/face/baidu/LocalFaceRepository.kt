package com.kyle.posfacedemo.face.baidu

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalFaceRepository(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TEST_USER (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                test_user_id TEXT NOT NULL UNIQUE,
                feature BLOB NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEST_USER")
        onCreate(db)
    }

    fun saveOrReplaceTestUser(testUserId: String, feature: ByteArray): Boolean {
        val featureCopy = feature.copyOf()
        return try {
            writableDatabase.use { db ->
                val values = ContentValues().apply {
                    put(COLUMN_TEST_USER_ID, testUserId)
                    put(COLUMN_FEATURE, featureCopy)
                    put(COLUMN_CREATED_AT, System.currentTimeMillis())
                }
                db.replace(TABLE_TEST_USER, null, values) >= 0
            }
        } finally {
            featureCopy.fill(0)
        }
    }

    fun getUserCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TEST_USER", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun getAllTestUsers(): List<LocalTestUserFeature> {
        val users = mutableListOf<LocalTestUserFeature>()
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
                users += LocalTestUserFeature(
                    localId = cursor.getInt(idIndex),
                    feature = cursor.getBlob(featureIndex)
                )
            }
        }
        return users
    }

    fun deleteAll(): Int {
        return writableDatabase.delete(TABLE_TEST_USER, null, null)
    }

    companion object {
        const val TEST_USER_ID = "test-user-001"
        private const val DB_NAME = "pos_face_local.db"
        private const val DB_VERSION = 1
        private const val TABLE_TEST_USER = "test_user"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TEST_USER_ID = "test_user_id"
        private const val COLUMN_FEATURE = "feature"
        private const val COLUMN_CREATED_AT = "created_at"
    }
}

data class LocalTestUserFeature(
    val localId: Int,
    val feature: ByteArray
)