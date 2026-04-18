package com.example.safewalk.util

import android.content.Context
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object NotificationHelper {

    private val client = OkHttpClient()

    /**
     * Sends an SOS notification to a specific user via their FCM token.
     * Note: In a production app, this logic should reside in a Backend (Firebase Cloud Functions).
     * This is a client-side implementation for demonstration purposes.
     */
    fun sendSOSNotification(context: Context, victimName: String, locationUrl: String, guardianUid: String) {
        Firebase.firestore.collection("users").document(guardianUid)
            .get()
            .addOnSuccessListener { document ->
                val fcmToken = document.getString("fcmToken")
                if (!fcmToken.isNullOrEmpty()) {
                    triggerFCM(victimName, locationUrl, fcmToken)
                }
            }
    }

    private fun triggerFCM(victimName: String, locationUrl: String, targetToken: String) {
        // IMPORTANT: Sending FCM directly from the app using a Server Key is insecure.
        // The proper way is using Firebase Cloud Functions.
        // This is a placeholder showing the structure of the message.
        
        val json = JSONObject()
        val notification = JSONObject()
        notification.put("title", "EMERGENCY: $victimName")
        notification.put("body", "I am in danger! Location: $locationUrl")
        
        val data = JSONObject()
        data.put("type", "SOS")
        data.put("location", locationUrl)
        
        json.put("to", targetToken)
        json.put("notification", notification)
        json.put("data", data)

        // To make this actually work, you'd need to call an endpoint.
        // Since we don't have a server set up yet, we will rely on Firestore 
        // triggers or a Cloud Function to handle this part.
    }
}
