package com.example.basehome

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class UsersActivity : AppCompatActivity() {

    private lateinit var rvUsers: RecyclerView
    private lateinit var adapter: UsersAdapter
    private lateinit var database: FirebaseDatabase
    private val usersList = mutableListOf<UserDetail>()
    private val addressCounts = mutableMapOf<String, Int>()
    private val entranceCounts = mutableMapOf<String, Int>()
    private val commentCounts = mutableMapOf<String, Int>()

    private val userListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val newList = snapshot.children.mapNotNull { u ->
                val uid = u.key ?: return@mapNotNull null
                UserDetail(
                    uid = uid,
                    name = u.child("name").getValue(String::class.java) ?: getString(R.string.anonymous),
                    email = u.child("email").getValue(String::class.java) ?: "",
                    addressCount = addressCounts[uid] ?: 0,
                    entranceCount = entranceCounts[uid] ?: 0,
                    commentCount = commentCounts[uid] ?: 0,
                    isOnline = u.child("isOnline").getValue(Boolean::class.java) ?: false,
                    lastSeen = u.child("lastSeen").getValue(Long::class.java) ?: 0L,
                    isAdmin = u.child("isAdmin").getValue(Boolean::class.java) ?: false,
                    selectedCity = u.child("selectedCity").getValue(String::class.java) ?: "",
                    appVersion = u.child("appVersion").getValue(String::class.java) ?: ""
                )
            }.sortedWith(compareByDescending<UserDetail> { it.isOnline }
                .thenByDescending { it.addressCount + it.entranceCount })
            adapter.updateList(newList)
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(this@UsersActivity, error.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)
        SwipeBackHelper.attach(this)
        database = FirebaseDatabase.getInstance()
        rvUsers = findViewById(R.id.rvUsersList)
        findViewById<ImageButton>(R.id.btnBackUsers).setOnClickListener { finish() }
        rvUsers.layoutManager = LinearLayoutManager(this)
        adapter = UsersAdapter(usersList, { user, newAdminState ->
            val actionText = if (newAdminState) "Назначить ${user.name} администратором?" else "Снять права администратора у ${user.name}?"
            AlertDialog.Builder(this)
                .setTitle("Изменение прав")
                .setMessage(actionText)
                .setPositiveButton("Да") { _, _ ->
                    database.getReference("users").child(user.uid).child("isAdmin").setValue(newAdminState)
                        .addOnSuccessListener { Toast.makeText(this, "Права изменены", Toast.LENGTH_SHORT).show() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }, { user -> showEditProfileDialog(user) })
        rvUsers.adapter = adapter
        loadData()
    }

    private fun loadData() {
        database.getReference("addresses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                addressCounts.clear()
                for (item in snapshot.children) {
                    val uid = item.child("userId").getValue(String::class.java).orEmpty()
                    if (uid.isNotEmpty()) addressCounts[uid] = (addressCounts[uid] ?: 0) + 1
                }
                loadEntranceCounts()
            }
            override fun onCancelled(error: DatabaseError) { showMessage(error.message) }
        })
    }

    private fun loadEntranceCounts() {
        database.getReference("entrances").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                entranceCounts.clear()
                for (addressNode in snapshot.children) {
                    for (entranceNode in addressNode.children) {
                        val uid = entranceNode.child("lastEditorId").getValue(String::class.java).orEmpty()
                        if (uid.isNotEmpty()) entranceCounts[uid] = (entranceCounts[uid] ?: 0) + 1
                    }
                }
                loadCommentCounts()
            }
            override fun onCancelled(error: DatabaseError) { showMessage(error.message) }
        })
    }

    private fun loadCommentCounts() {
        database.getReference("comments").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commentCounts.clear()
                for (addressNode in snapshot.children) {
                    for (commentNode in addressNode.children) {
                        val uid = commentNode.child("authorId").getValue(String::class.java).orEmpty()
                        if (uid.isNotEmpty()) commentCounts[uid] = (commentCounts[uid] ?: 0) + 1
                    }
                }
                listenToUsers()
            }
            override fun onCancelled(error: DatabaseError) { showMessage(error.message) }
        })
    }

    private fun listenToUsers() {
        database.getReference("users").addValueEventListener(userListener)
    }

    private fun showEditProfileDialog(user: UserDetail) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showMessage(getString(R.string.account_creation_sign_in_required))
            return
        }
        database.getReference("users").child(currentUser.uid).child("isAdmin").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.getValue(Boolean::class.java) != true) {
                    showMessage(getString(R.string.account_creation_admin_only))
                } else showProfileEditor(user)
            }
            .addOnFailureListener { error -> showMessage(error.localizedMessage ?: "") }
    }

    private fun showProfileEditor(user: UserDetail) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.account_name_hint)
            setText(user.name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        val cityInput = EditText(this).apply {
            hint = getString(R.string.edit_user_city_optional)
            setText(user.selectedCity)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        container.addView(nameInput)
        container.addView(cityInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_user_profile_title, user.name))
            .setMessage(R.string.edit_user_profile_fields_note)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    nameInput.error = getString(R.string.fill_fields)
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val updates = mapOf<String, Any>("name" to name, "selectedCity" to cityInput.text.toString().trim())
                database.getReference("users").child(user.uid).updateChildren(updates)
                    .addOnSuccessListener {
                        if (dialog.isShowing) dialog.dismiss()
                        showMessage(getString(R.string.user_profile_updated))
                    }
                    .addOnFailureListener { error ->
                        if (dialog.isShowing) dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        showMessage(getString(R.string.edit_user_profile_error, error.localizedMessage ?: ""))
                    }
            }
        }
        dialog.show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        if (::database.isInitialized) database.getReference("users").removeEventListener(userListener)
        super.onDestroy()
    }
}
