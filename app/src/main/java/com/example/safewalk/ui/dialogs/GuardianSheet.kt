package com.example.safewalk.ui.dialogs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.safewalk.R
import com.example.safewalk.databinding.BottomSheetGuardianBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class GuardianSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGuardianBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openContactPicker()
        } else {
            Toast.makeText(requireContext(), "Permission denied to read contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            processContact(contactUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetGuardianBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        auth = Firebase.auth
        db = Firebase.firestore

        setupListeners()
        loadGuardians()
    }

    private fun setupListeners() {
        binding.addGuardianButton.setOnClickListener {
            saveGuardian()
        }
        
        binding.dismissButton.setOnClickListener {
            dismiss()
        }

        binding.btnPickContact.setOnClickListener {
            checkContactsPermission()
        }
    }

    private fun checkContactsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                openContactPicker()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun processContact(contactUri: android.net.Uri) {
        val cursor = requireContext().contentResolver.query(contactUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                val name = it.getString(nameIndex)
                val phone = it.getString(phoneIndex)
                
                binding.guardianNameInput.setText(name)
                binding.guardianPhoneInput.setText(phone)
            }
        }
    }


    private fun saveGuardian() {
        val name = binding.guardianNameInput.text.toString().trim()
        val phone = binding.guardianPhoneInput.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (userId != null) {
            val guardian = hashMapOf(
                "name" to name,
                "phone" to phone,
                "timestamp" to System.currentTimeMillis()
            )

            binding.addGuardianButton.isEnabled = false
            binding.addGuardianButton.text = "Saving..."

            db.collection("users").document(userId)
                .collection("guardians")
                .add(guardian)
                .addOnSuccessListener {
                    if (isAdded) {
                        binding.addGuardianButton.isEnabled = true
                        binding.addGuardianButton.text = "Add Guardian"
                        binding.guardianNameInput.text?.clear()
                        binding.guardianPhoneInput.setText("")
                        loadGuardians()
                        Toast.makeText(requireContext(), "Guardian added successfully", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    if (isAdded) {
                        binding.addGuardianButton.isEnabled = true
                        binding.addGuardianButton.text = "Add Guardian"
                        Toast.makeText(requireContext(), "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun loadGuardians() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId)
            .collection("guardians")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                if (_binding != null) {
                    binding.guardianList.removeAllViews()
                    for (document in result) {
                        val name = document.getString("name") ?: ""
                        val phone = document.getString("phone") ?: ""
                        addGuardianView(name, phone)
                    }
                    updateGuardianCount(result.size().toInt())
                }
            }
    }

    private fun updateGuardianCount(count: Int) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update("guardianCount", count)
            .addOnFailureListener { e ->
                android.util.Log.e("GuardianSheet", "Failed to update count", e)
            }
    }

    private fun addGuardianView(name: String, phone: String) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_guardian, binding.guardianList, false)
        view.findViewById<TextView>(R.id.itemGuardianName).text = name
        view.findViewById<TextView>(R.id.itemGuardianPhone).text = phone
        binding.guardianList.addView(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
