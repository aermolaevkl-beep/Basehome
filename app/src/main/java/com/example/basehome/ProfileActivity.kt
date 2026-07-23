package com.example.basehome

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRating: TextView
    private lateinit var tvAddressesCount: TextView
    private lateinit var tvCommentsCount: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnLogout: ImageButton
    private lateinit var btnViewUsers: Button
    
    private lateinit var rvAddresses: RecyclerView
    private lateinit var rvComments: RecyclerView
    private lateinit var rvGlobalRanking: RecyclerView
    
    private lateinit var btnToggleAddresses: MaterialButton
    private lateinit var llAddressesContainer: LinearLayout
    private lateinit var btnLoadMoreAddresses: Button
    
    private lateinit var btnToggleComments: MaterialButton
    private lateinit var llCommentsContainer: LinearLayout
    private lateinit var btnLoadMoreComments: Button
    
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    private val userAddressesFull = mutableListOf<Address>()
    private val userCommentsFull = mutableListOf<Comment>()
    private val globalRanks = mutableListOf<UserRank>()
    
    private var addressesLimit = 10
    private var commentsLimit = 10
    
    private lateinit var addressAdapter: AddressAdapter
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var rankingAdapter: UserRankAdapter
    
    private var isAdmin = false
    private val userNamesMap = mutableMapOf<String, String>()
    private var currentUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUid = auth.currentUser?.uid ?: run { 
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return 
        }

        // Инициализация View
        btnBack = findViewById(R.id.btnBackProfile)
        btnLogout = findViewById(R.id.btnLogout)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRating = findViewById(R.id.tvUserRating)
        tvAddressesCount = findViewById(R.id.tvAddressesCount)
        tvCommentsCount = findViewById(R.id.tvCommentsCount)
        btnViewUsers = findViewById(R.id.btnViewUsers)
        
        rvAddresses = findViewById(R.id.rvUserAddresses)
        rvComments = findViewById(R.id.rvUserComments)
        rvGlobalRanking = findViewById(R.id.rvGlobalRanking)
        
        btnToggleAddresses = findViewById(R.id.btnToggleAddresses)
        llAddressesContainer = findViewById(R.id.llAddressesContainer)
        btnLoadMoreAddresses = findViewById(R.id.btnLoadMoreAddresses)
        
        btnToggleComments = findViewById(R.id.btnToggleComments)
        llCommentsContainer = findViewById(R.id.llCommentsContainer)
        btnLoadMoreComments = findViewById(R.id.btnLoadMoreComments)

        // Скрываем списки по умолчанию
        llAddressesContainer.isVisible = false
        llCommentsContainer.isVisible = false

        btnBack.setOnClickListener { finish() }
        btnLogout.setOnClickListener { showLogoutDialog() }
        tvUserName.setOnClickListener { showChangeNameDialog() }
        btnViewUsers.setOnClickListener { startActivity(Intent(this, UsersActivity::class.java)) }

        setupUI()
        
        // Показываем временное имя
        tvUserName.text = auth.currentUser?.email?.substringBefore("@") ?: "Пользователь"

        listenToUserData()
        loadAllUserNames {
            loadAddressesAndRanking()
        }
    }

    private fun listenToUserData() {
        database.getReference("users").child(currentUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isAdmin = snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false
                val name = snapshot.child("name").getValue(String::class.java)
                
                updateUserNameDisplay(name)
                btnViewUsers.isVisible = isAdmin
                
                if (::addressAdapter.isInitialized) addressAdapter.setAdmin(isAdmin)
                if (::commentAdapter.isInitialized) commentAdapter.setAdmin(isAdmin)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUserNameDisplay(name: String?) {
        val baseName = name ?: auth.currentUser?.email?.substringBefore("@") ?: "Пользователь"
        tvUserName.text = RankHelper.formatNameWithRank(currentUid, baseName)
    }

    private fun loadAllUserNames(onComplete: () -> Unit) {
        database.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (u in snapshot.children) {
                    val name = u.child("name").getValue(String::class.java)
                    val email = u.child("email").getValue(String::class.java)
                    val uid = u.key ?: continue
                    userNamesMap[uid] = if (!name.isNullOrEmpty()) name else email?.substringBefore("@") ?: "Аноним"
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) { onComplete() }
        })
    }

    private fun setupUI() {
        addressAdapter = AddressAdapter(mutableListOf(), { addr ->
            val intent = Intent(this, AddressDetailActivity::class.java).apply { putExtra("addressId", addr.id) }
            startActivity(intent)
        }, { addr -> confirmDeleteAddress(addr) })
        rvAddresses.layoutManager = LinearLayoutManager(this)
        rvAddresses.adapter = addressAdapter

        commentAdapter = CommentAdapter(mutableListOf(), isAdmin, { _, _ -> }, { _ -> },
            { c -> confirmDeleteComment(c) }, { c -> showEditCommentDialog(c) })
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter

        rankingAdapter = UserRankAdapter(globalRanks) { userRank ->
            Toast.makeText(this, "Пользователь: ${userRank.userName}", Toast.LENGTH_SHORT).show()
        }
        rvGlobalRanking.layoutManager = LinearLayoutManager(this)
        rvGlobalRanking.adapter = rankingAdapter
        
        btnToggleAddresses.setOnClickListener { toggleSection(llAddressesContainer, btnToggleAddresses) }
        btnToggleComments.setOnClickListener { toggleSection(llCommentsContainer, btnToggleComments) }
        
        btnLoadMoreAddresses.setOnClickListener {
            addressesLimit += 10
            updateVisibleAddresses()
        }
        btnLoadMoreComments.setOnClickListener {
            commentsLimit += 10
            updateVisibleComments()
        }
    }

    private fun toggleSection(container: View, button: MaterialButton) {
        val isNowVisible = !container.isVisible
        container.isVisible = isNowVisible
        button.setIconResource(if (isNowVisible) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
    }

    private fun loadAddressesAndRanking() {
        database.getReference("addresses").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val countsMap = mutableMapOf<String, Int>()
                userAddressesFull.clear()
                
                for (data in snapshot.children) {
                    val addr = data.getValue(Address::class.java) ?: continue
                    addr.id = data.key
                    val creatorId = addr.userId ?: ""
                    if (creatorId.isNotEmpty()) {
                        countsMap[creatorId] = (countsMap[creatorId] ?: 0) + 1
                        if (isAdmin || creatorId == currentUid) userAddressesFull.add(addr)
                    }
                }
                
                val sortedRanks = countsMap.map { (uid, count) ->
                    UserRank(uid, userNamesMap[uid] ?: "Аноним", count)
                }.sortedByDescending { it.addressCount }
                
                UserRank.top3UserIds = sortedRanks.take(3).map { it.userId }
                
                // Обновляем имя текущего пользователя с учетом рейтинга
                updateUserNameDisplay(userNamesMap[currentUid])
                
                userAddressesFull.reverse()
                updateVisibleAddresses()
                rankingAdapter.updateList(sortedRanks.take(10))
                loadComments(countsMap[currentUid] ?: 0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateVisibleAddresses() {
        val visibleList = userAddressesFull.take(addressesLimit)
        addressAdapter.updateList(visibleList)
        btnLoadMoreAddresses.isVisible = userAddressesFull.size > addressesLimit
    }

    private fun loadComments(addrCount: Int) {
        database.getReference("comments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userCommentsFull.clear()
                var userCommCount = 0
                for (addrNode in snapshot.children) {
                    for (commentNode in addrNode.children) {
                        val comment = commentNode.getValue(Comment::class.java) ?: continue
                        comment.id = "${addrNode.key}/${commentNode.key}"
                        if (comment.authorId == currentUid) userCommCount++
                        if (isAdmin || comment.authorId == currentUid) userCommentsFull.add(comment)
                    }
                }
                userCommentsFull.sortByDescending { it.timestamp }
                updateVisibleComments()

                val rankRes = when {
                    addrCount > 50 -> R.string.rank_legend
                    addrCount > 20 -> R.string.rank_master
                    addrCount > 10 -> R.string.rank_activist
                    addrCount > 5 -> R.string.rank_advanced
                    else -> R.string.rank_newbie
                }
                tvUserRating.text = getString(R.string.rank_label, getString(rankRes))
                tvAddressesCount.text = addrCount.toString()
                tvCommentsCount.text = userCommCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun updateVisibleComments() {
        val visibleList = userCommentsFull.take(commentsLimit)
        commentAdapter.updateList(visibleList)
        btnLoadMoreComments.isVisible = userCommentsFull.size > commentsLimit
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выход")
            .setMessage("Вы действительно хотите выйти из профиля?")
            .setPositiveButton("Да") { _, _ ->
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun showChangeNameDialog() {
        val editText = EditText(this).apply { 
            val cleanName = tvUserName.text.toString()
                .replace(" 👑", "").replace(" 🥈", "").replace(" 🥉", "")
            setText(cleanName)
        }
        val container = FrameLayout(this).apply {
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p/4, p, 0)
            addView(editText)
        }
        AlertDialog.Builder(this).setTitle("Ваше имя").setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    database.getReference("users").child(currentUid).child("name").setValue(newName)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun confirmDeleteAddress(address: Address) {
        AlertDialog.Builder(this).setTitle(android.R.string.dialog_alert_title)
            .setMessage(getString(R.string.confirm_delete_address, address.street))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                database.getReference("addresses").child(address.id!!).removeValue()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirmDeleteComment(comment: Comment) {
        AlertDialog.Builder(this).setTitle(android.R.string.dialog_alert_title).setMessage(R.string.confirm_delete_comment)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                database.getReference("comments").child(comment.id).removeValue()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showEditCommentDialog(comment: Comment) {
        val et = EditText(this).apply { setText(comment.text) }
        AlertDialog.Builder(this).setTitle(R.string.edit_comment).setView(et)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                database.getReference("comments").child(comment.id).child("text").setValue(et.text.toString())
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
