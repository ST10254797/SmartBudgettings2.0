package com.st10254797.smartbudgetting20

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.google.firebase.Timestamp
import android.util.Log
import android.graphics.Color
import com.google.android.material.bottomnavigation.BottomNavigationView




class ExpenseActivity : AppCompatActivity() {

    private lateinit var categorySpinner: Spinner
    private lateinit var amountEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var saveExpenseBtn: Button
    private lateinit var uploadImageBtn: Button
    private lateinit var returnBtn: Button
    private lateinit var expensesListView: ListView
    private lateinit var startDateEditText: EditText
    private lateinit var endDateEditText: EditText
    private lateinit var filterDateButton: Button
    private lateinit var clearFilterButton: Button
    private lateinit var budgetWarningTextView: TextView

    private var imageUrl: String? = null
    private var selectedCategoryId: String? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var categories = listOf<CategoryFirestore>()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Persist permission for future access
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            imageUrl = it.toString()
            Toast.makeText(this, "Image selected successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        // Initialize UI components
        categorySpinner = findViewById(R.id.categorySpinner)
        amountEditText = findViewById(R.id.amountEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        dateEditText = findViewById(R.id.dateEditText)
        saveExpenseBtn = findViewById(R.id.saveExpenseBtn)
        uploadImageBtn = findViewById(R.id.uploadImageBtn)
        // returnBtn = findViewById(R.id.returnBtn)
        expensesListView = findViewById(R.id.expensesListView)
        startDateEditText = findViewById(R.id.startDateEditText)
        endDateEditText = findViewById(R.id.endDateEditText)
        filterDateButton = findViewById(R.id.filterDateButton)
        clearFilterButton = findViewById(R.id.clearFilterButton)
        budgetWarningTextView = findViewById(R.id.budgetWarningTextView)

        // Bottom Navigation setup
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.Add_Expense
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.Add_Expense -> {
                    Toast.makeText(this, "You are already on Expenses", Toast.LENGTH_SHORT).show()
                }
                R.id.Back_Home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                R.id.goals -> {
                    startActivity(Intent(this, GoalSettingsActivity::class.java))
                }
                R.id.graph -> {
                    startActivity(Intent(this, CategoryGraphActivity::class.java))
                }
                R.id.categories -> {
                    startActivity(Intent(this, CategoryActivity::class.java))
                }
            }
            true
        }

        // Setup spinner
        setupCategorySpinner()

        // Set click listeners
        uploadImageBtn.setOnClickListener { pickImage() }
        saveExpenseBtn.setOnClickListener { saveExpense() }
        filterDateButton.setOnClickListener { filterExpensesByDate() }
        clearFilterButton.setOnClickListener { clearExpenseFilter() }
        // returnBtn?.setOnClickListener { finish() } // uncomment if returnBtn is used

