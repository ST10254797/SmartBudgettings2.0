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
import android.content.Context



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

                val thisMonthCal = Calendar.getInstance() // no timezone specified, uses device local time
                thisMonthCal.set(Calendar.DAY_OF_MONTH, 1)
                thisMonthCal.set(Calendar.HOUR_OF_DAY, 0)
                thisMonthCal.set(Calendar.MINUTE, 0)
                thisMonthCal.set(Calendar.SECOND, 0)
                thisMonthCal.set(Calendar.MILLISECOND, 0)

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

                    // Unlock the summary viewer badge here
                    unlockBadge(this@BalanceOverviewActivity, userId, "summary_viewer")

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

    private fun isBadgeUnlocked(badgeId: String): Boolean {
        val prefs = getSharedPreferences("badges_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(badgeId, false)
    }

    private fun markBadgeUnlocked(badgeId: String) {
        val prefs = getSharedPreferences("badges_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(badgeId, true).apply()
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
            setCenterTextColor(Color.WHITE)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)

            rotationAngle = 0f
            isRotationEnabled = true
            setUsePercentValues(true)
            setExtraOffsets(24f, 24f, 24f, 24f)

            legend.apply {
                isEnabled = true
                textSize = 14f
                textColor = Color.WHITE
                form = Legend.LegendForm.CIRCLE
                formSize = 12f
                formToTextSpace = 8f
                xEntrySpace = 20f        // Horizontal space between legend items
                yEntrySpace = 12f        // Vertical space between lines when wrapped
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                isWordWrapEnabled = true
                yOffset = 20f            // Distance from chart to legend
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
                // Fetch data with consistent collection paths (matching loadData())
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

                // Parse expenses with proper date handling
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
                            category = doc.getString("categoryId")?.trim() ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            userId = doc.getString("userId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e("PDF_PARSE", "Failed to parse expense: ${e.message}")
                        null
                    }
                }

                // Parse categories with trimmed IDs
                val categories = categoriesSnapshot.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Category::class.java)?.copy(id = doc.id.trim())
                    }.getOrElse { e ->
                        Log.e("PDF_PARSE", "Error parsing category ${doc.id}", e)
                        null
                    }
                }

                val goal = goalSnapshot.toObject(Goal::class.java)

                // Date handling matching loadData()
                val sdf = SimpleDateFormat("d MMMM yyyy 'at' HH:mm:ss 'UTC'X", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")

                val thisMonthCal = Calendar.getInstance() // no timezone specified, uses device local time
                thisMonthCal.set(Calendar.DAY_OF_MONTH, 1)
                thisMonthCal.set(Calendar.HOUR_OF_DAY, 0)
                thisMonthCal.set(Calendar.MINUTE, 0)
                thisMonthCal.set(Calendar.SECOND, 0)
                thisMonthCal.set(Calendar.MILLISECOND, 0)

                val thisMonth = thisMonthCal.time

                // Filter expenses for current month
                val thisMonthExpenses = expenses.filter { expense ->
                    expense.date?.let { dateStr ->
                        try {
                            val date = sdf.parse(dateStr)
                            date != null && !date.before(thisMonth)
                        } catch (e: Exception) {
                            Log.e("PDF_DATE", "Failed to parse date: $dateStr", e)
                            false
                        }
                    } ?: false
                }

                // Calculate totals with debug logging
                val totalSpent = thisMonthExpenses.sumOf { it.amount }
                val categoryMap = categories.associateBy { it.id.trim() }

                val categoryTotals = thisMonthExpenses.groupBy { expense ->
                    val catId = expense.category.trim()
                    categoryMap[catId]?.name ?: run {
                        Log.w("PDF_CATEGORY", "No category found for: '$catId'")
                        "Unknown"
                    }
                }.mapValues { (_, v) -> v.sumOf { it.amount } }

                val daysLeft = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH) -
                        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

                Log.d("PDF_DATA", "Total spent: $totalSpent")
                Log.d("PDF_DATA", "Category totals: $categoryTotals")
                Log.d("PDF_DATA", "Days left: $daysLeft")

                withContext(Dispatchers.Main) {
                    generatePdfReport(categoryTotals, totalSpent, goal, daysLeft)
                }
            } catch (e: Exception) {
                Log.e("PDF_ERROR", "Error generating report", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BalanceOverviewActivity,
                        "Error generating report: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
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
        // Increased page size for better content fit
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Draw a border for debugging visible area
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.RED
            strokeWidth = 1f
        }
        canvas.drawRect(0f, 0f, 595f, 842f, borderPaint)

        val titlePaint = Paint().apply {
            textSize = 18f // Slightly larger title
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            textSize = 14f // Slightly larger text
            color = Color.BLACK
            isAntiAlias = true
        }

        val smallTextPaint = Paint().apply {
            textSize = 12f
            color = Color.DKGRAY
            isAntiAlias = true
        }

        var y = 50 // Increased starting y position

        // Title
        canvas.drawText("📊 Monthly Spending Report", 50f, y.toFloat(), titlePaint)
        y += 40

        // Report date
        val reportDate = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Report for $reportDate", 50f, y.toFloat(), smallTextPaint)
        y += 30

        // Total Spent
        canvas.drawText("Total Spent: R%.2f".format(totalSpent), 50f, y.toFloat(), textPaint)
        y += 30

        // Budget Goals if present
        if (goal != null) {
            canvas.drawText("Budget Goals:", 50f, y.toFloat(), textPaint)
            y += 25
            canvas.drawText("- Minimum: R${goal.minGoal}", 70f, y.toFloat(), textPaint)
            y += 20
            canvas.drawText("- Maximum: R${goal.maxGoal}", 70f, y.toFloat(), textPaint)
            y += 30
        }

        // Days left
        canvas.drawText("Days remaining in month: $daysLeft", 50f, y.toFloat(), textPaint)
        y += 40

        // Category breakdown header
        canvas.drawText("Category Breakdown:", 50f, y.toFloat(), titlePaint)
        y += 30

        // Category items
        for ((category, amount) in categoryTotals) {
            if (y > 800) { // Check if we're near bottom of page
                canvas.drawText("-- Continued on next page --", 50f, 820f, smallTextPaint)
                break
            }
            val line = "• $category: R%.2f".format(amount)
            canvas.drawText(line, 60f, y.toFloat(), textPaint)
            y += 25
        }

        document.finishPage(page)

        // Save the PDF
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val pdfFile = File(downloadsDir, "SpendingReport_${System.currentTimeMillis()}.pdf")

        try {
            FileOutputStream(pdfFile).use { outputStream ->
                document.writeTo(outputStream)
            }
            Toast.makeText(
                this,
                "✅ Report saved to: ${pdfFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("PDF_SAVE", "Failed to save PDF", e)
            Toast.makeText(
                this,
                "❌ Failed to save PDF: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            document.close()
        }
    }
}
