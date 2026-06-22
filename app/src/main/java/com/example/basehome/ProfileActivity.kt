package com.example.basehome

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var rvAddresses: RecyclerView
    private lateinit var rvComments: RecyclerView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    private val userAddresses = mutableListOf<Address>()
    private val userComments = mutableListOf<Comment>()
    
    private lateinit var addressAdapter: AddressAdapter
    private lateinit var commentAdapter: CommentAdapter
    
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        val user = auth.currentUser

        if (user == null) {
            finish()
            return
        }

        tvTitle = findViewById(R.id.tvProfileTitle)
        rvAddresses = findViewById(R.id.rvUserAddresses)
        rvComments = findViewById(R.id.rvUserComments)

        checkAdminStatus {
            setupUI()
            loadData()
        }
    }

    private fun checkAdminStatus(onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users").child(uid).child("isAdmin").get()
            .addOnSuccessListener {
                isAdmin = it.getValue(Boolean::class.java) ?: false
                onComplete()
            }.addOnFailureListener { onComplete() }
    }

    private fun setupUI() {
        if (isAdmin) {
            tvTitle.text = "АДМИН-ПАНЕЛЬ"
            findViewById<TextView>(R.id.tvSection1Title).text = "Все последние адреса:"
            findViewById<TextView>(R.id.tvSection2Title).text = "Все последние комментарии:"
        } else {
            tvTitle.text = "Личный кабинет"
        }

        // Setup Addresses
        val addressClickListener = AddressAdapter.OnAddressClickListener { address ->
            val intent = Intent(this@ProfileActivity, AddressDetailActivity::class.java)
            intent.putExtra("addressId", address.id)
            startActivity(intent)
        }

        val addressDeleteListener = AddressAdapter.OnDeleteClickListener { address ->
            confirmDeleteAddress(address)
        }

        addressAdapter = AddressAdapter(userAddresses, addressClickListener, addressDeleteListener)
        addressAdapter.setAdmin(isAdmin)
        
        rvAddresses.layoutManager = LinearLayoutManager(this)
        rvAddresses.adapter = addressAdapter

        // Setup Comments
        commentAdapter = CommentAdapter(userComments, isAdmin,
            { _, _ -> /* реакции не нужны */ },
            { _ -> /* ответы не нужны */ },
            { comment -> confirmDeleteComment(comment) },
            { comment -> showEditCommentDialog(comment) }
        )
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter
    }

    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return

        // Load Addresses
        val addrRef = database.getReference("addresses")
        val addrQuery = if (isAdmin) addrRef.limitToLast(20) else addrRef.orderByChild("userId").equalTo(uid)
        
        addrQuery.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userAddresses.clear()
                for (data in snapshot.children) {
                    val addr = data.getValue(Address::class.java)
                    if (addr != null) {
                        addr.id = data.key
                        userAddresses.add(addr)
                    }
                }
                userAddresses.reverse()
                addressAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Load Comments
        val commRef = database.getReference("comments")
        commRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userComments.clear()
                for (addrNode in snapshot.children) {
                    for (commentNode in addrNode.children) {
                        val comment = commentNode.getValue(Comment::class.java)
                        if (comment != null) {
                            // Путь к комментарию для удаления/правки
                            comment.id = "${addrNode.key}/${commentNode.key}"
                            if (isAdmin || comment.authorId == uid) {
                                userComments.add(comment)
                            }
                        }
                    }
                }
                userComments.sortByDescending { it.timestamp }
                if (userComments.size > 50) {
                    val limited = userComments.take(50)
                    userComments.clear()
                    userComments.addAll(limited)
                }
                commentAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun confirmDeleteAddress(address: Address) {
        val currentUid = auth.currentUser?.uid
        if (!isAdmin && address.userId != currentUid) return

        AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setMessage("Удалить адрес ${address.street} со всеми данными?")
            .setPositiveButton("Удалить") { _, _ ->
                val id = address.id ?: return@setPositiveButton
                database.getReference("addresses").child(id).removeValue()
                database.getReference("entrances").child(id).removeValue()
                database.getReference("comments").child(id).removeValue()
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditCommentDialog(comment: Comment) {
        val editText = EditText(this)
        editText.setText(comment.text)
        editText.setSelection(editText.text.length)
        
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this)
        container.addView(editText)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(padding, padding / 2, padding, 0)
        editText.layoutParams = lp

        AlertDialog.Builder(this)
            .setTitle("Редактировать комментарий")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    database.getReference("comments").child(comment.id).child("text").setValue(newText)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDeleteComment(comment: Comment) {
        val currentUid = auth.currentUser?.uid
        if (!isAdmin && comment.authorId != currentUid) return

        AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setMessage("Удалить этот комментарий?")
            .setPositiveButton("Удалить") { _, _ ->
                database.getReference("comments").child(comment.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
