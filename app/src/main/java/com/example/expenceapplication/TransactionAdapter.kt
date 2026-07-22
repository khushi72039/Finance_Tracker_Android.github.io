package com.example.expenceapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.expenceapplication.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(private val transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    class TransactionViewHolder(val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        holder.binding.tvDescription.text = transaction.description
        holder.binding.tvCategory.text = transaction.category
        holder.binding.tvDate.text = dateFormat.format(Date(transaction.timestamp))
        
        // Set category icon
        val iconRes = when (transaction.category) {
            "Food" -> android.R.drawable.ic_menu_today
            "Travel" -> android.R.drawable.ic_menu_directions
            "Shopping" -> android.R.drawable.ic_menu_save
            "Bills" -> android.R.drawable.ic_menu_agenda
            "Education" -> android.R.drawable.ic_menu_info_details
            "Healthcare" -> android.R.drawable.ic_menu_add
            "Entertainment" -> android.R.drawable.ic_menu_slideshow
            "Investment" -> android.R.drawable.ic_menu_gallery
            "Salary" -> android.R.drawable.ic_menu_send
            "Gift" -> android.R.drawable.ic_menu_myplaces
            else -> android.R.drawable.ic_menu_help
        }
        holder.binding.ivCategoryIcon.setImageResource(iconRes)
        
        if (transaction.type == "Income") {
            holder.binding.tvAmount.text = "+ ₹${transaction.amount}"
            holder.binding.tvAmount.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.success_green)
            )
        } else {
            holder.binding.tvAmount.text = "- ₹${transaction.amount}"
            holder.binding.tvAmount.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.error_red)
            )
        }
    }

    override fun getItemCount(): Int = transactions.size
}