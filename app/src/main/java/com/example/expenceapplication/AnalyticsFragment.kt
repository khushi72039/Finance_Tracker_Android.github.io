package com.example.expenceapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.expenceapplication.databinding.FragmentAnalyticsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadChartData()
    }

    private fun loadChartData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("transactions")
            .whereEqualTo("type", "Expense")
            .get()
            .addOnSuccessListener { snapshot ->
                val transactions = snapshot.toObjects(Transaction::class.java)
                if (transactions.isEmpty()) {
                    binding.tvNoData.visibility = View.VISIBLE
                    binding.pieChart.visibility = View.GONE
                    binding.barChart.visibility = View.GONE
                    binding.tvBarTitle.visibility = View.GONE
                } else {
                    binding.tvNoData.visibility = View.GONE
                    binding.pieChart.visibility = View.VISIBLE
                    binding.barChart.visibility = View.VISIBLE
                    binding.tvBarTitle.visibility = View.VISIBLE
                    
                    val categoryTotals = transactions.groupBy { it.category }
                        .mapValues { entry -> entry.value.sumOf { it.amount } }
                    
                    setupPieChart(categoryTotals)
                    setupBarChart(categoryTotals)
                }
            }
    }

    private fun setupPieChart(categoryMap: Map<String, Double>) {
        val entries = categoryMap.map { PieEntry(it.value.toFloat(), it.key) }
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_main)

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 14f

        val pieData = PieData(dataSet)
        binding.pieChart.data = pieData
        binding.pieChart.description.isEnabled = false
        binding.pieChart.centerText = "Spends"
        binding.pieChart.setCenterTextColor(textColor)
        binding.pieChart.setHoleColor(Color.TRANSPARENT)
        binding.pieChart.legend.textColor = textColor
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    private fun setupBarChart(categoryMap: Map<String, Double>) {
        val labels = categoryMap.keys.toList()
        val entries = mutableListOf<BarEntry>()
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_main)
        
        labels.forEachIndexed { index, label ->
            entries.add(BarEntry(index.toFloat(), categoryMap[label]?.toFloat() ?: 0f))
        }

        val dataSet = BarDataSet(entries, "Amount (₹)")
        dataSet.colors = ColorTemplate.LIBERTY_COLORS.toList()
        dataSet.valueTextColor = textColor
        dataSet.valueTextSize = 12f

        val barData = BarData(dataSet)
        binding.barChart.data = barData
        binding.barChart.description.isEnabled = false
        
        // Customize X-Axis
        val xAxis = binding.barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = textColor
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        // Customize Y-Axis
        binding.barChart.axisLeft.textColor = textColor
        binding.barChart.axisRight.isEnabled = false
        binding.barChart.legend.textColor = textColor
        
        binding.barChart.animateY(1000)
        binding.barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}