package com.example.basehome

import android.os.Bundle
import android.widget.ImageButton
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        database = FirebaseDatabase.getInstance()
        rvUsers = findViewById(R.id.rvUsersList)
        findViewById<ImageButton>(R.id.btnBackUsers).setOnClickListener { finish() }

        rvUsers.layoutManager = LinearLayoutManager(this)
        adapter = UsersAdapter(usersList)
        rvUsers.adapter = adapter

        loadData()
    }

    private fun loadData() {
        // Загружаем статистику один раз, чтобы не перегружать сеть
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
                        // Только теперь запускаем постоянный мониторинг статуса пользователей
                        listenToUsers()
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToUsers() {
        // Слушаем список пользователей постоянно (для онлайн-статуса)
        database.getReference("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<UserDetail>()
                for (u in snapshot.children) {
                    val uid = u.key ?: continue
                    val name = u.child("name").getValue(String::class.java) ?: "Аноним"
                    val email = u.child("email").getValue(String::class.java) ?: ""
                    val isOnline = u.child("isOnline").getValue(Boolean::class.java) ?: false
                    val lastSeen = u.child("lastSeen").getValue(Long::class.java) ?: 0L

                    newList.add(UserDetail(
                        uid = uid,
                        name = name,
                        email = email,
                        addressCount = addressCounts[uid] ?: 0,
                        commentCount = commentCounts[uid] ?: 0,
                        isOnline = isOnline,
                        lastSeen = lastSeen
                    ))
                }
                
                // Сортировка: сначала онлайн, потом по количеству вклада
                newList.sortWith(compareByDescending<UserDetail> { it.isOnline }.thenByDescending { it.addressCount })
                adapter.updateList(newList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
