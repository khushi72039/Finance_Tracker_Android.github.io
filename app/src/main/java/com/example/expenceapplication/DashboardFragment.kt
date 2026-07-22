package com.example.expenceapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expenceapplication.databinding.DialogAddExpenseBinding
import com.example.expenceapplication.databinding.DialogAddIncomeBinding
import com.example.expenceapplication.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var currentMonthlySpent = 0.0
    private var currentTotalBalance = 0.0
    private var currentGlobalLimit = 5000.0
    private var currentTopCategories: List<Pair<String, Double>> = emptyList()
    private var hasGeneratedInitialInsight = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (_binding != null && document != null) {
                        val name = document.getString("name")
                        binding.tvWelcomeUser.text = "Welcome, $name"
                    }
                }
        }

        binding.btnAddSpend.setOnClickListener { showAddExpenseDialog() }
        binding.btnAddIncome.setOnClickListener { showAddIncomeDialog() }
        binding.btnTxHistory.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_historyFragment)
        }
        binding.ivRefreshAi.setOnClickListener { generateDashboardInsight() }

        observeData()
    }

    private fun observeData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .collection("transactions")
            .addSnapshotListener { snapshot, e ->
                if (_binding == null || e != null) return@addSnapshotListener
                if (snapshot != null) {
                    currentTotalBalance = 0.0
                    currentMonthlySpent = 0.0
                    val categoryTotals = mutableMapOf<String, Double>()

                    val calendar = Calendar.getInstance()
                    val currentMonth = calendar.get(Calendar.MONTH)
                    val currentYear = calendar.get(Calendar.YEAR)

                    for (doc in snapshot.documents) {
                        val amount = doc.getDouble("amount") ?: 0.0
                        val type = doc.getString("type") ?: "Expense"
                        val category = doc.getString("category") ?: "Other"
                        val timestamp = doc.getLong("timestamp") ?: 0L

                        if (type == "Income") currentTotalBalance += amount
                        else currentTotalBalance -= amount

                        if (type == "Expense") {
                            val transCal = Calendar.getInstance()
                            transCal.timeInMillis = timestamp
                            if (transCal.get(Calendar.MONTH) == currentMonth &&
                                transCal.get(Calendar.YEAR) == currentYear
                            ) {
                                currentMonthlySpent += amount
                                categoryTotals[category] = (categoryTotals[category] ?: 0.0) + amount
                            }
                        }
                    }

                    currentTopCategories = categoryTotals.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .map { it.key to it.value }

                    binding.tvBalance.text = "₹${"%.2f".format(currentTotalBalance)}"
                    binding.tvMonthlySpent.text = "₹${currentMonthlySpent.toInt()}"
                    updateBudgetUI()

                    if (!hasGeneratedInitialInsight && snapshot.size() > 0) {
                        hasGeneratedInitialInsight = true
                        generateDashboardInsight()
                    }
                }
            }

        db.collection("users").document(userId).collection("budgets").document("global")
            .addSnapshotListener { doc, _ ->
                if (_binding == null) return@addSnapshotListener
                currentGlobalLimit = doc?.getDouble("limit") ?: 5000.0
                binding.tvMonthlyLimit.text = "₹${currentGlobalLimit.toInt()}"
                updateBudgetUI()
            }
    }

    private fun updateBudgetUI() {
        if (_binding == null) return
        val remaining = currentGlobalLimit - currentMonthlySpent
        binding.tvBudgetLeft.text = "₹${remaining.toInt()}"

        val progress = if (currentGlobalLimit > 0) {
            ((currentMonthlySpent / currentGlobalLimit) * 100).toInt()
        } else {
            0
        }
        binding.progressMonthlyBudget.progress = progress

        val color = if (progress >= 100) R.color.error_red else R.color.accent_purple
        context?.let {
            binding.progressMonthlyBudget.setIndicatorColor(it.getColor(color))
        }
    }

    private fun generateDashboardInsight() {
        if (_binding == null) return

        binding.pbAiInsight.visibility = View.VISIBLE
        binding.tvAiInsight.text = "AI is thinking..."

        val prompt = "Wallet Balance: ₹$currentTotalBalance. Spent: ₹$currentMonthlySpent. Limit: ₹$currentGlobalLimit. Give 1 short financial advice."

        lifecycleScope.launch {
            val result = GeminiService.generateText(prompt)
            if (_binding != null) {
                result.onSuccess { text ->
                    binding.tvAiInsight.text = text
                }.onFailure { error ->
                    binding.tvAiInsight.text = "AI Notice: ${error.message?.take(100)}"
                }
                binding.pbAiInsight.visibility = View.GONE
            }
        }
    }

    private fun showAddExpenseDialog() {
        val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogBinding.root)
            .create()

        var selectedTimestamp = System.currentTimeMillis()
        dialogBinding.etDate.setText(dateFormat.format(Date(selectedTimestamp)))

        dialogBinding.etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedTimestamp
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(year, month, day)
                selectedTimestamp = calendar.timeInMillis
                dialogBinding.etDate.setText(dateFormat.format(Date(selectedTimestamp)))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.actvCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, arrayOf("Food", "Travel", "Shopping", "Bills", "Education", "Healthcare", "Entertainment", "Investment", "Other")))
        dialogBinding.actvPaymentMethod.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, arrayOf("Cash", "Card", "UPI", "Bank Transfer")))

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val amount = dialogBinding.etAmount.text.toString()
            val description = dialogBinding.etDescription.text.toString()
            val category = dialogBinding.actvCategory.text.toString()
            val paymentMethod = dialogBinding.actvPaymentMethod.text.toString()

            if (amount.isNotEmpty() && description.isNotEmpty() && category.isNotEmpty() && paymentMethod.isNotEmpty()) {
                saveTransactionToFirestore(amount.toDouble(), description, category, paymentMethod, "Expense", selectedTimestamp, dialog)
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showAddIncomeDialog() {
        val dialogBinding = DialogAddIncomeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogBinding.root)
            .create()

        var selectedTimestamp = System.currentTimeMillis()
        dialogBinding.etDate.setText(dateFormat.format(Date(selectedTimestamp)))

        dialogBinding.etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedTimestamp
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(year, month, day)
                selectedTimestamp = calendar.timeInMillis
                dialogBinding.etDate.setText(dateFormat.format(Date(selectedTimestamp)))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.actvCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, arrayOf("Salary", "Freelance", "Investment", "Gift", "Refund", "Other")))
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val amount = dialogBinding.etAmount.text.toString()
            val description = dialogBinding.etDescription.text.toString()
            val category = dialogBinding.actvCategory.text.toString()
            if (amount.isNotEmpty() && description.isNotEmpty() && category.isNotEmpty()) {
                saveTransactionToFirestore(amount.toDouble(), description, category, "Bank", "Income", selectedTimestamp, dialog)
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun saveTransactionToFirestore(amount: Double, description: String, category: String, paymentMethod: String, type: String, timestamp: Long, dialog: AlertDialog) {
        val userId = auth.currentUser?.uid ?: return
        val transaction = hashMapOf("amount" to amount, "description" to description, "category" to category, "paymentMethod" to paymentMethod, "type" to type, "timestamp" to timestamp)
        db.collection("users").document(userId).collection("transactions").add(transaction)
            .addOnSuccessListener {
                if (_binding != null) Toast.makeText(context, "$type Saved!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .addOnFailureListener { e -> if (_binding != null) Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}