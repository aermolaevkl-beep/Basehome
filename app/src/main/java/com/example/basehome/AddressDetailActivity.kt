package com.example.basehome

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

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
    private lateinit var btnBack: ImageButton

    private lateinit var databaseReference: DatabaseReference
    private lateinit var commentsReference: DatabaseReference
    private lateinit var entrancesReference: DatabaseReference
    private lateinit var usersReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    
    private var addressId: String? = null
    private var replyingToCommentId: String? = null
    
    private lateinit var commentAdapter: CommentAdapter
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
        btnBack = findViewById(R.id.btnBackAddressDetail)

        btnBack.setOnClickListener { finish() }

        btnEditAddress.visibility = View.GONE
        btnDeleteAddress.visibility = View.GONE

        setupAdapters()
        checkAdminStatus {
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
        val user = auth.currentUser ?: return
        usersReference.child(user.uid).get().addOnSuccessListener { snapshot ->
            isAdmin = snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false
            currentUserName = snapshot.child("name").getValue(String::class.java) ?: user.email ?: "Аноним"
            onComplete()
        }.addOnFailureListener { onComplete() }
    }

    private fun setupAdapters() {
        entranceAdapter = EntranceAdapter(mutableListOf()) { entrance -> showEntranceDialog(entrance) }
        rvEntrances.layoutManager = LinearLayoutManager(this)
        rvEntrances.adapter = entranceAdapter

        commentAdapter = CommentAdapter(mutableListOf(), isAdmin,
            onReactionClick = { comment, isPlus -> handleReaction(comment, isPlus) },
            onReplyClick = { comment -> startReply(comment) },
            onDeleteClick = { comment -> confirmDeleteComment(comment) },
            onEditClick = { comment -> showEditCommentDialog(comment) }
        )
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter
    }

    private fun loadAddress() {
        databaseReference.child(addressId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentAddress = snapshot.getValue(Address::class.java)
                currentAddress?.let { address ->
                    address.id = snapshot.key
                    tvCityDetail.text = address.city ?: "Могилев"
                    tvStreet.text = address.street
                    tvHouse.text = getString(R.string.house_label, address.house)
                    
                    val nameWithRank = RankHelper.formatNameWithRank(address.userId, address.userName)
                    tvAddedBy.text = TextUtils.concat("Создал: ", nameWithRank)
                    
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

    private fun loadEntrances() {
        entrancesReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Entrance>()
                for (data in snapshot.children) {
                    data.getValue(Entrance::class.java)?.let {
                        it.id = data.key ?: ""
                        list.add(it)
                    }
                }
                list.sortBy { it.number.toIntOrNull() ?: 0 }
                entranceAdapter.updateList(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadComments() {
        commentsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val all = mutableListOf<Comment>()
                for (data in snapshot.children) {
                    data.getValue(Comment::class.java)?.let {
                        it.id = data.key ?: ""
                        all.add(it)
                    }
                }
                val sortedList = rebuildCommentsTree(all)
                commentAdapter.updateList(sortedList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun rebuildCommentsTree(all: List<Comment>): List<Comment> {
        val result = mutableListOf<Comment>()
        val roots = all.filter { it.parentId.isNullOrEmpty() }.sortedBy { it.timestamp }
        val addedIds = mutableSetOf<String>()
        fun addRecursive(parent: Comment) {
            if (addedIds.contains(parent.id)) return
            result.add(parent)
            addedIds.add(parent.id)
            all.filter { it.parentId == parent.id }.sortedBy { it.timestamp }.forEach { addRecursive(it) }
        }
        roots.forEach { addRecursive(it) }
        return result
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

    private fun showEditCommentDialog(comment: Comment) {
        val et = EditText(this).apply { setText(comment.text) }
        AlertDialog.Builder(this).setTitle("Правка").setView(et).setPositiveButton("Ок") { _, _ ->
            commentsReference.child(comment.id).child("text").setValue(et.text.toString())
        }.setNegativeButton("Отмена", null).show()
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

    private fun capitalize(s: String): String = s.replaceFirstChar { it.titlecase() }

    private fun showEditAddressDialog() {
        val address = currentAddress ?: return
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_address, null)
        val spinnerCity = view.findViewById<Spinner>(R.id.spinnerCityEdit)
        val etStreet = view.findViewById<EditText>(R.id.etStreetEdit)
        val etHouse = view.findViewById<EditText>(R.id.etHouseEdit)

        val cities = arrayOf("Могилев", "Бобруйск")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCity.adapter = adapter
        
        val cityIndex = cities.indexOf(address.city ?: "Могилев")
        if (cityIndex >= 0) spinnerCity.setSelection(cityIndex)
        
        etStreet.setText(address.street)
        etHouse.setText(address.house)

        AlertDialog.Builder(this)
            .setTitle("Редактирование адреса")
            .setView(view)
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
                } else {
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDeleteAddress() {
        AlertDialog.Builder(this).setTitle("Удаление").setMessage("Удалить этот адрес?").setPositiveButton("Удалить") { _, _ ->
            databaseReference.child(addressId!!).removeValue()
            FirebaseDatabase.getInstance().reference.child("entrances").child(addressId!!).removeValue()
            FirebaseDatabase.getInstance().reference.child("comments").child(addressId!!).removeValue()
            finish()
        }.setNegativeButton("Отмена", null).show()
    }

    private fun showEntranceDialog(entrance: Entrance?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_entrance, null)
        val etNum = view.findViewById<EditText>(R.id.etEntranceNum)
        val etCode = view.findViewById<EditText>(R.id.etIntercomCode)
        val etCross = view.findViewById<EditText>(R.id.etCrossFloor)
        val etKey = view.findViewById<EditText>(R.id.etBasementKey)
        entrance?.let {
            etNum.setText(it.number); etCode.setText(it.intercomCode); etCross.setText(it.crossFloor); etKey.setText(it.basementKey)
        }
        AlertDialog.Builder(this).setTitle("Подъезд").setView(view).setPositiveButton("Ок") { _, _ ->
            val num = etNum.text.toString()
            if (num.isEmpty()) return@setPositiveButton
            val id = entrance?.id ?: entrancesReference.push().key ?: return@setPositiveButton
            val e = Entrance(id, num, etCode.text.toString(), etCross.text.toString(), etKey.text.toString(), auth.currentUser?.uid ?: "", currentUserName, System.currentTimeMillis())
            entrancesReference.child(id).setValue(e)
        }.setNegativeButton("Отмена", null).show()
    }
}
