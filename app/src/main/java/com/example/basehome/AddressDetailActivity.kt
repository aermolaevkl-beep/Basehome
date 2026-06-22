package com.example.basehome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale

class AddressDetailActivity : AppCompatActivity() {

    private lateinit var tvStreet: TextView
    private lateinit var tvHouse: TextView
    private lateinit var tvCityDetail: TextView
    private lateinit var tvAddedBy: TextView
    private lateinit var rvEntrances: RecyclerView
    private lateinit var rvComments: RecyclerView
    private lateinit var etNewComment: EditText
    private lateinit var btnAddComment: ImageButton
    private lateinit var btnAddEntrance: Button
    private lateinit var btnEditAddress: ImageButton
    private lateinit var btnDeleteAddress: ImageButton
    private lateinit var tvReplyTo: TextView

    private lateinit var databaseReference: DatabaseReference
    private lateinit var commentsReference: DatabaseReference
    private lateinit var entrancesReference: DatabaseReference
    private lateinit var usersReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    
    private var addressId: String? = null
    private var replyingToCommentId: String? = null
    
    private val commentsList = mutableListOf<Comment>()
    private lateinit var commentAdapter: CommentAdapter
    
    private val entrancesList = mutableListOf<Entrance>()
    private lateinit var entranceAdapter: EntranceAdapter
    
