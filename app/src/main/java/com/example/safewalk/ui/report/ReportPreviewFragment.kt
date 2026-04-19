package com.example.safewalk.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.safewalk.databinding.FragmentReportPreviewBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.appcompat.app.AlertDialog
import com.example.safewalk.R

class ReportPreviewFragment : Fragment() {

    private var _binding: FragmentReportPreviewBinding? = null
    private val binding get() = _binding!!
    private val args: ReportPreviewFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        binding.previewCategory.text = args.category
        binding.previewDescription.text = args.description
        binding.previewLocation.text = args.locationName ?: "Lat: ${args.latitude}, Lng: ${args.longitude}"

        if (!args.imageUri.isNullOrEmpty()) {
            if (args.imageUri!!.startsWith("data:image")) {
                val base64String = args.imageUri!!.substringAfter("base64,")
                val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.previewEvidence.setImageBitmap(bitmap)
            }
            binding.previewEvidence.visibility = View.VISIBLE
            binding.noEvidenceText.visibility = View.GONE
        } else {
            binding.previewEvidence.visibility = View.GONE
            binding.noEvidenceText.visibility = View.VISIBLE
        }

        binding.editButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.confirmButton.setOnClickListener {
            showConfirmationDialog()
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Submission")
            .setMessage("Are you sure you want to submit this report? It will be recorded on the blockchain for security.")
            .setPositiveButton("Submit") { _, _ ->
                submitFinalReport()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitFinalReport() {
        binding.confirmButton.isEnabled = false
        binding.confirmButton.text = "Submitting..."

        val report = hashMapOf(
            "userId" to (Firebase.auth.currentUser?.uid ?: ""),
            "category" to args.category,
            "description" to args.description,
            "locationName" to args.locationName,
            "latitude" to args.latitude.toDouble(),
            "longitude" to args.longitude.toDouble(),
            "imageUrl" to args.imageUri,
            "timestamp" to System.currentTimeMillis()
        )

        Firebase.firestore.collection("reports")
            .add(report)
            .addOnSuccessListener {
                val uid = Firebase.auth.currentUser?.uid
                if (uid != null) {
                    Firebase.firestore.collection("users").document(uid)
                        .update("reportCount", com.google.firebase.firestore.FieldValue.increment(1))
                }
                Toast.makeText(context, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.navigation_home)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Submit error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                binding.confirmButton.isEnabled = true
                binding.confirmButton.text = "Confirm & Submit"
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
