package com.st10254797.smartbudgetting20

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.github.mikephil.charting.formatter.PercentFormatter
import android.text.Html
import android.text.method.LinkMovementMethod
import com.github.mikephil.charting.components.Legend
import android.widget.Button
import android.os.Environment
import android.widget.Toast
import java.io.File
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.FileOutputStream
import android.graphics.Typeface
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.graphics.Color
import com.github.mikephil.charting.animation.Easing
import com.google.firebase.Timestamp
import android.util.Log
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ViewPortHandler
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList


class BalanceOverviewActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var progressBar: ProgressBar
    private lateinit var summaryTextView: TextView
    private lateinit var budgetStatusTextView: TextView
    private lateinit var generateReportButton: Button

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance_overview)

        pieChart = findViewById(R.id.pieChart)
        progressBar = findViewById(R.id.budgetProgressBar)
        summaryTextView = findViewById(R.id.summaryTextView)
        budgetStatusTextView = findViewById(R.id.budgetStatusTextView)
        generateReportButton = findViewById(R.id.generateReportButton)
        generateReportButton.setOnClickListener {
            lifecycleScope.launch {
                generateAndSavePdfReport()
            }
        }

        loadData()
    }

    private fun loadData() {
        Log.i("LOAD_DATA", "loadData() called")
        val userId = auth.currentUser?.uid ?: return
        Log.d("USER_ID", "Current userId: $userId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch all necessary data
                val expensesSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("expenses")
                    .get()
                    .await()

                val categoriesSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("categories")
                    .get()
                    .await()

                val goalSnapshot = firestore.collection("goals")
                    .document(userId)
                    .get()
                    .await()

                // Parse expenses
                val expenses = expensesSnapshot.documents.mapNotNull { doc ->
                    try {
                        val dateField = doc.get("date")
                        val dateString = when (dateField) {
                            is com.google.firebase.Timestamp -> {
                                val sdf = SimpleDateFormat("d MMMM yyyy 'at' HH:mm:ss 'UTC'X", Locale.getDefault())
                                sdf.timeZone = TimeZone.getTimeZone("UTC")
                                sdf.format(dateField.toDate())
                            }
                            is String -> dateField
                            else -> null
                        }

                        Expense(
                            id = doc.id,
                            amount = doc.getDouble("amount") ?: 0.0,
                            description = doc.getString("description") ?: "",
                            date = dateString ?: "",
                            category = doc.getString("categoryId")?.trim() ?: "", // Ensure trimmed category ID
                            imageUrl = doc.getString("imageUrl"),
                            userId = doc.getString("userId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e("LOAD_DATA", "Failed to parse expense: ${e.message}")
                        null
                    }
                }

                // Parse categories with additional debug logging
                val categories = categoriesSnapshot.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Category::class.java)
                            ?.copy(id = doc.id.trim())
                            ?.also { cat ->
                                if (cat.name.isNullOrEmpty()) {
                                    Log.w("CATEGORY_WARNING", "Empty name for ID: '${doc.id}'")
                                }
                                Log.d("CATEGORY_DEBUG", "Loaded: ${cat.id} -> '${cat.name}'")
                            }
                    }.getOrElse { e ->
                        Log.e("CATEGORY_PARSE", "Error parsing ${doc.id}", e)
                        null
                    }
                }

                Log.d("CATEGORY_CHECK", "All category IDs: ${categoriesSnapshot.documents.map { it.id.trim() }}")
                // Create category map with trimmed keys
                val categoryMap = categories.associateBy { it.id.trim() }.also { map ->
                    Log.d("CATEGORY_MAP", "Category map contents:")
                    map.forEach { (id, category) ->
                        Log.d("CATEGORY_MAP", "  $id -> ${category.name}")
                    }
                }

                val goal = goalSnapshot.toObject(Goal::class.java)

                // Date handling for filtering current month expenses
                val sdf = SimpleDateFormat("d MMMM yyyy 'at' HH:mm:ss 'UTC'X", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")

                val thisMonthCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val thisMonth = thisMonthCal.time

                Log.d("DATE_FILTER", "Filtering expenses after: $thisMonth")

                // Filter expenses for current month with date parsing debug
                val thisMonthExpenses = expenses.filter { expense ->
                    expense.date?.let { dateStr ->
                        try {
                            val date = sdf.parse(dateStr)
                            val isCurrentMonth = date != null && !date.before(thisMonth)
                            if (!isCurrentMonth) {
                                Log.d("DATE_FILTER", "Excluding expense from ${date} (before cutoff)")
                            }
                            isCurrentMonth
                        } catch (e: Exception) {
                            Log.e("DATE_PARSE", "Failed to parse: $dateStr", e)
                            false
                        }
                    } ?: false
                }

                Log.d("EXPENSE_DATA", "Total expenses: ${expenses.size}, This month: ${thisMonthExpenses.size}")

                // Calculate category sums with enhanced debug logging
                val categorySums = thisMonthExpenses.groupBy { expense ->
                    val catId = expense.category.trim()
                    val categoryName = categoryMap[catId]?.name ?: run {
                        Log.w("MISSING_CATEGORY", "No category found for: '$catId'")
                        "Unknown"
                    }
                    Log.d("CATEGORY_MATCH", "Expense ${expense.id} - Category ID: '$catId' -> Name: '$categoryName'")
                    categoryName
                }.mapValues { (_, expenseList) ->
                    expenseList.sumOf { it.amount }.also { sum ->
                        Log.d("CATEGORY_SUM", "Category sum: ${expenseList.firstOrNull()?.category} = $sum")
                    }
                }

                Log.d("FINAL_DATA", "Category sums: $categorySums")
                Log.d("FINAL_DATA", "Total spent: ${thisMonthExpenses.sumOf { it.amount }}")
                Log.d("FINAL_DATA", "Goal: $goal")

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    updatePieChart(categorySums)
                    updateProgressBar(thisMonthExpenses.sumOf { it.amount }, goal)
                    updateSummary(thisMonthExpenses.sumOf { it.amount }, goal)
                    updateBudgetStatus(thisMonthExpenses.sumOf { it.amount }, goal)
                }
            } catch (e: Exception) {
                Log.e("LOAD_ERROR", "Error loading data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BalanceOverviewActivity,
                        "Error loading data: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun updatePieChart(dataMap: Map<String, Double>) {
        val filteredData = dataMap.filterValues { it > 0 }
        if (filteredData.isEmpty()) {
            pieChart.apply {
                clear()
                setNoDataText("No spending data available")
                setNoDataTextColor(Color.GRAY)
                setNoDataTextTypeface(Typeface.DEFAULT_BOLD)
                invalidate()
            }
            return
        }

        val maxCategories = 5
        val sortedEntries = filteredData.entries.sortedByDescending { it.value }
        val totalAmount = sortedEntries.sumOf { it.value }

        val entries = mutableListOf<PieEntry>()

        if (sortedEntries.size <= maxCategories) {
            sortedEntries.forEach { (category, amount) ->
                val percentage = (amount / totalAmount * 100).toFloat()
                val displayName = if (category.length > 15) "${category.take(12)}..." else category
                entries.add(PieEntry(percentage, displayName))
            }
        } else {
            val topCategories = sortedEntries.take(maxCategories - 1)
            val others = sortedEntries.drop(maxCategories - 1)
            val othersSum = others.sumOf { it.value }
            val othersPercentage = (othersSum / totalAmount * 100).toFloat()

            topCategories.forEach { (category, amount) ->
                val percentage = (amount / totalAmount * 100).toFloat()
                val displayName = if (category.length > 15) "${category.take(12)}..." else category
                entries.add(PieEntry(percentage, displayName))
            }

            entries.add(PieEntry(othersPercentage, "Others"))
        }

        val colors = listOf(
            Color.parseColor("#F44336"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FFEB3B"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#009688"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#795548"),
            Color.parseColor("#3F51B5"),
            Color.parseColor("#9E9E9E")
        )

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
            selectionShift = 5f
            setDrawValues(true)
            valueFormatter = PercentFormatter(pieChart)
            yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
            valueLinePart1Length = 0.4f
            valueLinePart2Length = 0.4f
            valueLineColor = Color.WHITE
        }

        pieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            setDrawEntryLabels(true) // <-- Show category names
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)

            isDrawHoleEnabled = true
            holeRadius = 45f
            setTransparentCircleRadius(50f)
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.argb(100, 0, 0, 0))

            setCenterText("Spending\nBreakdown")
            setCenterTextSize(16f)
            setCenterTextColor(Color.DKGRAY)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)

            rotationAngle = 0f
            isRotationEnabled = true
            setUsePercentValues(true)
            setExtraOffsets(24f, 24f, 24f, 24f)

            legend.apply {
                isEnabled = true
                textSize = 12f
                textColor = Color.DKGRAY
                form = Legend.LegendForm.CIRCLE
                formSize = 12f
                formToTextSpace = 8f
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                isWordWrapEnabled = true
                yOffset = 16f
                xOffset = 0f
            }

            animateY(1000, Easing.EaseInOutQuad)
            highlightValues(null)
            invalidate()
        }
    }



    private fun updateProgressBar(totalSpent: Double, goal: Goal?) {
        if (goal != null && goal.maxGoal > 0) {
            val progress = ((totalSpent / goal.maxGoal) * 100).coerceAtMost(100.0)
            progressBar.progress = progress.toInt()

            val isUnderBudget = totalSpent <= goal.maxGoal
            val color = if (isUnderBudget) {
                ContextCompat.getColor(this, R.color.green)  // or R.color.material_green_500
            } else {
                ContextCompat.getColor(this, R.color.material_red_500)
            }

            progressBar.progressTintList = ColorStateList.valueOf(color)
        } else {
            progressBar.progress = 0
            progressBar.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this , R.color.material_grey_500)
            )
        }
    }


    private fun updateBudgetStatus(totalSpent: Double, goal: Goal?) {
        if (goal == null || goal.maxGoal <= 0) {
            budgetStatusTextView.text = "No budget goal set"
            budgetStatusTextView.setTextColor(Color.GRAY)
            return
        }

        when {
            totalSpent > goal.maxGoal -> {
                budgetStatusTextView.text = "⚠️ You are OVER budget!"
                budgetStatusTextView.setTextColor(Color.RED)
            }
            totalSpent < goal.minGoal -> {
                budgetStatusTextView.text = "✅ You are UNDER budget!"
                budgetStatusTextView.setTextColor(Color.GREEN)
            }
            else -> {
                budgetStatusTextView.text = "⚠️ You are WITHIN budget range."
                budgetStatusTextView.setTextColor(Color.parseColor("#FFA500"))
            }
        }
    }

    private fun updateSummary(totalSpent: Double, goal: Goal?) {
        val daysLeft = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH) -
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val htmlSummary = buildString {
            append("💸 <b>Spent This Month:</b> R%.2f<br/><br/>".format(totalSpent))
            if (goal != null) {
                append("📉 <b>Min Goal:</b> R${goal.minGoal}<br/>")
                append("📈 <b>Max Goal:</b> R${goal.maxGoal}<br/><br/>")
            }
            append("📅 <b>Days Left:</b> $daysLeft")
        }

        summaryTextView.text = Html.fromHtml(htmlSummary, Html.FROM_HTML_MODE_LEGACY)
        summaryTextView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun generateAndSavePdfReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@launch

            try {
                val expensesSnapshot = firestore.collection("expenses")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val categoriesSnapshot = firestore.collection("categories")
                    .get()
                    .await()

                val goalSnapshot = firestore.collection("goals")
                    .document(userId)
                    .get()
                    .await()

                val expenses = expensesSnapshot.toObjects(Expense::class.java)
                val categories = categoriesSnapshot.toObjects(Category::class.java)
                val goal = goalSnapshot.toObject(Goal::class.java)

                val categoryMap = categories.associateBy { it.id }

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val thisMonth = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }.time

                val thisMonthExpenses = expenses.filter {
                    val expenseDate = sdf.parse(it.date)
                    expenseDate != null && !expenseDate.before(thisMonth)
                }

                val totalSpent = thisMonthExpenses.sumOf { it.amount }

                val categoryTotals = thisMonthExpenses.groupBy { expense ->
                    categoryMap[expense.category]?.name ?: "Unknown"
                }.mapValues { (_, v) -> v.sumOf { it.amount } }

                val daysLeft = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH) -
                        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

                withContext(Dispatchers.Main) {
                    generatePdfReport(categoryTotals, totalSpent, goal, daysLeft)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BalanceOverviewActivity, "Error generating report: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generatePdfReport(
        categoryTotals: Map<String, Double>,
        totalSpent: Double,
        goal: Goal?,
        daysLeft: Int
    ) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }

        val textPaint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }

        var y = 40 // starting y position for drawing text

        // Title
        canvas.drawText("Monthly Spending Report", 10f, y.toFloat(), titlePaint)
        y += 30

        // Total Spent
        canvas.drawText("Total Spent: R%.2f".format(totalSpent), 10f, y.toFloat(), textPaint)
        y += 25

        // Budget Goals if present
        if (goal != null) {
            canvas.drawText("Min Goal: R${goal.minGoal}", 10f, y.toFloat(), textPaint)
            y += 20
            canvas.drawText("Max Goal: R${goal.maxGoal}", 10f, y.toFloat(), textPaint)
            y += 25
        }

        // Days left
        canvas.drawText("Days Left: $daysLeft", 10f, y.toFloat(), textPaint)
        y += 30

        // Category breakdown
        canvas.drawText("Category Breakdown:", 10f, y.toFloat(), titlePaint)
        y += 25

        for ((category, amount) in categoryTotals) {
            if (y > 580) { // Check if we have space for more text
                break
            }
            val line = "$category: R%.2f".format(amount)
            canvas.drawText(line, 10f, y.toFloat(), textPaint)
            y += 20
        }

        document.finishPage(page)

        // Save the PDF to external storage (Documents folder)
        val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SpendingReport.pdf")
        try {
            FileOutputStream(pdfFile).use { outputStream ->
                document.writeTo(outputStream)
            }
            Toast.makeText(this, "PDF saved to ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            document.close()
        }
    }

}
