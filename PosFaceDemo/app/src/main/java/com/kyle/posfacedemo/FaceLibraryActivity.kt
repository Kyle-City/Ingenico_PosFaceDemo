package com.kyle.posfacedemo

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kyle.posfacedemo.face.baidu.BaiduFaceIdentificationService
import com.kyle.posfacedemo.face.baidu.LocalFaceRepository
import com.kyle.posfacedemo.face.baidu.LocalUserSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FaceLibraryActivity : AppCompatActivity() {
    private lateinit var userCountText: TextView
    private lateinit var emptyStateText: TextView
    private lateinit var userListContainer: LinearLayout
    private lateinit var deleteAllButton: Button
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_face_library)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.faceLibraryRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userCountText = findViewById(R.id.userCountText)
        emptyStateText = findViewById(R.id.emptyStateText)
        userListContainer = findViewById(R.id.userListContainer)
        deleteAllButton = findViewById(R.id.deleteAllButton)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        deleteAllButton.setOnClickListener { confirmDeleteAll() }
        renderUsers()
    }

    private fun renderUsers() {
        val repository = LocalFaceRepository(this)
        val users = try {
            repository.getAllUsersSummary()
        } finally {
            repository.close()
        }
        userCountText.text = getString(R.string.face_library_user_count, users.size)
        emptyStateText.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        deleteAllButton.isEnabled = users.isNotEmpty()
        userListContainer.removeAllViews()
        users.forEach { user ->
            userListContainer.addView(createUserRow(user))
        }
    }

    private fun createUserRow(user: LocalUserSummary): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 14, 0, 14)
        }
        val textGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textGroup.addView(TextView(this).apply {
            text = user.displayName
            textSize = 18f
            setTextColor(getColor(R.color.poc_text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textGroup.addView(TextView(this).apply {
            text = getString(R.string.face_library_registered_at, dateFormat.format(Date(user.createdAt)))
            textSize = 14f
            setTextColor(getColor(R.color.poc_text_secondary))
        })
        row.addView(textGroup)
        row.addView(Button(this).apply {
            text = getString(R.string.face_library_delete_user)
            minHeight = 48
            setOnClickListener { confirmDeleteUser(user) }
        })
        return row
    }

    private fun confirmDeleteUser(user: LocalUserSummary) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.face_library_delete_user_title, user.displayName))
            .setMessage(R.string.face_library_delete_user_message)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.delete_test_user_confirm) { _, _ ->
                deleteUser(user.localId)
            }
            .show()
    }

    private fun deleteUser(localId: Int) {
        val startTime = System.currentTimeMillis()
        val repository = LocalFaceRepository(this)
        try {
            repository.deleteUser(localId)
            val userCount = repository.getUserCount()
            BaiduFaceIdentificationService.reloadFromRepository(this)
            Log.i(TAG, "operation=deleteUser")
            Log.i(TAG, "state=SUCCESS")
            Log.i(TAG, "userCount=$userCount")
            Log.i(TAG, "durationMs=${System.currentTimeMillis() - startTime}")
        } catch (exception: Exception) {
            Log.i(TAG, "operation=deleteUser")
            Log.i(TAG, "state=FAILED")
            Log.i(TAG, "failureReason=EXCEPTION")
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
        } finally {
            repository.close()
        }
        renderUsers()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.face_library_delete_all_title)
            .setMessage(R.string.face_library_delete_all_message)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.delete_test_user_confirm) { _, _ ->
                deleteAllUsers()
            }
            .show()
    }

    private fun deleteAllUsers() {
        val startTime = System.currentTimeMillis()
        val repository = LocalFaceRepository(this)
        try {
            repository.deleteAll()
            BaiduFaceIdentificationService.clearSearchMemory()
            Log.i(TAG, "operation=deleteAll")
            Log.i(TAG, "state=SUCCESS")
            Log.i(TAG, "userCount=0")
            Log.i(TAG, "durationMs=${System.currentTimeMillis() - startTime}")
        } catch (exception: Exception) {
            Log.i(TAG, "operation=deleteAll")
            Log.i(TAG, "state=FAILED")
            Log.i(TAG, "failureReason=EXCEPTION")
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
        } finally {
            repository.close()
        }
        renderUsers()
    }

    companion object {
        private const val TAG = "POSFACE_FACE_LIBRARY"
    }
}