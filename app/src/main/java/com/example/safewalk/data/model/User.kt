package com.example.safewalk.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val fcmToken: String = "",
    val photoUrl: String? = null,
    val isGuardianMode: Boolean = false,
    val guardianCount: Int = 0,
    val reportCount: Int = 0
)