    private var isAdmin = false
    private var currentUserName = ""
    private var currentAddress: Address? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_detail)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().reference.child("addresses")
        usersReference = FirebaseDatabase.getInstance().reference.child("users")
        
        addressId = intent.getStringExtra("addressId")
        if (addressId == null) {
            Toast.makeText(this, "Ошибка: адрес не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        commentsReference = FirebaseDatabase.getInstance().reference.child("comments").child(addressId!!)
        entrancesReference = FirebaseDatabase.getInstance().reference.child("entrances").child(addressId!!)

        tvCityDetail = findViewById(R.id.tvCityDetail)
        tvStreet = findViewById(R.id.tvStreet)
        tvHouse = findViewById(R.id.tvHouse)
        tvAddedBy = findViewById(R.id.tvAddedBy)
        rvEntrances = findViewById(R.id.rvEntrances)
        rvComments = findViewById(R.id.rvComments)
        etNewComment = findViewById(R.id.etNewComment)
        btnAddComment = findViewById(R.id.btnAddComment)
        btnAddEntrance = findViewById(R.id.btnAddEntrance)
        btnEditAddress = findViewById(R.id.btnEditAddress)
        btnDeleteAddress = findViewById(R.id.btnDeleteAddress)
        tvReplyTo = findViewById(R.id.tvReplyTo)

        // Hide by default
        btnEditAddress.visibility = View.GONE
        btnDeleteAddress.visibility = View.GONE

        checkAdminStatus {
            setupAdapters()
            loadAddress()
            loadEntrances()
            loadComments()
        }

        btnAddEntrance.setOnClickListener { showEntranceDialog(null) }
        btnAddComment.setOnClickListener { addComment() }
        tvReplyTo.setOnClickListener { cancelReply() }
        btnEditAddress.setOnClickListener { showEditAddressDialog() }
        btnDeleteAddress.setOnClickListener { confirmDeleteAddress() }
    }

    private fun checkAdminStatus(onComplete: () -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete()
            return
        }
        usersReference.child(user.uid).get().addOnSuccessListener { snapshot ->
            isAdmin = snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false
            currentUserName = snapshot.child("name").getValue(String::class.java) ?: user.email ?: "Аноним"
            onComplete()
        }.addOnFailureListener { onComplete() }
    }

    private fun setupAdapters() {
        entranceAdapter = EntranceAdapter(entrancesList) { entrance ->
            showEntranceDialog(entrance)
        }
        rvEntrances.layoutManager = LinearLayoutManager(this)
        rvEntrances.adapter = entranceAdapter
        rvEntrances.isNestedScrollingEnabled = false

        commentAdapter = CommentAdapter(commentsList, isAdmin,
            onReactionClick = { comment, isPlus -> handleReaction(comment, isPlus) },
            onReplyClick = { comment -> startReply(comment) },
            onDeleteClick = { comment -> confirmDeleteComment(comment) },
            onEditClick = { comment -> showEditCommentDialog(comment) }
        )
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter
        rvComments.isNestedScrollingEnabled = false
    }

    private fun loadAddress() {
        databaseReference.child(addressId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAddress = snapshot.getValue(Address::class.java)
                currentAddress?.let { address ->
                    tvCityDetail.text = address.city ?: "Могилев"
                    tvStreet.text = address.street
                    tvHouse.text = getString(R.string.house_label, address.house)
                    tvAddedBy.text = getString(R.string.created_by_format, address.userName ?: getString(R.string.anonymous))
                    
                    val currentUid = auth.currentUser?.uid
                    if (isAdmin || (currentUid != null && currentUid == address.userId)) {
                        btnEditAddress.visibility = View.VISIBLE
                        btnDeleteAddress.visibility = View.VISIBLE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun capitalize(s: String): String {
        if (s.isEmpty()) return s
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun showEditAddressDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_add_address, null)
        val etStreet = dialogView.findViewById<EditText>(R.id.etStreet)
        val etHouse = dialogView.findViewById<EditText>(R.id.etHouse)
        val spinnerCity = dialogView.findViewById<Spinner>(R.id.spinnerCity)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        
        if (dialogView is ViewGroup && dialogView.isNotEmpty()) {
            val firstChild = dialogView.getChildAt(0)
            if (firstChild is TextView) firstChild.visibility = View.GONE
        }
        btnSave.visibility = View.GONE
        
        val cities = arrayOf("Могилев", "Бобруйск")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCity.adapter = adapter
        
        etStreet.setText(currentAddress?.street)
        etHouse.setText(currentAddress?.house)
        val cityIndex = if (currentAddress?.city == "Бобруйск") 1 else 0
        spinnerCity.setSelection(cityIndex)

        AlertDialog.Builder(this)
            .setTitle("Редактировать адрес")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newStreet = capitalize(etStreet.text.toString().trim())
                val newHouse = etHouse.text.toString().trim()
                val newCity = spinnerCity.selectedItem.toString()
                if (newStreet.isNotEmpty() && newHouse.isNotEmpty()) {
                    val updates = mapOf(
                        "street" to newStreet,
                        "house" to newHouse,
                        "city" to newCity
                    )
                    databaseReference.child(addressId!!).updateChildren(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Адрес обновлен", Toast.LENGTH_SHORT).show()
                        }
                }
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
                    commentsReference.child(comment.id).child("text").setValue(newText)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDeleteAddress() {
        AlertDialog.Builder(this)
            .setTitle("Удаление адреса")
            .setMessage("Вы уверены, что хотите полностью удалить этот адрес со всеми данными?")
            .setPositiveButton("Удалить") { _, _ ->
                val id = addressId!!
                databaseReference.child(id).removeValue()
                FirebaseDatabase.getInstance().reference.child("entrances").child(id).removeValue()
                FirebaseDatabase.getInstance().reference.child("comments").child(id).removeValue()
                
                Toast.makeText(this, "Адрес удален", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadEntrances() {
        entrancesReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                entrancesList.clear()
                for (data in snapshot.children) {
                    val entrance = data.getValue(Entrance::class.java)
                    if (entrance != null) {
                        entrance.id = data.key ?: ""
                        entrancesList.add(entrance)
                    }
                }
                entrancesList.sortBy { it.number.toIntOrNull() ?: 0 }
                entranceAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showEntranceDialog(entrance: Entrance?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_entrance, null)
        val etNum = dialogView.findViewById<EditText>(R.id.etEntranceNum)
        val etCode = dialogView.findViewById<EditText>(R.id.etIntercomCode)
        val etCross = dialogView.findViewById<EditText>(R.id.etCrossFloor)
        val etKey = dialogView.findViewById<EditText>(R.id.etBasementKey)

        if (entrance != null) {
            etNum.setText(entrance.number)
            etCode.setText(entrance.intercomCode)
            etCross.setText(entrance.crossFloor)
            etKey.setText(entrance.basementKey)
        }

        AlertDialog.Builder(this)
            .setTitle(if (entrance == null) "Добавить подъезд" else "Редактировать подъезд")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val num = etNum.text.toString().trim()
                if (num.isEmpty()) return@setPositiveButton
                
                val id = entrance?.id ?: entrancesReference.push().key ?: return@setPositiveButton
                val newEntrance = Entrance(
                    id, num, 
                    etCode.text.toString().trim(),
                    etCross.text.toString().trim(),
                    etKey.text.toString().trim(),
                    auth.currentUser?.uid ?: "",
                    currentUserName,
                    System.currentTimeMillis()
                )
                entrancesReference.child(id).setValue(newEntrance)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadComments() {
        commentsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allComments = mutableListOf<Comment>()
                for (data in snapshot.children) {
                    val comment = data.getValue(Comment::class.java)
                    if (comment != null) {
                        comment.id = data.key ?: ""
                        allComments.add(comment)
                    }
                }
                rebuildCommentsTree(allComments)
                commentAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun rebuildCommentsTree(all: List<Comment>) {
        commentsList.clear()
        val roots = all.filter { it.parentId.isNullOrEmpty() }.sortedBy { it.timestamp }
        val addedIds = mutableSetOf<String>()
        fun addRecursive(parent: Comment) {
            if (addedIds.contains(parent.id)) return
            commentsList.add(parent)
            addedIds.add(parent.id)
            all.filter { it.parentId == parent.id }.sortedBy { it.timestamp }.forEach { addRecursive(it) }
        }
        roots.forEach { addRecursive(it) }
        all.filter { !addedIds.contains(it.id) }.sortedBy { it.timestamp }.forEach { commentsList.add(it) }
    }

    private fun addComment() {
        val text = etNewComment.text.toString().trim()
        if (text.isEmpty()) return
        val user = auth.currentUser ?: return
        val commentId = commentsReference.push().key ?: return
        val comment = Comment(commentId, text, user.uid, user.email ?: "Anonymous", System.currentTimeMillis(), replyingToCommentId)
        commentsReference.child(commentId).setValue(comment).addOnSuccessListener {
            etNewComment.text.clear()
            cancelReply()
        }
    }

    private fun startReply(comment: Comment) {
        replyingToCommentId = comment.id
        tvReplyTo.text = "Ответ пользователю (отменить)"
        tvReplyTo.visibility = View.VISIBLE
        etNewComment.requestFocus()
    }

    private fun cancelReply() {
        replyingToCommentId = null
        tvReplyTo.visibility = View.GONE
    }

    private fun confirmDeleteComment(comment: Comment) {
        AlertDialog.Builder(this).setTitle("Удаление").setMessage("Удалить комментарий?").setPositiveButton("Да") { _, _ ->
            commentsReference.child(comment.id).removeValue()
        }.setNegativeButton("Нет", null).show()
    }

    private fun handleReaction(comment: Comment, isPlus: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val currentVote = comment.votedUsers[userId] ?: 0
        val newVote = if (isPlus) 1 else -1
        if (currentVote == newVote) {
            comment.votedUsers.remove(userId)
            if (isPlus) comment.plusCount-- else comment.minusCount--
        } else {
            if (currentVote != 0) { if (currentVote == 1) comment.plusCount-- else comment.minusCount-- }
            comment.votedUsers[userId] = newVote
            if (isPlus) comment.plusCount++ else comment.minusCount++
        }
        commentsReference.child(comment.id).setValue(comment)
    }
}
