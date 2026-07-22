package com.example.expenceapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.expenceapplication.databinding.ItemBudgetBinding

class BudgetAdapter(
    private val budgets: List<Budget>,
    private val onSetLimitClick: (Budget) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    class BudgetViewHolder(val binding: ItemBudgetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val binding = ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BudgetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val budget = budgets[position]
        holder.binding.tvCategoryName.text = budget.category
        holder.binding.tvLimit.text = "Limit: ₹${budget.limitAmount.toInt()}"
        
        val spent = budget.spentAmount
        val limit = budget.limitAmount
        
        holder.binding.tvSpentInfo.text = "₹${spent.toInt()} spent of ₹${limit.toInt()}"
        
        if (limit > 0) {
            val progress = ((spent / limit) * 100).toInt()
            holder.binding.progressBudget.progress = progress
            
            if (progress >= 100) {
                holder.binding.progressBudget.setIndicatorColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.error_red)
                )
            } else {
                holder.binding.progressBudget.setIndicatorColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.accent_purple)
                )
            }
        } else {
            holder.binding.progressBudget.progress = 0
        }

        holder.itemView.setOnClickListener { onSetLimitClick(budget) }
    }

    override fun getItemCount(): Int = budgets.size
}