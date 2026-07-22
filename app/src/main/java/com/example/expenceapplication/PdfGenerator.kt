package com.example.expenceapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    fun generateMonthlyStatement(
        context: Context,
        outputStream: OutputStream,
        userName: String,
        userEmail: String,
        transactions: List<Transaction>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
            color = Color.BLACK
        }

        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.GRAY
        }

        val textPaint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val currentMonth = monthFormat.format(Date())

        var y = 50f

        // Title
        canvas.drawText("Smart Wallet Monthly Statement", 50f, y, titlePaint)
        y += 30f

        // User Info
        canvas.drawText("Name: $userName", 50f, y, textPaint)
        y += 20f
        canvas.drawText("Email: $userEmail", 50f, y, textPaint)
        y += 20f
        canvas.drawText("Period: $currentMonth", 50f, y, textPaint)
        y += 40f

        // Table Header
        canvas.drawText("Date", 50f, y, headerPaint)
        canvas.drawText("Category", 150f, y, headerPaint)
        canvas.drawText("Description", 300f, y, headerPaint)
        canvas.drawText("Amount", 500f, y, headerPaint)
        y += 10f
        canvas.drawLine(50f, y, 550f, y, paint)
        y += 25f

        // Transactions
        var totalIncome = 0.0
        var totalExpense = 0.0

        for (tx in transactions) {
            if (y > 800) { // Simple pagination check (basic)
                break 
            }

            canvas.drawText(dateFormat.format(Date(tx.timestamp)), 50f, y, textPaint)
            canvas.drawText(tx.category, 150f, y, textPaint)
            
            // Truncate description if too long
            val desc = if (tx.description.length > 20) tx.description.substring(0, 17) + "..." else tx.description
            canvas.drawText(desc, 300f, y, textPaint)

            val amountStr = if (tx.type == "Income") {
                totalIncome += tx.amount
                "+ ₹${tx.amount}"
            } else {
                totalExpense += tx.amount
                "- ₹${tx.amount}"
            }
            canvas.drawText(amountStr, 500f, y, textPaint)
            
            y += 20f
        }

        y += 20f
        canvas.drawLine(50f, y, 550f, y, paint)
        y += 30f

        // Summary
        canvas.drawText("Summary:", 50f, y, headerPaint)
        y += 20f
        canvas.drawText("Total Income: ₹$totalIncome", 50f, y, textPaint)
        y += 20f
        canvas.drawText("Total Expense: ₹$totalExpense", 50f, y, textPaint)
        y += 20f
        val savings = totalIncome - totalExpense
        canvas.drawText("Net Savings: ₹$savings", 50f, y, titlePaint.apply { textSize = 14f })

        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}