        // Set date pickers
        dateEditText.setOnClickListener { showDatePicker(dateEditText) }
        startDateEditText.setOnClickListener { showDatePicker(startDateEditText) }
        endDateEditText.setOnClickListener { showDatePicker(endDateEditText) }
    }


    private fun setupCategorySpinner() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                val snapshot = db.collection("users")
                    .document(userId)
                    .collection("categories")
                    .get()
                    .await()

                categories = snapshot.documents.map { doc ->
                    CategoryFirestore(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unnamed"
                    )
                }
                val categoryNames = categories.map { it.name }

                withContext(Dispatchers.Main) {
                    val adapter = ArrayAdapter(
                        this@ExpenseActivity,
                        android.R.layout.simple_spinner_item,
                        categoryNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    categorySpinner.adapter = adapter

                    (categorySpinner.selectedView as? TextView)?.setTextColor(Color.WHITE)
                    categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View?, position: Int, id: Long
                        ) {
                            (view as? TextView)?.setTextColor(Color.WHITE)
                            selectedCategoryId = categories[position].id
                            loadExpensesForCategory(selectedCategoryId!!)
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {
                            selectedCategoryId = null
                            clearExpenseList()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadExpensesForCategory(categoryId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                // Fetch expenses
                val snapshot = db.collection("users")
                    .document(userId)
                    .collection("expenses")
                    .whereEqualTo("categoryId", categoryId)
                    .get()
                    .await()

                // Map to Expense objects as MutableList
                val expenses = snapshot.documents.mapNotNull { doc ->
                    try {
                        val timestamp = doc.getTimestamp("date")
                        val dateString = timestamp?.toDate()?.let {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it)
                        } ?: ""

                        Expense(
                            id = doc.id,
                            amount = doc.getDouble("amount") ?: 0.0,
                            description = doc.getString("description") ?: "",
                            date = dateString,
                            category = doc.getString("categoryId") ?: "",
                            imageUrl = doc.getString("imageUrl")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.toMutableList() // Convert to MutableList

                val total = expenses.sumOf { it.amount }

                // Fetch budget goal for this category
                val goalDoc = db.collection("goals")
                    .document(userId)
                    .get()
                    .await()

                val goal = if (goalDoc.exists()) {
                    try {
                        // Convert Double to Float for Goal class
                        Goal(
                            userId = userId,
                            minGoal = (goalDoc.getDouble("minGoal") ?: 0.0).toFloat(),
                            maxGoal = (goalDoc.getDouble("maxGoal") ?: 0.0).toFloat()
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    // Update ListView with MutableList
                    expensesListView.adapter = ExpenseAdapter(
                        this@ExpenseActivity,
                        expenses,
                        onDeleteClick = { expense -> deleteExpense(expense) }
                    )

                    // Update budget warning
                    budgetWarningTextView.visibility = View.VISIBLE
                    when {
                        total == 0.0 -> {
                            budgetWarningTextView.text = "No expenses recorded"
                            budgetWarningTextView.setTextColor(Color.GRAY)
                        }
                        goal != null && total > goal.maxGoal -> {
                            budgetWarningTextView.text = "⚠️ You've exceeded your max budget of R${"%.2f".format(goal.maxGoal)}!"
                            budgetWarningTextView.setTextColor(Color.RED)
                        }
                        goal != null && total < goal.minGoal -> {
                            budgetWarningTextView.text = "💡 You're below your min budget goal of R${"%.2f".format(goal.minGoal)}."
                            budgetWarningTextView.setTextColor(Color.BLUE)
                        }
                        goal != null -> {
                            budgetWarningTextView.text = "✅ You're within your budget (R${"%.2f".format(total)}/${"%.2f".format(goal.maxGoal)})"
                            budgetWarningTextView.setTextColor(Color.GREEN)
                        }
                        else -> {
                            budgetWarningTextView.text = "Total expenses: R${"%.2f".format(total)}"
                            budgetWarningTextView.setTextColor(Color.BLACK)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ExpenseActivity,
                        "Failed to load data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("ExpenseActivity", "Error loading expenses/goal", e)
                }
            }
        }
    }
// updated code
    private fun clearExpenseList() {
        expensesListView.adapter = null
        budgetWarningTextView.visibility = android.view.View.GONE
    }

    private fun pickImage() {
        imagePickerLauncher.launch(arrayOf("image/*"))
    }

    private fun saveExpense() {
        val amount = amountEditText.text.toString().toDoubleOrNull()
        val description = descriptionEditText.text.toString().trim()
        val dateString = dateEditText.text.toString().trim()
        val categoryId = selectedCategoryId

        if (amount == null || description.isEmpty() || dateString.isEmpty() || categoryId.isNullOrBlank()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date: Date = try {
            sdf.parse(dateString) ?: throw IllegalArgumentException("Invalid date")
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid date format. Use yyyy-MM-dd.", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert Date to Firestore Timestamp
        val timestamp = Timestamp(date)

        val expenseData = hashMapOf(
            "amount" to amount,
            "description" to description,
            "date" to timestamp,  // <-- Use Timestamp here
            "categoryId" to categoryId,
            "imageUrl" to imageUrl,
            "userId" to userId
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.collection("users")
                    .document(userId)
                    .collection("expenses")
                    .add(expenseData)
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "Expense added successfully", Toast.LENGTH_SHORT).show()
                    unlockBadge(this@ExpenseActivity,userId, "expense_saver")  // Your badge ID here
                    loadExpensesForCategory(categoryId)
                    clearInputFields()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "Failed to save expense: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun clearInputFields() {
        amountEditText.text.clear()
        descriptionEditText.text.clear()
        dateEditText.text.clear()
        imageUrl = null
    }

    private fun deleteExpense(expense: Expense) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.collection("users")
                    .document(userId)
                    .collection("expenses")
                    .document(expense.id)
                    .delete()
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "Expense deleted", Toast.LENGTH_SHORT).show()
                    loadExpensesForCategory(expense.category)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "Failed to delete expense: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterExpensesByDate() {
        val startDate = startDateEditText.text.toString().trim()
        val endDate = endDateEditText.text.toString().trim()
        val categoryId = selectedCategoryId

        if (startDate.isEmpty() || endDate.isEmpty() || categoryId.isNullOrBlank()) {
            Toast.makeText(this, "Please enter start and end dates and select a category", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDateParsed = try { dateFormat.parse(startDate) } catch (e: ParseException) { null }
        val endDateParsed = try { dateFormat.parse(endDate) } catch (e: ParseException) { null }

        if (startDateParsed == null || endDateParsed == null) {
            Toast.makeText(this, "Invalid date format. Use yyyy-MM-dd.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                val snapshot = db.collection("users")
                    .document(userId)
                    .collection("expenses")
                    .whereEqualTo("categoryId", categoryId)
                    .whereGreaterThanOrEqualTo("date", Timestamp(startDateParsed))
                    .whereLessThanOrEqualTo("date", Timestamp(endDateParsed))
                    .get()
                    .await()

                val filteredExpenses = snapshot.documents.map { doc ->
                    val timestamp = doc.getTimestamp("date")
                    val dateStr = timestamp?.toDate()?.let { dateFormat.format(it) } ?: ""

                    Expense(
                        id = doc.id,
                        amount = doc.getDouble("amount") ?: 0.0,
                        description = doc.getString("description") ?: "",
                        date = dateStr,
                        category = doc.getString("categoryId") ?: "",
                        imageUrl = doc.getString("imageUrl")
                    )
                }.toMutableList()

                withContext(Dispatchers.Main) {
                    expensesListView.adapter = ExpenseAdapter(
                        this@ExpenseActivity,
                        filteredExpenses,
                        onDeleteClick = { expense -> deleteExpense(expense) }
                    )

                    // <-- Add this line here to update totals
                    updateCategoryTotals(filteredExpenses)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseActivity, "Filter error: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("FirestoreError", "Filtering failed", e)
                }
            }
        }
    }
    private fun updateCategoryTotals(expenses: List<Expense>) {
        val categoryTotalsLayout = findViewById<LinearLayout>(R.id.categoryTotalsLayout)
        categoryTotalsLayout.removeAllViews()

        // Create ID-to-Name mapping
        val categoryMap = categories.associate { it.id to it.name }

        // Calculate totals by category
        val totalsByCategory = expenses.groupBy { it.category }  // This groups by category ID
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        totalsByCategory.forEach { (categoryId, total) ->
            // Get category name from ID
            val categoryName = categoryMap[categoryId] ?: "Unknown Category"

            val textView = TextView(this)
            textView.text = "$categoryName: R%.2f".format(total)
            textView.setTextColor(Color.WHITE)
            textView.textSize = 16f
            categoryTotalsLayout.addView(textView)
        }
    }

    private fun clearExpenseFilter() {
        startDateEditText.text.clear()
        endDateEditText.text.clear()

        // Clear totals display
        findViewById<LinearLayout>(R.id.categoryTotalsLayout).removeAllViews()
        selectedCategoryId?.let { loadExpensesForCategory(it) }
    }

    private fun showDatePicker(targetEditText: EditText) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                targetEditText.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }
}

// Data class for Expense - used consistently everywhere
data class ExpenseFirestore(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: String = "",
    val categoryId: String = "",
    val imageUrl: String? = null
)

// Data class for Category
data class CategoryFirestore(
    val id: String = "",
    val name: String = ""
)

//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]