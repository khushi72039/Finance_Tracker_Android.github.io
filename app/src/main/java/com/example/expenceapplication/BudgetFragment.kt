package com.example.expenceapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expenceapplication.databinding.FragmentBudgetBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val categories = arrayOf("Food", "Travel", "Shopping", "Bills", "Education", "Healthcare", "Entertainment", "Investment", "Other")
    private val budgetList = mutableListOf<Budget>()
    private lateinit var adapter: BudgetAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadBudgetsAndSpending()
        
        binding.btnSetTotalBudget.setOnClickListener {
            showSetGlobalLimitDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = BudgetAdapter(budgetList) { budget ->
            showSetLimitDialog(budget)
        }
        binding.rvBudget.layoutManager = LinearLayoutManager(context)
        binding.rvBudget.adapter = adapter
    }

    private fun loadBudgetsAndSpending() {
        val userId = auth.currentUser?.uid ?: return
        
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Listen to Global Budget
        db.collection("users").document(userId).collection("budgets").document("global")
            .addSnapshotListener { doc, _ ->
                val globalLimit = doc?.getDouble("limit") ?: 5000.0
                binding.tvTotalBudgetAmount.text = "₹${globalLimit.toInt()}"
            }

        // Listen to Category Budgets and Transactions
        db.collection("users").document(userId).collection("budgets")
            .addSnapshotListener { budgetSnapshot, _ ->
                val limits = mutableMapOf<String, Double>()
                budgetSnapshot?.forEach { doc ->
                    if (doc.id != "global") {
                        limits[doc.id] = doc.getDouble("limit") ?: 0.0
                    }
                }

                db.collection("users").document(userId).collection("transactions")
                    .whereEqualTo("type", "Expense")
                    .addSnapshotListener { transSnapshot, _ ->
                        val spending = mutableMapOf<String, Double>()
                        
                        transSnapshot?.forEach { doc ->
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val transCal = Calendar.getInstance()
                            transCal.timeInMillis = timestamp
                            
                            // ONLY count if it's in the CURRENT month and year
                            if (transCal.get(Calendar.MONTH) == currentMonth && 
                                transCal.get(Calendar.YEAR) == currentYear) {
                                
                                val cat = doc.getString("category") ?: ""
                                val amt = doc.getDouble("amount") ?: 0.0
                                spending[cat] = (spending[cat] ?: 0.0) + amt
                            }
                        }

                        budgetList.clear()
                        categories.forEach { cat ->
                            budgetList.add(Budget(cat, limits[cat] ?: 0.0, spending[cat] ?: 0.0))
                        }
                        adapter.notifyDataSetChanged()
                    }
            }
    }

    private fun showSetGlobalLimitDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter total monthly limit"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Total Monthly Budget")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val limit = input.text.toString().toDoubleOrNull() ?: 5000.0
                saveLimit("global", limit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSetLimitDialog(budget: Budget) {
        val input = EditText(requireContext())
        input.hint = "Enter monthly limit"
        input.setText(budget.limitAmount.toInt().toString())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Limit for ${budget.category}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val limit = input.text.toString().toDoubleOrNull() ?: 0.0
                saveLimit(budget.category, limit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveLimit(category: String, limit: Double) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("budgets").document(category)
            .set(mapOf("limit" to limit))
            .addOnSuccessListener {
                Toast.makeText(context, "Limit updated", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}