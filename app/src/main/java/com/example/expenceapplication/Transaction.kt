package com.example.expenceapplication

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Transaction(
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val paymentMethod: String = "",
    val type: String = "Expense",
    val timestamp: Long = 0
)