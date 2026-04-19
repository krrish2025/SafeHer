package com.example.safewalk

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.safewalk.databinding.ActivityMainBinding
import com.example.safewalk.util.NotificationHelper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var alertListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        checkNotificationPermission()
        listenForEmergencyAlerts()
        updateHeroLocation()
    }

    private fun updateHeroLocation() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val db = Firebase.firestore
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)

        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val updates = mapOf(
                        "lastLat" to it.latitude,
                        "lastLng" to it.longitude
                    )
                    db.collection("users").document(uid).update(updates)
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun listenForEmergencyAlerts() {
        val currentUser = Firebase.auth.currentUser ?: return
        
        alertListener = Firebase.firestore.collection("app_alerts")
            .whereEqualTo("toUid", currentUser.uid)
            .whereEqualTo("status", "SENT")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                for (dc in snapshots!!.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val alertId = dc.document.id
                        val fromName = dc.document.getString("fromName") ?: "Someone"
                        val locationUrl = dc.document.getString("locationUrl") ?: ""
                        
                        // Show a local notification
                        NotificationHelper.showLocalNotification(
                            this,
                            "EMERGENCY: $fromName",
                            "Your trusted contact needs help! Tap to see location.",
                            locationUrl
                        )
                        
                        // Mark as received so we don't alert again
                        Firebase.firestore.collection("app_alerts").document(alertId)
                            .update("status", "RECEIVED")
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        alertListener?.remove()
    }
}
