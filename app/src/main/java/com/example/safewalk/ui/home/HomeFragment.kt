package com.example.safewalk.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.safewalk.R
import com.example.safewalk.databinding.FragmentHomeBinding
import com.example.safewalk.ui.dialogs.GuardianSheet
import com.example.safewalk.ui.dialogs.SOSDialog

import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = Firebase.auth
        db = Firebase.firestore
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        startPulseAnimation()
        setupListeners()
        checkUserPhone()
    }

    private fun checkUserPhone() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val phone = snapshot.getString("phone")
                if (phone.isNullOrBlank()) {
                    showPhoneInputDialog()
                }
            }
        }
    }

    private fun showPhoneInputDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_input_phone, null)
        val phoneInput = dialogView.findViewById<android.widget.EditText>(R.id.phoneInput)

        builder.setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val phone = phoneInput.text.toString().trim()
                if (phone.length >= 10) {
                    saveUserPhone(phone)
                } else {
                    Toast.makeText(requireContext(), "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                    showPhoneInputDialog() // Re-show if invalid
                }
            }
        
        builder.create().show()
    }

    private fun saveUserPhone(phone: String) {
        val uid = auth.currentUser?.uid ?: return
        val cleanPhone = phone.replace("\\D".toRegex(), "").takeLast(10)
        db.collection("users").document(uid).update("phone", cleanPhone)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Phone number updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update phone", Toast.LENGTH_SHORT).show()
                showPhoneInputDialog()
            }
    }

    private fun startPulseAnimation() {
        val pulse = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        binding.pulseRing.startAnimation(pulse)
    }



    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0
    private val totalTime = 3000L // 3 seconds
    private val interval = 30L // update every 30ms

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (_binding == null) return
            progress += (interval * 100 / totalTime).toInt()
            if (progress >= 100) {
                binding.sosProgress.progress = 100
                triggerSOS()
            } else {
                binding.sosProgress.progress = progress
                handler.postDelayed(this, interval)
            }
        }
    }



    private fun setupListeners() {
        requestPermissionsIfNeeded()
        binding.sosButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    startSOSCounter()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopSOSCounter()
                    true
                }
                else -> false
            }
        }

        binding.actionSafeRoute.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.navigation_map
        }

        binding.actionReport.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.navigation_report
        }

        binding.actionGuardians.setOnClickListener {
            val sheet = GuardianSheet()
            sheet.show(parentFragmentManager, "GUARDIAN_SHEET")
        }

        binding.actionAlerts.setOnClickListener {
            Toast.makeText(context, "Coming Soon: Recent Alerts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS
        )
        
        val permissionsToRequest = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 1001)
        }
    }

    private fun startSOSCounter() {
        progress = 0
        binding.sosProgress.progress = 0
        binding.sosProgress.visibility = View.VISIBLE
        handler.post(progressRunnable)
    }

    private fun stopSOSCounter() {
        handler.removeCallbacks(progressRunnable)
        progress = 0
        binding.sosProgress.progress = 0
    }



    private fun triggerSOS() {
        stopSOSCounter()
        val dialog = SOSDialog()
        dialog.show(parentFragmentManager, "SOS_DIALOG")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        _binding = null
    }

}
