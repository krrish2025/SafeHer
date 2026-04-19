package com.example.safewalk.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.safewalk.R
import com.example.safewalk.databinding.FragmentProfileBinding
import com.example.safewalk.ui.auth.AuthActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import android.widget.Toast
import com.example.safewalk.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        auth = Firebase.auth
        db = Firebase.firestore
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayUserInfo()
        fetchUserStats()
        setupListeners()
        setupLogout()
        syncPhoneDisplay()
    }

    private fun syncPhoneDisplay() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (_binding != null && snapshot != null && snapshot.exists()) {
                val phone = snapshot.getString("phone")
                if (!phone.isNullOrEmpty()) {
                    binding.profilePhoneText.text = phone
                    binding.profilePhoneText.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text))
                } else {
                    binding.profilePhoneText.text = "Add Phone"
                    binding.profilePhoneText.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.ember))
                }
            }
        }
    }

    private fun displayUserInfo() {
        val user = auth.currentUser
        user?.let {
            binding.profileName.text = it.displayName ?: "SafeWalk User"
            binding.profileEmail.text = it.email
            
            val initials = if (!it.displayName.isNullOrEmpty()) {
                it.displayName!!.split(" ").filter { s -> s.isNotEmpty() }.map { name -> name.take(1) }.take(2).joinToString("").uppercase()
            } else "SW"
            binding.profileInitials.text = initials

            if (it.photoUrl != null) {
                binding.profileInitials.visibility = View.GONE
                Glide.with(this)
                    .load(it.photoUrl)
                    .circleCrop()
                    .into(binding.profileImage)
            } else {
                binding.profileInitials.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchUserStats() {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (_binding != null && snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)
                user?.let {
                    binding.guardianCount.text = it.guardianCount.toString()
                    binding.reportCount.text = it.reportCount.toString()
                    
                    // Temporarily remove listeners to prevent infinite loops when updating UI from Firestore
                    binding.communityModeToggle.setOnCheckedChangeListener(null)
                    
                    binding.communityModeToggle.isChecked = it.isCommunityMember
                    
                    // Restore listeners
                    binding.communityModeToggle.setOnCheckedChangeListener { _, isChecked ->
                        updateCommunityMode(isChecked)
                    }
                }
            }
        }
    }


    private fun setupListeners() {
        // Listeners are now initialized/restored in fetchUserStats to avoid infinite loops
        
        binding.rowPhone.setOnClickListener {
            showPhoneInputDialog()
        }

        binding.rowGuardians.setOnClickListener {
            val sheet = com.example.safewalk.ui.dialogs.GuardianSheet()
            sheet.show(parentFragmentManager, "GUARDIAN_SHEET")
        }

        binding.rowReports.setOnClickListener {
            // TODO: Navigate to My Reports list
            Toast.makeText(context, "Coming Soon: My Incident Reports", Toast.LENGTH_SHORT).show()
        }

        binding.rowNotifications.setOnClickListener {
            Toast.makeText(context, "Coming Soon: Notifications History", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateCommunityMode(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        
        val updates = mutableMapOf<String, Any>(
            "isCommunityMember" to enabled
        )

        if (enabled) {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity())
            if (androidx.core.app.ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        updates["lastLat"] = it.latitude
                        updates["lastLng"] = it.longitude
                    }
                    db.collection("users").document(uid).update(updates)
                }
            } else {
                db.collection("users").document(uid).update(updates)
            }
        } else {
            db.collection("users").document(uid).update(updates)
        }
    }


    private fun setupLogout() {
        binding.logoutButton.setOnClickListener {
            // Firebase Sign Out
            auth.signOut()

            // Google Sign Out (to allow choosing account again)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
            googleSignInClient.signOut().addOnCompleteListener {
                navigateToAuth()
            }
        }
    }

    private fun showPhoneInputDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_input_phone, null)
        val phoneInput = dialogView.findViewById<android.widget.EditText>(R.id.phoneInput)
        
        // Pre-fill if exists
        val currentPhone = binding.profilePhoneText.text.toString()
        if (currentPhone != "Add Phone") {
            phoneInput.setText(currentPhone)
        }

        builder.setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val phone = phoneInput.text.toString().trim()
                if (phone.length >= 10) {
                    saveUserPhone(phone)
                } else {
                    Toast.makeText(requireContext(), "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
        
        builder.create().show()
    }

    private fun saveUserPhone(phone: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("phone", phone)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Phone updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToAuth() {
        val intent = Intent(requireContext(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        _binding = null
    }

}
