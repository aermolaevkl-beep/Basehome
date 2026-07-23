package com.example.basehome

data class UserDetail(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val addressCount: Int = 0,
    val commentCount: Int = 0,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0
)
