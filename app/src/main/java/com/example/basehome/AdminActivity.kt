package com.example.basehome

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminActivity : AppCompatActivity() {

    private lateinit var tvAdminStatsByCity: LinearLayout
    private lateinit var overviewUsersValue: TextView
    private lateinit var overviewAddressesValue: TextView
    private lateinit var overviewCommentsValue: TextView
    private lateinit var tvPhysicalStatLabel: TextView
    private lateinit var tvPhysicalStatValue: TextView
    private lateinit var tvPhysicalStatDetail: TextView
    private lateinit var tvBusinessStatLabel: TextView
    private lateinit var tvBusinessStatValue: TextView
    private lateinit var tvBusinessStatDetail: TextView
    private lateinit var tvUsersStatLabel: TextView
    private lateinit var tvUsersStatValue: TextView
    private lateinit var tvUsersStatDetail: TextView
    private val statsListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var statsLoadFailed = false
    private val registeredCities = mutableSetOf<String>()
    private lateinit var btnManageUsers: Button
    private lateinit var btnCreateAccount: Button
    private lateinit var btnModerateComments: Button
    private lateinit var btnAdminProfile: Button
    private lateinit var btnBackAdmin: ImageButton

    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        SwipeBackHelper.attach(this)

        database = FirebaseDatabase.getInstance()

        tvAdminStatsByCity = findViewById(R.id.tvAdminStatsByCity)
        overviewUsersValue = findViewById<View>(R.id.overviewUsersRow).findViewById(R.id.tvOverviewValue)
        overviewAddressesValue = findViewById<View>(R.id.overviewAddressesRow).findViewById(R.id.tvOverviewValue)
        overviewCommentsValue = findViewById<View>(R.id.overviewCommentsRow).findViewById(R.id.tvOverviewValue)
        findViewById<View>(R.id.overviewUsersRow).findViewById<TextView>(R.id.tvOverviewLabel).setText(R.string.admin_overview_users)
        findViewById<View>(R.id.overviewAddressesRow).findViewById<TextView>(R.id.tvOverviewLabel).setText(R.string.admin_overview_addresses)
        findViewById<View>(R.id.overviewCommentsRow).findViewById<TextView>(R.id.tvOverviewLabel).setText(R.string.admin_overview_comments)
        val physicalCard = findViewById<View>(R.id.statPhysical)
        tvPhysicalStatLabel = physicalCard.findViewById(R.id.tvAdminStatLabel)
        tvPhysicalStatValue = physicalCard.findViewById(R.id.tvAdminStatValue)
        tvPhysicalStatDetail = physicalCard.findViewById(R.id.tvAdminStatDetail)
        val businessCard = findViewById<View>(R.id.statBusiness)
        tvBusinessStatLabel = businessCard.findViewById(R.id.tvAdminStatLabel)
        tvBusinessStatValue = businessCard.findViewById(R.id.tvAdminStatValue)
        tvBusinessStatDetail = businessCard.findViewById(R.id.tvAdminStatDetail)
        val usersCard = findViewById<View>(R.id.statUsers)
        tvUsersStatLabel = usersCard.findViewById(R.id.tvAdminStatLabel)
        tvUsersStatValue = usersCard.findViewById(R.id.tvAdminStatValue)
        tvUsersStatDetail = usersCard.findViewById(R.id.tvAdminStatDetail)
        btnManageUsers = findViewById(R.id.btnManageUsers)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnModerateComments = findViewById(R.id.btnModerateComments)
        btnAdminProfile = findViewById(R.id.btnAdminProfile)
        btnBackAdmin = findViewById(R.id.btnBackAdmin)

        btnBackAdmin.setOnClickListener { finish() }
        btnAdminProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        btnManageUsers.setOnClickListener { startActivity(Intent(this, UsersActivity::class.java)) }
        btnCreateAccount.setOnClickListener { verifyAdminAndShowCreateAccountDialog() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnManageCities).setOnClickListener {
            startActivity(Intent(this, AdminCitiesActivity::class.java))
        }
        btnModerateComments.setOnClickListener { showModerationDialog() }

        loadStats()
    }

    private fun verifyAdminAndShowCreateAccountDialog() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, R.string.account_creation_sign_in_required, Toast.LENGTH_LONG).show()
            return
        }

        database.getReference("users").child(currentUser.uid).child("isAdmin")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.getValue(Boolean::class.java) == true) {
                    showCreateAccountDialog()
                } else {
                    Toast.makeText(this, R.string.account_creation_admin_only, Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, getString(R.string.account_creation_error, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
    }

    private fun showCreateAccountDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }

        val nameInput = EditText(this).apply {
            hint = getString(R.string.account_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isSingleLine = true
        }
        val emailInput = EditText(this).apply {
            hint = getString(R.string.account_email_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
        }
        val passwordInput = EditText(this).apply {
            hint = getString(R.string.account_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        container.addView(nameInput)
        container.addView(emailInput)
        container.addView(passwordInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.account_create_title)
            .setMessage(R.string.account_create_client_security_note)
            .setView(container)
            .setPositiveButton(R.string.account_create_button, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()

                when {
                    name.isEmpty() || email.isEmpty() || password.isEmpty() -> {
                        Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show()
                    }
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                        emailInput.error = getString(R.string.account_invalid_email)
                    }
                    password.length < 6 -> {
                        passwordInput.error = getString(R.string.account_password_too_short)
                    }
                    else -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        createFirebaseAccount(name, email, password, dialog)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createFirebaseAccount(name: String, email: String, password: String, dialog: AlertDialog) {
        val appName = "account_creator_${System.nanoTime()}"
        val secondaryApp = try {
            FirebaseApp.initializeApp(this, FirebaseApp.getInstance().options, appName)
        } catch (_: Exception) {
            null
        }

        if (secondaryApp == null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            Toast.makeText(this, R.string.account_creation_init_error, Toast.LENGTH_LONG).show()
            return
        }

        FirebaseAuth.getInstance(secondaryApp)
            .createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    secondaryApp.delete()
                    dialog.dismiss()
                    Toast.makeText(this, R.string.account_created_profile_error, Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val profile = mapOf(
                    "name" to name,
                    "email" to email,
                    "isAdmin" to false,
                    "isOnline" to false,
                    "lastSeen" to 0L,
                )
                FirebaseDatabase.getInstance(secondaryApp).getReference("users").child(uid).setValue(profile)
                    .addOnSuccessListener {
                        secondaryApp.delete()
                        dialog.dismiss()
                        Toast.makeText(this, R.string.account_created, Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { error ->
                        secondaryApp.delete()
                        dialog.dismiss()
                        Toast.makeText(
                            this,
                            getString(R.string.account_created_profile_error, error.localizedMessage ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                secondaryApp.delete()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                Toast.makeText(
                    this,
                    getString(R.string.account_creation_error, error.localizedMessage ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    private fun loadStats() {
        val usersRef = database.getReference("users")
        val addressesRef = database.getReference("addresses")
        val businessRef = database.getReference("business_addresses")
        val commentsRef = database.getReference("comments")
        val entrancesRef = database.getReference("entrances")

        val citiesRef = database.getReference("cities")
        val citiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                registeredCities.clear()
                snapshot.children.mapNotNullTo(registeredCities) { it.getValue(String::class.java)?.trim()?.takeIf(String::isNotEmpty) }
                updateCityStats()
            }

            override fun onCancelled(error: DatabaseError) {
                statsLoadFailed = true
                showStatsLoadError()
            }
        }
        statsListeners.add(citiesRef to citiesListener)
        citiesRef.addValueEventListener(citiesListener)

        listOf(usersRef, addressesRef, businessRef, commentsRef, entrancesRef).forEach { ref ->
            // These are the only nodes read by the overview; the Google/task database is never queried.
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    updateStats(ref.key.orEmpty(), snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    statsLoadFailed = true
                    showStatsLoadError()
                }
            }
            statsListeners.add(ref to listener)
            ref.addValueEventListener(listener)
        }
    }

    private fun updateStats(path: String, snapshot: DataSnapshot) {
        when (path) {
            "users" -> {
                val total = snapshot.childrenCount.toInt()
                val online = snapshot.children.count { it.child("isOnline").getValue(Boolean::class.java) == true }
                val admins = snapshot.children.count { it.child("isAdmin").getValue(Boolean::class.java) == true }
                overviewUsersValue.text = getString(R.string.admin_overview_users_value, total, online, admins)
                tvUsersStatLabel.text = getString(R.string.admin_stat_users)
                tvUsersStatValue.text = total.toString()
                tvUsersStatDetail.text = getString(R.string.admin_stat_users_detail, online, admins)
            }
            "addresses" -> {
                statsLoadFailed = false
                val counts = countByCity(snapshot)
                val total = snapshot.childrenCount.toInt()
                physicalTotal = total
                physicalAddressIds.clear()
                snapshot.children.mapNotNullTo(physicalAddressIds) { it.key }
                entranceTotal = physicalAddressIds.sumOf { entrancesByAddress[it] ?: 0 }
                tvPhysicalStatLabel.text = getString(R.string.admin_stat_physical)
                tvPhysicalStatValue.text = total.toString()
                physicalCountsByCity.clear()
                physicalCountsByCity.putAll(counts)
                updateAddressSummary()
                updateCityStats()
                renderCommentSummary()
            }
            "business_addresses" -> {
                val counts = countByCity(snapshot)
                val total = snapshot.childrenCount.toInt()
                businessTotal = total
                businessAddressIds.clear()
                snapshot.children.mapNotNullTo(businessAddressIds) { it.key }
                businessCountsByCity.clear()
                businessCountsByCity.putAll(counts)
                updateBusinessDetails(snapshot, total)
                updateAddressSummary()
                updateCityStats()
                renderCommentSummary()
            }
            "comments" -> updateCommentSummary(snapshot)
            "entrances" -> updateEntranceSummary(snapshot)
        }
    }

    private val physicalCountsByCity = linkedMapOf<String, Int>()
    private val businessCountsByCity = linkedMapOf<String, Int>()
    private var physicalTotal = 0
    private var businessTotal = 0
    private var physicalCommentTotal = 0
    private var businessCommentTotal = 0
    private var entranceTotal = 0
    private val entrancesByAddress = mutableMapOf<String, Int>()

    private fun countByCity(snapshot: DataSnapshot): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        for (item in snapshot.children) {
            val rawCity = item.child("city").getValue(String::class.java)?.trim().orEmpty()
            val city = rawCity.ifEmpty { "Город не указан" }
            counts[city] = (counts[city] ?: 0) + 1
        }
        return counts
    }

    private fun updateAddressSummary() {
        val total = physicalTotal + businessTotal
        overviewAddressesValue.text = getString(R.string.admin_overview_addresses_value, physicalTotal, businessTotal, total)
    }

    private fun updateBusinessDetails(snapshot: DataSnapshot, total: Int) {
        businessTotal = total
        currentBusinessRelatedCount = 0
        for (businessAddress in snapshot.children) {
            currentBusinessRelatedCount += businessAddress.child("sockets").childrenCount.toInt()
            currentBusinessRelatedCount += businessAddress.child("reserves").childrenCount.toInt()
        }
        tvBusinessStatLabel.text = getString(R.string.admin_stat_business)
        tvBusinessStatValue.text = total.toString()
        tvBusinessStatDetail.text = getString(
            R.string.admin_stat_business_detail,
            currentBusinessRelatedCount,
            businessCommentTotal,
        )
    }

    private fun updateEntranceSummary(snapshot: DataSnapshot) {
        entrancesByAddress.clear()
        for (addressEntrances in snapshot.children) {
            val addressId = addressEntrances.key ?: continue
            entrancesByAddress[addressId] = addressEntrances.childrenCount.toInt()
        }
        entranceTotal = entrancesByAddress.values.sum()
        updatePhysicalDetails()
        renderCommentSummary()
    }

    private fun updateCommentSummary(snapshot: DataSnapshot) {
        commentsByAddress.clear()
        for (addressComments in snapshot.children) {
            val addressId = addressComments.key ?: continue
            commentsByAddress[addressId] = addressComments.children.count { commentNode ->
                !commentNode.child("text").getValue(String::class.java).isNullOrBlank()
            }
        }
        renderCommentSummary()
        updatePhysicalDetails()
    }

    private val commentsByAddress = mutableMapOf<String, Int>()
    private var commentTotal = 0

    private fun renderCommentSummary() {
        physicalCommentTotal = physicalAddressIds.sumOf { commentsByAddress[it] ?: 0 }
        businessCommentTotal = businessAddressIds.sumOf { commentsByAddress[it] ?: 0 }
        commentTotal = commentsByAddress.values.sum()
        overviewCommentsValue.text = getString(R.string.admin_overview_comments_value, commentTotal, physicalCommentTotal, businessCommentTotal)
        tvBusinessStatDetail.text = getString(
            R.string.admin_stat_business_detail,
            currentBusinessRelatedCount,
            businessCommentTotal,
        )
    }

    private var currentBusinessRelatedCount = 0
    private val physicalAddressIds = mutableSetOf<String>()
    private val businessAddressIds = mutableSetOf<String>()

    private fun updatePhysicalDetails() {
        tvPhysicalStatDetail.text = getString(R.string.admin_stat_physical_detail, entranceTotal, physicalCommentTotal)
    }

    private fun updateCityStats() {
        val cityNames = (registeredCities.asSequence() + physicalCountsByCity.keys.asSequence() + businessCountsByCity.keys.asSequence()).distinct().sorted() .toList()
        tvAdminStatsByCity.removeAllViews()
        if (cityNames.isEmpty()) {
            TextView(this).apply {
                setText(R.string.admin_stats_city_empty)
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 14f
            }.also(tvAdminStatsByCity::addView)
            return
        }
        cityNames.forEachIndexed { index, city ->
            val row = layoutInflater.inflate(R.layout.item_admin_city_stat, tvAdminStatsByCity, false)
            row.findViewById<TextView>(R.id.tvCityStatName).text = city
            row.findViewById<TextView>(R.id.tvCityStatPhysical).text = getString(R.string.admin_city_stat_physical, physicalCountsByCity[city] ?: 0)
            row.findViewById<TextView>(R.id.tvCityStatBusiness).text = getString(R.string.admin_city_stat_business, businessCountsByCity[city] ?: 0)
            tvAdminStatsByCity.addView(row)
            if (index < cityNames.lastIndex) {
                val divider = View(this).apply {
                    setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * resources.displayMetrics.density).toInt())
                }
                tvAdminStatsByCity.addView(divider)
            }
        }
    }

    private fun showStatsLoadError() {
        if (statsLoadFailed) {
            tvAdminStatsByCity.removeAllViews()
            TextView(this).apply {
                setText(R.string.admin_stats_error)
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 14f
            }.also(tvAdminStatsByCity::addView)
        }
    }

    private fun showModerationDialog() {
        database.getReference("comments").addListenerForSingleValueEvent(
            object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                val allComments = mutableListOf<Pair<String, Comment>>() // Pair(fullPath, Comment)
                for (addrNode in snapshot.children) {
                    val addrId = addrNode.key ?: continue
                    for (commNode in addrNode.children) {
                        val commId = commNode.key ?: continue
                        val comm = commNode.getValue(Comment::class.java) ?: continue
                        allComments.add(Pair("$addrId/$commId", comm))
                    }
                }

                allComments.sortByDescending { (_, comment) -> comment.timestamp }

                if (allComments.isEmpty()) {
                    Toast.makeText(this@AdminActivity, "Комментариев пока нет", Toast.LENGTH_SHORT).show()
                    return
                }

                val view = LayoutInflater.from(this@AdminActivity).inflate(R.layout.dialog_moderate_comments, null)
                val rv = view.findViewById<RecyclerView>(R.id.rvModerationComments)
                rv.layoutManager = LinearLayoutManager(this@AdminActivity)

                var alertDialog: AlertDialog? = null
                val commentsList = allComments.asSequence().map { (_, comment) -> comment }.toMutableList()

                val deleteComment: (Comment) -> Unit = { comment ->
                        AlertDialog.Builder(this@AdminActivity)
                            .setTitle("Удаление комментария")
                            .setMessage("Удалить комментарий пользователя ${comment.authorEmail}?")
                            .setPositiveButton("Да") { _, _ ->
                                val path = allComments.find { (_, candidate) -> candidate.id == comment.id }?.first
                                if (path != null) {
                                    val parts = path.split("/")
                                    if (parts.size == 2) {
                                        database.getReference("comments").child(parts[0]).child(parts[1]).removeValue()
                                        Toast.makeText(this@AdminActivity, "Удалено", Toast.LENGTH_SHORT).show()
                                        alertDialog?.dismiss()
                                    }
                                }
                            }
                            .setNegativeButton("Нет", null)
                            .show()
                }
                val adapter = CommentAdapter(
                    comments = commentsList,
                    isAdmin = true,
                    onReactionClick = { _, _ -> },
                    onReplyClick = {},
                    onDeleteClick = deleteComment,
                    onEditClick = {},
                )

                rv.adapter = adapter

                val builder = AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Модерация всех комментариев")
                    .setView(view)
                    .setNegativeButton("Закрыть", null)

                alertDialog = builder.show()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminActivity, error.message, Toast.LENGTH_LONG).show()
            }
        })

    }

    override fun onDestroy() {
        statsListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        statsListeners.clear()
        super.onDestroy()
    }
}
