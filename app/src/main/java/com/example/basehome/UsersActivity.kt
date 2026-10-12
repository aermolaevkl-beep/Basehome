package com.example.basehome

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class UsersActivity : AppCompatActivity() {

    private lateinit var rvUsers: RecyclerView
    private lateinit var adapter: UsersAdapter
    private lateinit var database: FirebaseDatabase
    
    private val usersList = mutableListOf<UserDetail>()
    private val addressCounts = mutableMapOf<String, Int>()
    private val commentCounts = mutableMapOf<String, Int>()
    private val userListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val newList = mutableListOf<UserDetail>()
            for (u in snapshot.children) {
                val uid = u.key ?: continue
                val name = u.child("name").getValue(String::class.java) ?: getString(R.string.anonymous)
                val email = u.child("email").getValue(String::class.java) ?: ""
                val isOnline = u.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = u.child("lastSeen").getValue(Long::class.java) ?: 0L
                val isAdmin = u.child("isAdmin").getValue(Boolean::class.java) ?: false
                val selectedCity = u.child("selectedCity").getValue(String::class.java) ?: ""
                val appVersion = u.child("appVersion").getValue(String::class.java) ?: ""

                newList.add(UserDetail(
                    uid = uid,
                    name = name,
                    email = email,
                    addressCount = addressCounts[uid] ?: 0,
                    commentCount = commentCounts[uid] ?: 0,
                    isOnline = isOnline,
                    lastSeen = lastSeen,
                    isAdmin = isAdmin,
                    selectedCity = selectedCity,
                    appVersion = appVersion
                ))
            }
            newList.sortWith(compareByDescending<UserDetail> { it.isOnline }.thenByDescending { it.addressCount })
            adapter.updateList(newList)
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(this@UsersActivity, error.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

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
                        .addOnSuccessListener {
                            Toast.makeText(this, "Права изменены", Toast.LENGTH_SHORT).show()
                        }
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
                for (item in snapshot.children) {
                    val uid = item.child("userId").getValue(String::class.java) ?: ""
                    if (uid.isNotEmpty()) {
                        addressCounts[uid] = (addressCounts[uid] ?: 0) + 1
                    }
                }
                
                database.getReference("comments").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(commSnapshot: DataSnapshot) {
                        for (addrNode in commSnapshot.children) {
                            for (commentNode in addrNode.children) {
                                val uid = commentNode.child("authorId").getValue(String::class.java) ?: ""
                                if (uid.isNotEmpty()) {
                                    commentCounts[uid] = (commentCounts[uid] ?: 0) + 1
                                }
                            }
                        }
                        listenToUsers()
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
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
                } else {
                    showProfileEditor(user)
                }
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
                val updates = mapOf<String, Any>(
                    "name" to name,
                    "selectedCity" to cityInput.text.toString().trim()
                )
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
