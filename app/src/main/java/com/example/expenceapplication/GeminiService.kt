package com.example.expenceapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.util.Log

object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // USING STABLE V1 AND FLASH MODEL - Most reliable for new AQ keys
    private const val MODEL = "gemini-3.6-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1/models/$MODEL:generateContent"

    fun buildDashboardInsightPrompt(
        balance: Double,
        monthlySpent: Double,
        budgetLimit: Double,
        topCategories: List<Pair<String, Double>>
    ): String {
        val categoriesStr = topCategories.joinToString { "${it.first}: ₹${it.second}" }
        return "Wallet: ₹$balance. Spent: ₹$monthlySpent. Limit: ₹$budgetLimit. Categories: $categoriesStr. Give a very short financial tip."
    }

    fun buildCoachPrompt(transactions: List<Transaction>, budgetLimit: Double, userQuestion: String): String {
        val transactionsStr = transactions.joinToString("\n") {
            "- ${it.type}: ₹${it.amount} on ${it.description} (${it.category})"
        }
        return "Limit: ₹$budgetLimit. History:\n$transactionsStr\n\nQuestion: $userQuestion"
    }

    suspend fun generateText(prompt: String, systemInstruction: String = ""): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = Constants.GEMINI_API_KEY.trim()
                
                // Construct the JSON body
                val finalPrompt = if (systemInstruction.isNotEmpty()) "$systemInstruction\n\n$prompt" else prompt
                
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", finalPrompt)
                                })
                            })
                        })
                    })
                }

                val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
                
                // Using API Key in the URL for maximum compatibility
                val urlWithKey = "$BASE_URL?key=$apiKey"

                val request = Request.Builder()
                    .url(urlWithKey)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseString = response.body?.string() ?: ""
                    
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("API Error ${response.code}: $responseString"))
                    }

                    val jsonResponse = JSONObject(responseString)
                    val text = jsonResponse
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    Result.success(text.trim())
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}