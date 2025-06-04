package com.st10254797.smartbudgetting20

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent



class CategoryGraphActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var btnRefreshGraph: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnClearFilter: androidx.appcompat.widget.AppCompatButton
    // private lateinit var backButton: androidx.appcompat.widget.AppCompatButton
    private lateinit var startDateEditText: EditText
    private lateinit var endDateEditText: EditText
    private lateinit var bottomNavigationView: com.google.android.material.bottomnavigation.BottomNavigationView

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var startDateString: String? = null
    private var endDateString: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_graph)

        barChart = findViewById(R.id.barChart)
        btnRefreshGraph = findViewById(R.id.btnRefreshGraph)
        btnClearFilter = findViewById(R.id.btnClearFilter)
        // backButton = findViewById(R.id.backButton)
        startDateEditText = findViewById(R.id.startDateEditText)
        endDateEditText = findViewById(R.id.endDateEditText)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)  // Make sure your layout has this ID

        setupChartAppearance()

        startDateEditText.setOnClickListener { pickDate(true) }
        endDateEditText.setOnClickListener { pickDate(false) }

        // backButton.setOnClickListener { finish() }  // Commented out per request
        btnRefreshGraph.setOnClickListener { loadGraphData() }
        btnClearFilter.setOnClickListener {
            startDateString = null
            endDateString = null
            startDateEditText.text.clear()
            endDateEditText.text.clear()
            Toast.makeText(this, "Filter cleared. Showing all data.", Toast.LENGTH_SHORT).show()
            loadGraphData()
        }

        // Setup bottom navigation item selected listener
        bottomNavigationView.selectedItemId = R.id.graph
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.Add_Expense -> {
                    startActivity(Intent(this, ExpenseActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.Back_Home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.goals -> {
                    startActivity(Intent(this, GoalSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.categories -> {
                    startActivity(Intent(this, CategoryActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.graph -> {
                    // Already on graph screen
                    Toast.makeText(this, "You are already on Graph", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        loadGraphData()
    }

    private fun setupChartAppearance() {
        barChart.apply {
            // Basic chart configuration
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setPinchZoom(false)
            setDrawGridBackground(false)

            // Legend settings
            legend.isEnabled = true
            legend.textColor = Color.WHITE

            // Chart padding
            setExtraOffsets(10f, 0f, 0f, 40f)  // Left, Top, Right, Bottom padding

            // X-axis configuration
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM  // Fixed typo from "BOITION"
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.WHITE
                setAvoidFirstLastClipping(true)
                labelRotationAngle = -45f
                setDrawAxisLine(true)  // Make X-axis line visible
                axisLineColor = Color.WHITE
                axisLineWidth = 1f
            }

            // Left Y-axis configuration (main axis)
            axisLeft.apply {
                isEnabled = true
                axisMinimum = 0f
                textColor = Color.WHITE
                setDrawZeroLine(false)
                setDrawAxisLine(true)
                axisLineColor = Color.WHITE  // Changed from RED to WHITE for production
                axisLineWidth = 2f  // Slightly thicker than X-axis
                setDrawGridLines(true)  // Enable horizontal grid lines
                gridColor = Color.WHITE  // Light gray grid lines
                gridLineWidth = 0.5f
            }

            // Right Y-axis configuration
            axisRight.isEnabled = false

            // Layout improvements
            clipChildren = false
            clipToPadding = false

            // Additional performance improvements
            setHardwareAccelerationEnabled(true)
        }
    }

    private fun pickDate(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val selectedDate = dateFormat.format(calendar.time)

                if (isStart) {
                    if (endDateString != null) {
                        val endDate = dateFormat.parse(endDateString!!)
                        if (endDate != null && calendar.time.after(endDate)) {
                            Toast.makeText(
                                this,
                                "Start date cannot be after end date",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@DatePickerDialog
                        }
                    }
                    startDateString = selectedDate
                    startDateEditText.setText(selectedDate)
                } else {
                    if (startDateString != null) {
                        val startDate = dateFormat.parse(startDateString!!)
                        if (startDate != null && calendar.time.before(startDate)) {
                            Toast.makeText(
                                this,
                                "End date cannot be before start date",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@DatePickerDialog
                        }
                    }
                    endDateString = selectedDate
                    endDateEditText.setText(selectedDate)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun loadGraphData() {
        Log.d("GraphLoadDebug", "loadGraphData() called")

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        fun showNoData(message: String) {
            barChart.clear()
            barChart.setNoDataText(message)
            barChart.setNoDataTextColor(Color.GRAY)
            barChart.invalidate()
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val categoriesRef = db.collection("users").document(userId).collection("categories")
                val expenseRef = db.collection("users").document(userId).collection("expenses")
                val categorySnapshot = withContext(Dispatchers.IO) { categoriesRef.get().await() }

                val categoryMap = mutableMapOf<String, String>()
                for (catDoc in categorySnapshot.documents) {
                    val id = catDoc.id.trim()
                    val name = catDoc.getString("name")?.trim() ?: id
                    categoryMap[id] = name
                    Log.d("CategoryMapDebug", "Category ID: '$id' -> Name: '$name'")
                }
                Log.d("CategoryMapDebug", "Total categories loaded: ${categoryMap.size}")

                val expenseSnapshot = withContext(Dispatchers.IO) { expenseRef.get().await() }
                Log.d("ExpenseDebug", "Total expenses fetched: ${expenseSnapshot.size()}")

                if (expenseSnapshot.isEmpty) {
                    Log.w("ExpenseDebug", "No expenses found.")
                    showNoData("No expense data to display.")
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val startDate = startDateString?.let { dateFormat.parse(it) }
                val endDate = endDateString?.let { dateFormat.parse(it) }

                val filteredExpenses = expenseSnapshot.documents.filter { doc ->
                    val timestamp = doc.getTimestamp("date")
                    if (timestamp == null) {
                        Log.w("FilterDebug", "Expense ${doc.id} missing 'date' timestamp; skipping")
                        return@filter false
                    }
                    val expenseDate = timestamp.toDate()

                    val afterStart = startDate?.let {
                        val result = expenseDate >= it
                        if (!result) Log.d("FilterDebug", "Expense ${doc.id} date $expenseDate before start $it")
                        result
                    } ?: true

                    val beforeEnd = endDate?.let {
                        val result = expenseDate <= it
                        if (!result) Log.d("FilterDebug", "Expense ${doc.id} date $expenseDate after end $it")
                        result
                    } ?: true

                    afterStart && beforeEnd
                }

                Log.d("FilterDebug", "Filtered expenses count: ${filteredExpenses.size}")

                if (filteredExpenses.isEmpty()) {
                    Log.w("ExpenseDebug", "No expenses after filtering by date.")
                    showNoData("No categorized expense data to display.")
                    return@launch
                }

                val categoryTotals = filteredExpenses.mapNotNull { doc ->
                    val categoryId = doc.getString("categoryId")?.trim()
                    val amount = doc.getDouble("amount")?.toFloat()
                    if (categoryId.isNullOrBlank() || amount == null || amount <= 0f) {
                        Log.w("ExpenseDebug", "Invalid expense ${doc.id}; skipping")
                        null
                    } else {
                        val catName = categoryMap[categoryId] ?: "Unknown"
                        if (catName == "Unknown") {
                            Log.w("CategoryDebug", "Expense ${doc.id} has unknown category ID: '$categoryId'")
                        }
                        catName to amount
                    }
                }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { it.value.sum() }
                    .toMutableMap()

                Log.d("ExpenseDebug", "Category totals computed: $categoryTotals")

                if (categoryTotals.isEmpty()) {
                    Log.w("ExpenseDebug", "No valid categorized expense data to display after processing.")
                    showNoData("No categorized expense data to display.")
                    return@launch
                }

                val entries = ArrayList<BarEntry>()
                val labels = ArrayList<String>()

                categoryTotals.entries.sortedByDescending { it.value }
                    .forEachIndexed { index, entry ->
                        entries.add(BarEntry(index.toFloat(), entry.value))
                        labels.add(entry.key)
                    }

                val maxBarValue = entries.maxOfOrNull { it.y } ?: 0f

                // Additional logs here
                Log.d("GraphDataDebug", "Entries size: ${entries.size}, Labels size: ${labels.size}")
                Log.d("GraphDataDebug", "Entries: $entries")
                Log.d("GraphDataDebug", "Labels: $labels")
                Log.d("GraphDataDebug", "Max bar value: $maxBarValue")
                Log.d("GraphDebug", "BarChart visibility: ${barChart.visibility}")
                Log.d("GraphDebug", "BarChart width: ${barChart.width}, height: ${barChart.height}")

                for ((i, entry) in entries.withIndex()) {
                    Log.d("GraphDataDebug", "Entry $i: x=${entry.x}, y=${entry.y}, label=${labels.getOrNull(i)}")
                }


                val barDataSet = BarDataSet(entries, "Expenses by Category").apply {
                    color = ContextCompat.getColor(this@CategoryGraphActivity, R.color.teal_700)
                    valueTextSize = 12f
                    valueTextColor = Color.WHITE
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "R${value.toInt()}"
                        }
                    }
                }

                val data = BarData(barDataSet).apply {
                    barWidth = 0.6f
                }

                // Get goals before setting up the chart
                val goalsDoc = withContext(Dispatchers.IO) {
                    db.collection("goals").document(userId).get().await()
                }
                val minGoal = goalsDoc.getDouble("minGoal")?.toFloat()
                val maxGoal = goalsDoc.getDouble("maxGoal")?.toFloat()

                Log.d("GoalDebug", "MinGoal: $minGoal, MaxGoal: $maxGoal, MaxBarValue: ${entries.maxOfOrNull { it.y }}")

                barChart.apply {
                    this.data = data
                    xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    xAxis.labelCount = labels.size
                    xAxis.textColor = Color.WHITE
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.labelRotationAngle = -30f

                    // Configure left axis (primary axis)
                    axisLeft.apply {
                        isEnabled = true
                        textColor = Color.WHITE
                        axisLineColor = Color.WHITE
                        gridColor = Color.GRAY

                        // Calculate the maximum value considering both data and goals
                        val calculatedMax = listOfNotNull(
                            entries.maxOfOrNull { it.y },
                            minGoal,
                            maxGoal
                        ).maxOrNull() ?: entries.maxOfOrNull { it.y } ?: 0f

                        axisMaximum = calculatedMax * 1.2f  // Add 20% padding
                        axisMinimum = 0f

                        // Clear any existing limit lines
                        removeAllLimitLines()

                        // Add goal lines
                        minGoal?.let {
                            addLimitLine(LimitLine(it, "Min Goal").apply {
                                lineColor = Color.GREEN
                                lineWidth = 2f
                                textColor = Color.GREEN
                                textSize = 10f
                            })
                        }

                        maxGoal?.let {
                            addLimitLine(LimitLine(it, "Max Goal").apply {
                                lineColor = Color.RED
                                lineWidth = 2f
                                textColor = Color.RED
                                textSize = 10f
                            })
                        }
                    }

                    // Keep right axis disabled
                    axisRight.isEnabled = false

                    setFitBars(true)
                    setVisibleXRangeMaximum(6f)
                    moveViewToX(entries.size.toFloat())
                    animateY(1000)
                    notifyDataSetChanged()
                    invalidate()  // Only call this once at the end
                }

            } catch (e: Exception) {
                Log.e("GraphLoadError", "Failed to load graph data", e)
                Toast.makeText(
                    this@CategoryGraphActivity,
                    "Something went wrong while loading data.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}
//Google for Developers, 2025.Save data in a local database using Room. [online] Available at:https://developer.android.com/training/data-storage/room (Accessed 28 April 2025)