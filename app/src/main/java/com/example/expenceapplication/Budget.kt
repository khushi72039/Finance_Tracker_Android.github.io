package com.example.expenceapplication

data class Budget(
    val category: String = "",
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0
)