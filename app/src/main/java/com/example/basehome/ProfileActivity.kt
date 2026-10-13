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
    private lateinit var tvRankProgress: TextView
    private lateinit var tvProfileCity: TextView
    private lateinit var tvLoginStreak: TextView
    private lateinit var tvProfileEntranceCount: TextView
    private lateinit var rvRecentActivity: RecyclerView
    private lateinit var tvAddressesCount: TextView
    private lateinit var tvCommentsCount: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnLogout: ImageButton
    private lateinit var btnViewUsers: Button
    private lateinit var btnChangeCity: MaterialButton
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
    private lateinit var recentActivityAdapter: RecentActivityAdapter
    private var isAdmin = false
    private val userNamesMap = mutableMapOf<String, String>()
    private val adminUsersMap = mutableMapOf<String, Boolean>()
    private val addressesById = mutableMapOf<String, Address>()
    private val businessAddressesById = mutableMapOf<String, BusinessAddress>()
    private var currentUid: String = ""
    private var selectedCity: String = CityManager.ALL_CITIES
    private var currentAddressCount = 0
    private var currentEntranceCount = 0
    private var currentCommentCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        SwipeBackHelper.attach(this)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUid = auth.currentUser?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        btnBack = findViewById(R.id.btnBackProfile)
        btnLogout = findViewById(R.id.btnLogout)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRating = findViewById(R.id.tvUserRating)
        tvRankProgress = findViewById(R.id.tvRankProgress)
        tvProfileCity = findViewById(R.id.tvProfileCity)
        tvLoginStreak = findViewById(R.id.tvLoginStreak)
        tvProfileEntranceCount = findViewById(R.id.tvProfileEntranceCount)
        rvRecentActivity = findViewById(R.id.rvRecentActivity)
        tvAddressesCount = findViewById(R.id.tvAddressesCount)
        tvCommentsCount = findViewById(R.id.tvCommentsCount)
        btnViewUsers = findViewById(R.id.btnViewUsers)
        btnChangeCity = findViewById(R.id.btnChangeCity)
        rvAddresses = findViewById(R.id.rvUserAddresses)
        rvComments = findViewById(R.id.rvUserComments)
        rvGlobalRanking = findViewById(R.id.rvGlobalRanking)
        btnToggleAddresses = findViewById(R.id.btnToggleAddresses)
        llAddressesContainer = findViewById(R.id.llAddressesContainer)
        btnLoadMoreAddresses = findViewById(R.id.btnLoadMoreAddresses)
        btnToggleComments = findViewById(R.id.btnToggleComments)
        llCommentsContainer = findViewById(R.id.llCommentsContainer)
        btnLoadMoreComments = findViewById(R.id.btnLoadMoreComments)

        llAddressesContainer.isVisible = false
        llCommentsContainer.isVisible = false
        btnBack.setOnClickListener { finish() }
        btnLogout.setOnClickListener { showLogoutDialog() }
        tvUserName.setOnClickListener { showChangeNameDialog() }
        btnChangeCity.setOnClickListener { showCityPicker() }
        btnViewUsers.setOnClickListener { startActivity(Intent(this, UsersActivity::class.java)) }
        tvUserName.text = auth.currentUser?.email?.substringBefore("@") ?: getString(R.string.user_default_name)

        setupUI()
        listenToUserData()
        loadAllUserNames { loadAddressesAndRanking() }
    }

    private fun listenToUserData() {
        database.getReference("users").child(currentUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isAdmin = snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false
                val loginStreak = snapshot.child("loginStreakCount").getValue(Long::class.java) ?: 0L
                tvLoginStreak.text = getString(R.string.profile_login_streak, loginStreak)
                updateUserNameDisplay(snapshot.child("name").getValue(String::class.java))
                selectedCity = snapshot.child("selectedCity").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: CityManager.ALL_CITIES
                tvProfileCity.text = getString(R.string.profile_city_format, selectedCity)
                btnViewUsers.isVisible = isAdmin
                if (::addressAdapter.isInitialized) addressAdapter.setAdmin(isAdmin)
                if (::commentAdapter.isInitialized) commentAdapter.setAdmin(isAdmin)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUserNameDisplay(name: String?) {
        val baseName = name ?: auth.currentUser?.email?.substringBefore("@") ?: getString(R.string.user_default_name)
        tvUserName.text = RankHelper.formatNameWithRank(currentUid, baseName)
    }

    private fun loadAllUserNames(onComplete: () -> Unit) {
        database.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (u in snapshot.children) {
                    val name = u.child("name").getValue(String::class.java)
                    val email = u.child("email").getValue(String::class.java)
                    val uid = u.key ?: continue
                    userNamesMap[uid] = name?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@") ?: getString(R.string.anonymous)
                    adminUsersMap[uid] = u.child("isAdmin").getValue(Boolean::class.java) ?: false
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) { onComplete() }
        })
    }

    private fun setupUI() {
        addressAdapter = AddressAdapter(mutableListOf(), { address ->
            startActivity(Intent(this, AddressDetailActivity::class.java).putExtra("addressId", address.id))
        }, { address -> confirmDeleteAddress(address) })
        rvAddresses.layoutManager = LinearLayoutManager(this)
        rvAddresses.adapter = addressAdapter

        commentAdapter = CommentAdapter(mutableListOf(), isAdmin, { _, _ -> }, { _ -> },
            { comment -> confirmDeleteComment(comment) }, { comment -> showEditCommentDialog(comment) })
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter

        rankingAdapter = UserRankAdapter(globalRanks) { rank ->
            Toast.makeText(this, getString(R.string.user_rank_toast, rank.userName), Toast.LENGTH_SHORT).show()
        }
        rvGlobalRanking.layoutManager = LinearLayoutManager(this)
        rvGlobalRanking.adapter = rankingAdapter

        recentActivityAdapter = RecentActivityAdapter(emptyList()) { item ->
            val id = item.targetId ?: return@RecentActivityAdapter
            val activity = if (item.isBusiness) BusinessAddressDetailActivity::class.java else AddressDetailActivity::class.java
            startActivity(Intent(this, activity).putExtra("addressId", id))
        }
        rvRecentActivity.layoutManager = LinearLayoutManager(this)
        rvRecentActivity.adapter = recentActivityAdapter

        btnToggleAddresses.setOnClickListener { toggleSection(llAddressesContainer, btnToggleAddresses) }
        btnToggleComments.setOnClickListener { toggleSection(llCommentsContainer, btnToggleComments) }
        btnLoadMoreAddresses.setOnClickListener { addressesLimit += 10; updateVisibleAddresses() }
        btnLoadMoreComments.setOnClickListener { commentsLimit += 10; updateVisibleComments() }
    }

    private fun toggleSection(container: View, button: MaterialButton) {
        val visible = !container.isVisible
        container.isVisible = visible
        button.setIconResource(if (visible) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
    }

    private fun loadAddressesAndRanking() {
        database.getReference("addresses").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val counts = mutableMapOf<String, Int>()
                userAddressesFull.clear()
                addressesById.clear()
                val activities = mutableListOf<ActivityItem>()
                for (data in snapshot.children) {
                    val address = data.getValue(Address::class.java) ?: continue
                    address.id = data.key
                    address.id?.let { addressesById[it] = address }
                    val uid = address.userId.orEmpty()
                    if (uid.isNotEmpty()) {
                        counts[uid] = (counts[uid] ?: 0) + 1
                        if (uid == currentUid) {
                            userAddressesFull.add(address)
                            val place = formatAddress(address.city, address.street, address.house)
                            activities.add(ActivityItem(
                                ActivityType.ADDRESS,
                                getString(R.string.profile_activity_address, place),
                                0L,
                                address.id
                            ))
                        }
                    }
                }
                currentAddressCount = counts[currentUid] ?: 0
                val sorted = counts.mapNotNull { (uid, count) ->
                    if (adminUsersMap[uid] == true) null else UserRank(uid, userNamesMap[uid] ?: getString(R.string.anonymous), count)
                }.sortedByDescending { it.addressCount }
                UserRank.top3UserIds = sorted.take(3).map { it.userId }
                updateUserNameDisplay(userNamesMap[currentUid])
                userAddressesFull.reverse()
                updateVisibleAddresses()
                rankingAdapter.updateList(sorted.take(10))
                loadBusinessAddressesAndContinue(activities)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadBusinessAddressesAndContinue(activities: MutableList<ActivityItem>) {
        database.getReference("business_addresses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                businessAddressesById.clear()
                for (node in snapshot.children) {
                    val address = node.getValue(BusinessAddress::class.java) ?: continue
                    val id = node.key ?: continue
                    address.id = id
                    businessAddressesById[id] = address
                    if (address.userId == currentUid) {
                        val place = formatAddress(address.city, address.street, address.house)
                        activities.add(ActivityItem(
                            ActivityType.BUSINESS_ADDRESS,
                            getString(R.string.profile_activity_business_address, address.centerName.orEmpty()),
                            0L,
                            id,
                            isBusiness = true,
                            address = place
                        ))
                    }
                }
                loadEntrancesAndComments(activities)
            }
            override fun onCancelled(error: DatabaseError) {
                businessAddressesById.clear()
                loadEntrancesAndComments(activities)
            }
        })
    }

    private fun loadEntrancesAndComments(activities: MutableList<ActivityItem>) {
        database.getReference("entrances").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentEntranceCount = 0
                for (addressNode in snapshot.children) {
                    for (entranceNode in addressNode.children) {
                        val entrance = entranceNode.getValue(Entrance::class.java) ?: continue
                        if (entrance.lastEditorId == currentUid) {
                            currentEntranceCount++
                            val address = findAddress(addressNode.key)
                            val place = address?.let { formatAddress(it.city, it.street, it.house) }.orEmpty()
                            activities.add(ActivityItem(
                                ActivityType.ENTRANCE,
                                getString(R.string.profile_activity_entrance, entrance.number),
                                entrance.lastEditTimestamp,
                                addressNode.key,
                                address = place
                            ))
                        }
                    }
                }
                loadCommentsAndRender(activities)
            }
            override fun onCancelled(error: DatabaseError) { loadCommentsAndRender(activities) }
        })
    }

    private fun findAddress(id: String?): Address? = id?.let { addressesById[it] }

    private fun formatAddress(city: String?, street: String?, house: String?): String =
        listOfNotNull(city, street, house?.takeIf { it.isNotBlank() }?.let { "д. $it" })
            .filter { it.isNotBlank() }
            .joinToString(", ")

    private fun loadCommentsAndRender(activities: MutableList<ActivityItem>) {
        database.getReference("comments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userCommentsFull.clear()
                currentCommentCount = 0
                for (addressNode in snapshot.children) {
                    for (commentNode in addressNode.children) {
                        val comment = commentNode.getValue(Comment::class.java) ?: continue
                        comment.id = "${addressNode.key}/${commentNode.key}"
                        if (comment.authorId == currentUid) {
                            currentCommentCount++
                            val text = comment.text.orEmpty().replace('\n', ' ').take(90)
                            val addressId = addressNode.key
                            val physicalAddress = addressId?.let { addressesById[it] }
                            val businessAddress = addressId?.let { businessAddressesById[it] }
                            val place = physicalAddress?.let { formatAddress(it.city, it.street, it.house) }
                                ?: businessAddress?.let { formatAddress(it.city, it.street, it.house) }.orEmpty()
                            activities.add(ActivityItem(
                                ActivityType.COMMENT,
                                getString(R.string.profile_activity_comment, text),
                                comment.timestamp,
                                addressId,
                                isBusiness = businessAddress != null,
                                address = place
                            ))
                            userCommentsFull.add(comment)
                        } else if (isAdmin) {
                            userCommentsFull.add(comment)
                        }
                    }
                }
                userCommentsFull.sortByDescending { it.timestamp }
                updateVisibleComments()
                renderContribution()
                renderRecentActivity(activities)
            }
            override fun onCancelled(error: DatabaseError) {
                renderContribution()
                renderRecentActivity(activities)
            }
        })
    }

    private fun renderContribution() {
        val points = currentAddressCount + currentEntranceCount
        tvUserRating.text = getString(R.string.rank_label, ContributionRank.title(this, points))
        tvRankProgress.text = ContributionRank.progress(this, points)
        tvAddressesCount.text = currentAddressCount.toString()
        tvCommentsCount.text = currentCommentCount.toString()
        tvProfileEntranceCount.text = currentEntranceCount.toString()
    }

    private fun renderRecentActivity(activities: List<ActivityItem>) {
        val entries = activities.sortedByDescending { it.timestamp }.take(15)
        recentActivityAdapter.updateList(entries)
    }

    private fun updateVisibleAddresses() {
        addressAdapter.updateList(userAddressesFull.take(addressesLimit))
        btnLoadMoreAddresses.isVisible = userAddressesFull.size > addressesLimit
    }

    private fun updateVisibleComments() {
        commentAdapter.updateList(userCommentsFull.take(commentsLimit))
        btnLoadMoreComments.isVisible = userCommentsFull.size > commentsLimit
    }

    private fun showCityPicker() {
        CityManager.loadCitiesFromFirebase { cities ->
            runOnUiThread {
                val options = (cities + CityManager.ALL_CITIES).distinct()
                val dialog = AlertDialog.Builder(this).setTitle(R.string.choose_city).create()
                val list = RecyclerView(this).apply {
                    layoutManager = LinearLayoutManager(this@ProfileActivity)
                    adapter = CityPickerAdapter(options, selectedCity) { city ->
                        CityManager.setSelectedCity(this@ProfileActivity, city)
                        selectedCity = city
                        tvProfileCity.text = getString(R.string.profile_city_format, city)
                        dialog.dismiss()
                    }
                }
                dialog.setView(list)
                dialog.show()
            }
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this).setTitle(R.string.logout)
            .setMessage(R.string.profile_logout_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showChangeNameDialog() {
        val editText = EditText(this).apply {
            setText(tvUserName.text.toString().replace(" 👑", "").replace(" 🥈", "").replace(" 🥉", ""))
        }
        val container = FrameLayout(this).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 4, padding, 0)
            addView(editText)
        }
        AlertDialog.Builder(this).setTitle(R.string.change_name_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                editText.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    database.getReference("users").child(currentUid).child("name").setValue(it)
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirmDeleteAddress(address: Address) {
        AlertDialog.Builder(this).setTitle(android.R.string.dialog_alert_title)
            .setMessage(getString(R.string.confirm_delete_address, address.street))
            .setPositiveButton(android.R.string.ok) { _, _ -> address.id?.let { database.getReference("addresses").child(it).removeValue() } }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirmDeleteComment(comment: Comment) {
        AlertDialog.Builder(this).setTitle(android.R.string.dialog_alert_title).setMessage(R.string.confirm_delete_comment)
            .setPositiveButton(android.R.string.ok) { _, _ -> database.getReference("comments").child(comment.id).removeValue() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showEditCommentDialog(comment: Comment) {
        val input = EditText(this).apply { setText(comment.text) }
        AlertDialog.Builder(this).setTitle(R.string.edit_comment).setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                database.getReference("comments").child(comment.id).child("text").setValue(input.text.toString())
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
