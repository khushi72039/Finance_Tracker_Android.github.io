package com.example.expenceapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expenceapplication.databinding.DialogAddExpenseBinding
import com.example.expenceapplication.databinding.DialogAddIncomeBinding
import com.example.expenceapplication.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var allTransactions = listOf<Transaction>()
    private var filteredTransactions = mutableListOf<Transaction>()
    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupFilters()
        loadTransactions()
        
        binding.fabAdd.setOnClickListener { showAddOptionsDialog() }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(filteredTransactions)
        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (_binding != null) applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, _ ->
            if (_binding != null) applyFilters()
        }
    }

    private fun applyFilters() {
        if (_binding == null) return
        val query = binding.etSearch.text.toString().lowercase()
        val checkedChipId = binding.chipGroupFilter.checkedChipId

        filteredTransactions.clear()
        filteredTransactions.addAll(allTransactions.filter { transaction ->
            val matchesQuery = transaction.description.lowercase().contains(query) || 
                             transaction.category.lowercase().contains(query)
            
            val matchesType = when (checkedChipId) {
                R.id.chipIncome -> transaction.type == "Income"
                R.id.chipExpense -> transaction.type == "Expense"
                else -> true
            }
            matchesQuery && matchesType
        })

        if (filteredTransactions.isEmpty()) {
            binding.tvNoData.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        } else {
            binding.tvNoData.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
    }

    private fun showAddOptionsDialog() {
        val options = arrayOf("Add Income", "Add Spend")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Transaction Type")
            .setItems(options) { _, which ->
                if (which == 0) showAddIncomeDialog() else showAddExpenseDialog()
            }
            .show()
    }

    private fun loadTransactions() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (_binding == null || e != null) return@addSnapshotListener
                if (snapshot != null) {
                    allTransactions = snapshot.toObjects(Transaction::class.java)
                    applyFilters()
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