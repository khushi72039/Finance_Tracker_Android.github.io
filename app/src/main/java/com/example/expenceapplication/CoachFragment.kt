package com.example.expenceapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.expenceapplication.databinding.FragmentCoachBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CoachFragment : Fragment() {
    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnAsk.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                getAiAdvice(question)
                binding.etQuestion.text?.clear()
            }
        }
    }

    private fun getAiAdvice(userQuestion: String) {
        val userId = auth.currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnAsk.isEnabled = false
        binding.tvCoachTip.text = "Thinking..."

        lifecycleScope.launch {
            try {
                // Fetch recent transactions
                val transactionsSnapshot = db.collection("users").document(userId)
                    .collection("transactions")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(15)
                    .get()
                    .await()

                // Fetch budget limit
                val budgetDoc = db.collection("users").document(userId)
                    .collection("budgets")
                    .document("global")
                    .get()
                    .await()

                val transactions = transactionsSnapshot.toObjects(Transaction::class.java)
                val budgetLimit = budgetDoc.getDouble("limit") ?: 5000.0

                // Build prompt and call updated Gemini Service
                val prompt = GeminiService.buildCoachPrompt(transactions, budgetLimit, userQuestion)

                val result = GeminiService.generateText(
                    prompt = prompt,
                    systemInstruction = "You are a professional financial coach. Provide concise advice based on user spending."
                )

                if (_binding != null) {
                    binding.tvCoachTip.text = result.getOrElse { error ->
                        "Coach Error: ${error.message}. \n\nTip: If you use an AQ key, ensure you enabled 'Generative Language API' in Google Cloud Console."
                    }
                }
            } catch (e: Exception) {
                binding.tvCoachTip.text = "Error: ${e.message ?: "Please check your internet connection."}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnAsk.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}