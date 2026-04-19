package com.example.safewalk.data.model

import com.google.firebase.firestore.PropertyName

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val fcmToken: String = "",
    val photoUrl: String? = null,
    
    @get:PropertyName("isGuardianMode")
    @set:PropertyName("isGuardianMode")
    var isGuardianMode: Boolean = false,
    
    @get:PropertyName("isCommunityMember")
    @set:PropertyName("isCommunityMember")
    var isCommunityMember: Boolean = false,
    
    var guardianCount: Int = 0,
    var reportCount: Int = 0,
    var lastLat: Double = 0.0,
    var lastLng: Double = 0.0
)
