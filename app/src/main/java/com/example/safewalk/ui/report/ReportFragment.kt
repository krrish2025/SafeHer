package com.example.safewalk.ui.report

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.safewalk.R
import com.example.safewalk.databinding.FragmentReportBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream
import java.util.UUID

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    
    private var selectedImageUri: Uri? = null
    private var selectedLocation: String? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.evidenceImage.setImageURI(it)
            binding.evidenceImage.visibility = View.VISIBLE
            binding.uploadPlaceholder.visibility = View.GONE
        }
    }

    private val captureImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                val uri = getImageUri(it)
                selectedImageUri = uri
                binding.evidenceImage.setImageBitmap(it)
                binding.evidenceImage.visibility = View.VISIBLE
                binding.uploadPlaceholder.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener("location_request") { _, bundle ->
            selectedLatitude = bundle.getDouble("lat")
            selectedLongitude = bundle.getDouble("lng")
            selectedLocation = bundle.getString("address") ?: "Custom Location"
            binding.locationText.text = selectedLocation
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        setupUI()
        return binding.root
    }

    private fun setupUI() {
        binding.categoryGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.btnOther) {
                binding.otherCategoryInput.visibility = View.VISIBLE
            } else {
                binding.otherCategoryInput.visibility = View.GONE
            }
        }

        binding.evidenceUpload.setOnClickListener {
            showImagePickerOptions()
        }

        binding.locationPicker.setOnClickListener {
            val bundle = Bundle()
            bundle.putBoolean("pick_mode", true)
            findNavController().navigate(R.id.navigation_map, bundle)
        }

        binding.submitButton.setOnClickListener {
            submitReport()
        }
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Gallery", "Camera")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Evidence From")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImage.launch("image/*")
                    1 -> {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        captureImage.launch(intent)
                    }
                }
            }
            .show()
    }

    private fun getImageUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(requireContext().contentResolver, bitmap, "Evidence", null)
        return Uri.parse(path)
    }

    private fun submitReport() {
        val checkedId = binding.categoryGroup.checkedRadioButtonId
        if (checkedId == -1) {
            Toast.makeText(context, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        var category = when (checkedId) {
            R.id.btnHarassment -> "Harassment"
            R.id.btnStalking -> "Stalking"
            R.id.btnSuspicious -> "Suspicious"
            else -> binding.otherCategoryInput.text.toString()
        }

        if (category.isEmpty()) {
            Toast.makeText(context, "Please specify the issue", Toast.LENGTH_SHORT).show()
            return
        }

        val description = binding.descriptionInput.text.toString()
        if (description.isEmpty()) {
            Toast.makeText(context, "Please enter a description", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLocation == null) {
            Toast.makeText(context, "Please select a location", Toast.LENGTH_SHORT).show()
            return
        }

        binding.submitButton.isEnabled = false
        binding.submitButton.text = "Uploading..."

        if (selectedImageUri != null) {
            uploadImage(category, description)
        } else {
            saveToFirestore(category, description, null)
        }
    }

    private fun uploadImage(category: String, description: String) {
        val storageRef = Firebase.storage.reference.child("evidence/${UUID.randomUUID()}.jpg")
        storageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveToFirestore(category, description, uri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                binding.submitButton.isEnabled = true
                binding.submitButton.text = "Submit Report →"
            }
    }

    private fun saveToFirestore(category: String, description: String, imageUrl: String?) {
        val report = hashMapOf(
            "userId" to (Firebase.auth.currentUser?.uid ?: ""),
            "category" to category,
            "description" to description,
            "locationName" to selectedLocation,
            "latitude" to selectedLatitude,
            "longitude" to selectedLongitude,
            "imageUrl" to imageUrl,
            "timestamp" to System.currentTimeMillis()
        )

        Firebase.firestore.collection("reports")
            .add(report)
            .addOnSuccessListener {
                Toast.makeText(context, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to submit report", Toast.LENGTH_SHORT).show()
                binding.submitButton.isEnabled = true
                binding.submitButton.text = "Submit Report →"
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
