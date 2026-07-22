package com.example.expenceapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.expenceapplication.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            generatePdf(it)
        }
    }

    private var currentTransactions = listOf<Transaction>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadUserDetails()

        binding.btnDownloadStatement.setOnClickListener {
            fetchMonthlyTransactionsAndDownload()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun fetchMonthlyTransactionsAndDownload() {
        val userId = auth.currentUser?.uid ?: return
        
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        db.collection("users").document(userId).collection("transactions")
            .get()
            .addOnSuccessListener { snapshot ->
                val allTx = snapshot.toObjects(Transaction::class.java)
                currentTransactions = allTx.filter { tx ->
                    val txCal = Calendar.getInstance()
                    txCal.timeInMillis = tx.timestamp
                    txCal.get(Calendar.MONTH) == currentMonth && txCal.get(Calendar.YEAR) == currentYear
                }.sortedByDescending { it.timestamp }

                if (currentTransactions.isEmpty()) {
                    Toast.makeText(context, "No transactions for this month to download.", Toast.LENGTH_SHORT).show()
                } else {
                    val fileName = "Statement_${SimpleDateFormat("MMM_yyyy", Locale.getDefault()).format(Date())}.pdf"
                    createDocumentLauncher.launch(fileName)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to fetch data.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generatePdf(uri: android.net.Uri) {
        val name = binding.tvProfileName.text.toString()
        val email = binding.tvProfileEmail.text.toString()

        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                PdfGenerator.generateMonthlyStatement(
                    requireContext(),
                    outputStream,
                    name,
                    email,
                    currentTransactions
                )
                Toast.makeText(context, "Statement saved successfully!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadUserDetails() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    binding.tvProfileName.text = document.getString("name") ?: "No Name"
                    binding.tvProfileEmail.text = document.getString("email") ?: "No Email"
                }
            }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                findNavController().navigate(R.id.action_global_loginFragment)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Warning: This will permanently delete your account and all your data. This action cannot be undone.")
            .setPositiveButton("Delete Permanently") { _, _ ->
                deleteUserAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser
        val userId = user?.uid ?: return

        // 1. Delete data from Firestore
        db.collection("users").document(userId).delete()
            .addOnSuccessListener {
                // 2. Delete Auth user
                user.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(context, "Account Deleted", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_global_loginFragment)
                    } else {
                        Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to delete data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}