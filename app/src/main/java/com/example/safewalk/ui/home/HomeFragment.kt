package com.example.safewalk.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.safewalk.R
import com.example.safewalk.databinding.FragmentHomeBinding
import com.example.safewalk.ui.dialogs.GuardianSheet
import com.example.safewalk.ui.dialogs.SOSDialog

import android.widget.Toast
import com.example.safewalk.data.model.User
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
        syncGuardianMode()
    }

    private fun syncGuardianMode() {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (_binding != null && snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)
                user?.let {
                    binding.guardianModeToggle.isChecked = it.isGuardianMode
                }
            }
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
            // Handle Alerts click (e.g., navigate to notification history)
        }

        binding.guardianModeToggle.setOnCheckedChangeListener { _, isChecked ->
            updateGuardianMode(isChecked)
        }
    }

    private fun updateGuardianMode(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("isGuardianMode", enabled)
            .addOnFailureListener {
                if (_binding != null) {
                    Toast.makeText(context, "Failed to update Guardian Mode", Toast.LENGTH_SHORT).show()
                }
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
        // Optional: hide after a delay or just leave at 0
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
