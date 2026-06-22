package com.example.basehome

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etLoginEmail)
        etPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)

        btnLogin.setOnClickListener {
            loginUser()
        }
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            tvError.visibility = View.VISIBLE
            tvError.text = getString(R.string.fill_fields)
            return
        }

        tvError.visibility = View.GONE

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    val userRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid)
                    userRef.child("email").setValue(user.email)
                    
                    Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
                    
                    // Исправлено: запуск MainActivity перед закрытием окна входа
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                tvError.visibility = View.VISIBLE
                tvError.text = getString(R.string.error_format, e.message)
            }
    }
}