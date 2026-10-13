package com.example.basehome

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminCitiesActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var cityAdapter: AdminCitiesAdapter
    private lateinit var cityCount: TextView
    private val cityNames = mutableListOf<String>()
    private val cityKeys = mutableMapOf<String, String>()
    private var citiesRef: DatabaseReference? = null
    private var citiesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_cities)
        SwipeBackHelper.attach(this)
        database = FirebaseDatabase.getInstance()

        findViewById<ImageButton>(R.id.btnBackCities).setOnClickListener { finish() }
        val input = findViewById<EditText>(R.id.etNewCity)
        cityCount = findViewById(R.id.tvCitiesCount)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddCity)
            .setOnClickListener { addCity(input) }

        cityAdapter = AdminCitiesAdapter(cityNames, ::confirmDeleteCity)
        findViewById<RecyclerView>(R.id.rvAdminCities).apply {
            layoutManager = LinearLayoutManager(this@AdminCitiesActivity)
            adapter = cityAdapter
        }
        listenForCities()
    }

    private fun listenForCities() {
        val ref = database.getReference("cities")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                cityNames.clear()
                cityKeys.clear()
                snapshot.children.forEach { child ->
                    val name = child.getValue(String::class.java)?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
                    val key = child.key ?: return@forEach
                    cityNames.add(name)
                    cityKeys[name] = key
                }
                cityNames.sortBy(String::lowercase)
                cityAdapter.updateList(cityNames.toList())
                cityCount.text = getString(R.string.admin_cities_count, cityNames.size)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminCitiesActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        citiesRef = ref
        citiesListener = listener
        ref.addValueEventListener(listener)
    }

    private fun addCity(input: EditText) {
        val name = input.text.toString().trim()
        if (name.isEmpty()) {
            input.error = getString(R.string.admin_city_required)
            return
        }
        if (cityNames.any { it.equals(name, ignoreCase = true) }) {
            input.error = getString(R.string.admin_city_duplicate)
            return
        }
        database.getReference("cities").push().setValue(name)
            .addOnSuccessListener {
                input.text.clear()
                Toast.makeText(this, getString(R.string.admin_city_added, name), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, getString(R.string.error_format, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDeleteCity(name: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_city_delete_title)
            .setMessage(getString(R.string.admin_city_delete_confirm, name))
            .setPositiveButton(R.string.admin_city_delete) { _, _ ->
                val key = cityKeys[name] ?: return@setPositiveButton
                database.getReference("cities").child(key).removeValue()
                    .addOnFailureListener { error ->
                        Toast.makeText(this, getString(R.string.error_format, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        citiesRef?.let { ref -> citiesListener?.let(ref::removeEventListener) }
        super.onDestroy()
    }
}
