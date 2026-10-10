package com.example.basehome

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminActivity : AppCompatActivity() {

    private lateinit var tvStatUsers: TextView
    private lateinit var tvStatAddresses: TextView
    private lateinit var tvStatComments: TextView
    private lateinit var etNewCity: EditText
    private lateinit var btnAddCity: Button
    private lateinit var rvAdminCities: RecyclerView
    private lateinit var btnManageUsers: Button
    private lateinit var btnModerateComments: Button
    private lateinit var btnAdminProfile: Button
    private lateinit var btnBackAdmin: ImageButton

    private lateinit var database: FirebaseDatabase
    private lateinit var citiesAdapter: AdminCitiesAdapter
    private val citiesList = mutableListOf<String>()
    private val cityKeysMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        database = FirebaseDatabase.getInstance()

        tvStatUsers = findViewById(R.id.tvStatUsers)
        tvStatAddresses = findViewById(R.id.tvStatAddresses)
        tvStatComments = findViewById(R.id.tvStatComments)
        etNewCity = findViewById(R.id.etNewCity)
        btnAddCity = findViewById(R.id.btnAddCity)
        rvAdminCities = findViewById(R.id.rvAdminCities)
        btnManageUsers = findViewById(R.id.btnManageUsers)
        btnModerateComments = findViewById(R.id.btnModerateComments)
        btnAdminProfile = findViewById(R.id.btnAdminProfile)
        btnBackAdmin = findViewById(R.id.btnBackAdmin)

        btnBackAdmin.setOnClickListener { finish() }
        btnAdminProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        btnManageUsers.setOnClickListener { startActivity(Intent(this, UsersActivity::class.java)) }
        btnAddCity.setOnClickListener { addNewCity() }
        btnModerateComments.setOnClickListener { showModerationDialog() }

        setupCitiesAdapter()
        loadStats()
        loadCities()
    }

    private fun setupCitiesAdapter() {
        citiesAdapter = AdminCitiesAdapter(citiesList) { cityToDelete ->
            confirmDeleteCity(cityToDelete)
        }
        rvAdminCities.layoutManager = LinearLayoutManager(this)
        rvAdminCities.adapter = citiesAdapter
    }

    private fun loadStats() {
        // Статистика пользователей
        database.getReference("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val total = snapshot.childrenCount
                var online = 0
                for (u in snapshot.children) {
                    val isOnline = u.child("isOnline").getValue(Boolean::class.java) ?: false
                    if (isOnline) online++
                }
                tvStatUsers.text = "Пользователи: всего $total (онлайн: $online)"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Статистика адресов (физических и юридических)
        database.getReference("addresses").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(physSnapshot: DataSnapshot) {
                val physCount = physSnapshot.childrenCount
                database.getReference("business_addresses").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(bizSnapshot: DataSnapshot) {
                        val bizCount = bizSnapshot.childrenCount
                        tvStatAddresses.text = "Адреса: физ. $physCount, юр. $bizCount (всего: ${physCount + bizCount})"
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Статистика комментариев
        database.getReference("comments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalComm = 0
                for (addrNode in snapshot.children) {
                    totalComm += addrNode.childrenCount.toInt()
                }
                tvStatComments.text = "Комментарии: $totalComm во всех разделах"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadCities() {
        database.getReference("cities").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                citiesList.clear()
                cityKeysMap.clear()
                for (child in snapshot.children) {
                    val city = child.getValue(String::class.java) ?: continue
                    val key = child.key ?: continue
                    citiesList.add(city)
                    cityKeysMap[city] = key
                }
                citiesAdapter.updateList(citiesList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addNewCity() {
        val cityName = etNewCity.text.toString().trim()
        if (cityName.isEmpty()) {
            Toast.makeText(this, "Введите название города", Toast.LENGTH_SHORT).show()
            return
        }

        if (citiesList.contains(cityName)) {
            Toast.makeText(this, "Город уже есть в списке", Toast.LENGTH_SHORT).show()
            return
        }

        val ref = database.getReference("cities").push()
        ref.setValue(cityName).addOnSuccessListener {
            etNewCity.text.clear()
            Toast.makeText(this, "Город '$cityName' добавлен", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Ошибка при добавлении: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDeleteCity(cityName: String) {
        AlertDialog.Builder(this)
            .setTitle("Удаление города")
            .setMessage("Удалить город '$cityName' из справочника?")
            .setPositiveButton("Удалить") { _, _ ->
                val key = cityKeysMap[cityName]
                if (key != null) {
                    database.getReference("cities").child(key).removeValue().addOnSuccessListener {
                        Toast.makeText(this, "Город удален", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Ошибка при удалении: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showModerationDialog() {
        database.getReference("comments").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allComments = mutableListOf<Pair<String, Comment>>() // Pair(fullPath, Comment)
                for (addrNode in snapshot.children) {
                    val addrId = addrNode.key ?: continue
                    for (commNode in addrNode.children) {
                        val commId = commNode.key ?: continue
                        val comm = commNode.getValue(Comment::class.java) ?: continue
                        allComments.add(Pair("$addrId/$commId", comm))
                    }
                }

                allComments.sortByDescending { it.second.timestamp }

                if (allComments.isEmpty()) {
                    Toast.makeText(this@AdminActivity, "Комментариев пока нет", Toast.LENGTH_SHORT).show()
                    return
                }

                val view = LayoutInflater.from(this@AdminActivity).inflate(R.layout.dialog_moderate_comments, null)
                val rv = view.findViewById<RecyclerView>(R.id.rvModerationComments)
                rv.layoutManager = LinearLayoutManager(this@AdminActivity)

                var alertDialog: AlertDialog? = null
                val commentsList = allComments.map { it.second }.toMutableList()

                val adapter = CommentAdapter(
                    commentsList,
                    isAdmin = true,
                    onReactionClick = { _, _ -> },
                    onReplyClick = { _ -> },
                    onDeleteClick = { comment ->
                        AlertDialog.Builder(this@AdminActivity)
                            .setTitle("Удаление комментария")
                            .setMessage("Удалить комментарий пользователя ${comment.authorEmail}?")
                            .setPositiveButton("Да") { _, _ ->
                                val path = allComments.find { it.second.id == comment.id }?.first
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
                    },
                    onEditClick = { _ -> }
                )

                rv.adapter = adapter

                val builder = AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Модерация всех комментариев")
                    .setView(view)
                    .setNegativeButton("Закрыть", null)

                alertDialog = builder.show()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
