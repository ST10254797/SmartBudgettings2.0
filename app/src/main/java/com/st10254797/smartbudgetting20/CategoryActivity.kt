package com.st10254797.smartbudgetting20

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.st10254797.smartbudgetting20.databinding.ActivityCategoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.st10254797.smartbudgetting20.unlockBadge


@Suppress("DEPRECATION")
class CategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupClickListeners()
        updateCategoryList()

        binding.buttonRefresh.setOnClickListener {
            updateCategoryList()
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
        }
        binding.bottomNavigationView.selectedItemId = R.id.categories
        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.Add_Expense -> startActivity(Intent(this, ExpenseActivity::class.java))
                R.id.Back_Home -> startActivity(Intent(this, MainActivity::class.java))
                R.id.goals -> startActivity(Intent(this, GoalSettingsActivity::class.java))
                R.id.graph -> startActivity(Intent(this, CategoryGraphActivity::class.java))
                R.id.categories -> {
                    // Already on this screen
                    Toast.makeText(this, "You are already on Categories", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupClickListeners() {
        binding.buttonAddCategory.setOnClickListener { handleAddCategory() }
        binding.buttonDeleteCategory.setOnClickListener { handleDeleteCategory() }
//        binding.buttonBackToHome.setOnClickListener { navigateToMainActivity() }
//        binding.buttonGoToExpense.setOnClickListener { navigateToExpenseActivity() }
    }

    private fun handleAddCategory() {
        val categoryName = binding.editTextCategoryName.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (categoryName.isEmpty()) {
            showToast("Please enter a category name")
            return
        }
        if (userId == null) {
            showToast("User not logged in")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val existingCategory = getCategoryByNameAndUser(categoryName, userId)

                if (existingCategory == null) {
                    val newCategory = hashMapOf(
                        "name" to categoryName,
                        "userId" to userId
                    )
                    firestore.collection("users")
                        .document(userId)
                        .collection("categories")
                        .add(newCategory)
                        .await()

                    withContext(Dispatchers.Main) {
                        unlockBadge(this@CategoryActivity, userId, "category_creator")
                        showToast("$categoryName added")
                        binding.editTextCategoryName.text?.clear()
                        updateCategoryList()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("$categoryName already exists")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to add category: ${e.message}")
                }
            }
        }
    }

    private fun handleDeleteCategory() {
        val categoryName = binding.editTextCategoryName.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (categoryName.isEmpty()) {
            showToast("Please enter a category name to delete")
            return
        }
        if (userId == null) {
            showToast("User not logged in")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val categoryDoc = getCategoryDocumentByNameAndUser(categoryName, userId)
                if (categoryDoc != null) {
                    // Delete category document under user's subcollection
                    firestore.collection("users")
                        .document(userId)
                        .collection("categories")
                        .document(categoryDoc.id)
                        .delete()
                        .await()

                    // Optionally, delete all expenses linked to this category under user's expenses subcollection
                    deleteExpensesByCategory(categoryDoc.id, userId)

                    withContext(Dispatchers.Main) {
                        showToast("$categoryName deleted")
                        binding.editTextCategoryName.text?.clear()
                        updateCategoryList()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("$categoryName does not exist")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to delete category: ${e.message}")
                }
            }
        }
    }

    private suspend fun getCategoryByNameAndUser(name: String, userId: String): Map<String, Any>? {
        val querySnapshot = firestore.collection("users")
            .document(userId)
            .collection("categories")
            .whereEqualTo("name", name)
            .get()
            .await()

        return querySnapshot.documents.firstOrNull()?.data
    }

    private suspend fun getCategoryDocumentByNameAndUser(name: String, userId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("categories")
            .whereEqualTo("name", name)
            .get()
            .await()
            .documents
            .firstOrNull()

    private suspend fun deleteExpensesByCategory(categoryDocId: String, userId: String) {
        val expensesQuery = firestore.collection("users")
            .document(userId)
            .collection("expenses")
            .whereEqualTo("categoryId", categoryDocId)
            .get()
            .await()

        val batch = firestore.batch()
        for (doc in expensesQuery.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToExpenseActivity() {
        Intent(this, ExpenseActivity::class.java).apply {
            binding.editTextCategoryName.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
                putExtra("category_name", it)  // Passing the category name to ExpenseActivity
            }
            startActivityForResult(this, 1)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateCategoryList() {
        val userId = auth.currentUser?.uid ?: return
        binding.textViewCategories.text = "Loading..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get categories for user under user's subcollection
                val categoriesSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("categories")
                    .get()
                    .await()

                if (categoriesSnapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        binding.textViewCategories.text = "No categories found."
                    }
                    return@launch
                }

                // For each category, fetch expenses and calculate totals under user's expenses subcollection
                val categoriesWithExpenses = categoriesSnapshot.documents.map { categoryDoc ->
                    val categoryId = categoryDoc.id
                    val categoryName = categoryDoc.getString("name") ?: "Unknown"

                    val expensesSnapshot = firestore.collection("users")
                        .document(userId)
                        .collection("expenses")
                        .whereEqualTo("categoryId", categoryId)
                        .get()
                        .await()

                    val expenses = expensesSnapshot.documents.map { expenseDoc ->
                        val timestamp = expenseDoc.getTimestamp("date")
                        val dateString = timestamp?.toDate()?.let {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(it)
                        } ?: ""

                        Expense(
                            id = expenseDoc.id,
                            category = categoryId,
                            userId = userId,
                            amount = expenseDoc.getDouble("amount") ?: 0.0,
                            description = expenseDoc.getString("description") ?: "",
                            date = dateString
                        )
                    }
                    val total = expenses.sumOf { it.amount }

                    CategoryWithExpenses(
                        Category(name = categoryName, userId = userId),
                        expenses,
                        total
                    )
                }

                withContext(Dispatchers.Main) {
                    val displayText = buildString {
                        categoriesWithExpenses.forEach { (category, expenses, total) ->
                            append("${category.name} (Total: R${"%.2f".format(total)})\n")
                            if (expenses.isEmpty()) {
                                append("  No expenses\n")
                            } else {
                                expenses.forEach { expense ->
                                    append("  • R${"%.2f".format(expense.amount)} - ${expense.description}\n")
                                }
                            }
                            append("\n")
                        }
                    }
                    binding.textViewCategories.text = displayText
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error loading categories: ${e.message}")
                    binding.textViewCategories.text = "Error loading data"
                }
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK) {
            val expenseAdded = data?.getBooleanExtra("expense_added", false) == true
            if (expenseAdded) {
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(500)  // Wait 0.5 second
                    updateCategoryList()
                }
            }
        }
    }

    private data class CategoryWithExpenses(
        val category: Category,
        val expenses: List<Expense>,
        val total: Double
    )
}
//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]