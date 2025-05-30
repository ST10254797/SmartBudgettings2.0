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

class CategoryGraphActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var btnRefreshGraph: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnClearFilter: androidx.appcompat.widget.AppCompatButton
    private lateinit var backButton: androidx.appcompat.widget.AppCompatButton
    private lateinit var startDateEditText: EditText
    private lateinit var endDateEditText: EditText

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
        backButton = findViewById(R.id.backButton)
        startDateEditText = findViewById(R.id.startDateEditText)
        endDateEditText = findViewById(R.id.endDateEditText)

        setupChartAppearance()

        startDateEditText.setOnClickListener { pickDate(true) }
        endDateEditText.setOnClickListener { pickDate(false) }

        backButton.setOnClickListener { finish() }
        btnRefreshGraph.setOnClickListener { loadGraphData() }
        btnClearFilter.setOnClickListener {
            startDateString = null
            endDateString = null
            startDateEditText.text.clear()
            endDateEditText.text.clear()
            Toast.makeText(this, "Filter cleared. Showing all data.", Toast.LENGTH_SHORT).show()
            loadGraphData()
        }

        loadGraphData()
    }

    private fun setupChartAppearance() {
        barChart.apply {
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setPinchZoom(false)
            setDrawGridBackground(false)
            legend.isEnabled = true
            legend.textColor = Color.BLACK
            setExtraOffsets(0f, 0f, 0f, 40f)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.BLACK
                setAvoidFirstLastClipping(true)
                labelRotationAngle = -45f
            }

            axisLeft.apply {
                axisMinimum = 0f
                textColor = Color.BLACK
                setDrawZeroLine(true)
                zeroLineColor = Color.GRAY
            }
            axisRight.isEnabled = false
        }
    }

    private fun pickDate(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            val selectedDate = dateFormat.format(calendar.time)

            if (isStart) {
                if (endDateString != null) {
                    val endDate = dateFormat.parse(endDateString!!)
                    if (endDate != null && calendar.time.after(endDate)) {
                        Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show()
                        return@DatePickerDialog
                    }
                }
                startDateString = selectedDate
                startDateEditText.setText(selectedDate)
            } else {
                if (startDateString != null) {
                    val startDate = dateFormat.parse(startDateString!!)
                    if (startDate != null && calendar.time.before(startDate)) {
                        Toast.makeText(this, "End date cannot be before start date", Toast.LENGTH_SHORT).show()
                        return@DatePickerDialog
                    }
                }
                endDateString = selectedDate
                endDateEditText.setText(selectedDate)
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        datePicker.show()
    }

    private fun loadGraphData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val categoriesRef = db.collection("users").document(userId).collection("categories")
            val expenseRef = db.collection("users").document(userId).collection("expenses")

            // Fetch categories to map ID -> Name, trim IDs and names
            categoriesRef.get().addOnSuccessListener { categorySnapshot ->
                val categoryMap = mutableMapOf<String, String>()
                for (catDoc in categorySnapshot.documents) {
                    val id = catDoc.id.trim()
                    val name = catDoc.getString("name")?.trim() ?: id
                    categoryMap[id] = name
                    Log.d("CategoryMapDebug", "Category ID: '$id' -> Name: '$name'")
                }

                val localStartDateString = startDateString
                val localEndDateString = endDateString

                val expensesQuery = when {
                    localStartDateString != null && localEndDateString != null -> {
                        val startDate = dateFormat.parse(localStartDateString)
                        val endDate = dateFormat.parse(localEndDateString)
                        if (startDate != null && endDate != null && !startDate.after(endDate)) {
                            Toast.makeText(
                                this@CategoryGraphActivity,
                                "Showing data from $localStartDateString to $localEndDateString",
                                Toast.LENGTH_SHORT
                            ).show()
                            expenseRef
                                .whereGreaterThanOrEqualTo("date", Timestamp(startDate))
                                .whereLessThanOrEqualTo("date", Timestamp(endDate))
                        } else {
                            Toast.makeText(this@CategoryGraphActivity, "Invalid date range. Showing all data.", Toast.LENGTH_SHORT).show()
                            expenseRef
                        }
                    }
                    else -> {
                        Toast.makeText(this@CategoryGraphActivity, "Showing all data (no date filter).", Toast.LENGTH_SHORT).show()
                        expenseRef
                    }
                }

                expensesQuery.get().addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        barChart.clear()
                        barChart.setNoDataText("No expense data to display.")
                        barChart.setNoDataTextColor(Color.GRAY)
                        barChart.invalidate()
                        return@addOnSuccessListener
                    }

                    val categoryTotals = mutableMapOf<String, Float>()

                    for (doc in snapshot.documents) {
                        val categoryId = doc.getString("category")?.trim()
                        val amount = doc.get("amount")?.toString()?.toFloatOrNull() ?: 0f

                        Log.d("ExpenseDebug", "Category ID from expense: '$categoryId', Amount: $amount")

                        if (amount <= 0f) continue

                        // Lookup category name from the map, fallback to "Unknown"
                        val categoryName = categoryId?.let { categoryMap[it] } ?: "Unknown"

                        Log.d("ExpenseDebug", "Mapped Category Name: '$categoryName'")

                        categoryTotals[categoryName] = categoryTotals.getOrDefault(categoryName, 0f) + amount
                    }

                    if (categoryTotals.isEmpty()) {
                        barChart.clear()
                        barChart.setNoDataText("No categorized expense data to display.")
                        barChart.setNoDataTextColor(Color.GRAY)
                        barChart.invalidate()
                        return@addOnSuccessListener
                    }

                    Log.d("CategoryTotals", categoryTotals.toString())

                    val entries = ArrayList<BarEntry>()
                    val labels = ArrayList<String>()

                    categoryTotals.entries.sortedByDescending { it.value }.forEachIndexed { index, entry ->
                        entries.add(BarEntry(index.toFloat(), entry.value))
                        labels.add(entry.key)
                    }

                    val maxBarValue = entries.maxOfOrNull { it.y } ?: 0f

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
                        setValueTextSize(10f)
                    }

                    barChart.apply {
                        this.data = data
                        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                        xAxis.labelCount = labels.size
                        xAxis.textColor = Color.WHITE
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)

                        axisLeft.apply {
                            isEnabled = true
                            textColor = Color.WHITE
                            axisLineColor = Color.WHITE
                            gridColor = Color.GRAY
                            axisMaximum = maxBarValue * 1.2f
                        }

                        axisRight.isEnabled = false
                        setFitBars(true)
                        setVisibleXRangeMaximum(6f)
                        moveViewToX(entries.size.toFloat())
                        animateY(1000)
                        invalidate()
                    }

                    // Add goal lines if available
                    db.collection("goals").document(userId).get().addOnSuccessListener { doc ->
                        val minGoal = doc.getDouble("minGoal")?.toFloat()
                        val maxGoal = doc.getDouble("maxGoal")?.toFloat()

                        val yAxis = barChart.axisLeft
                        yAxis.removeAllLimitLines()

                        minGoal?.let {
                            yAxis.addLimitLine(LimitLine(it, "Min Goal").apply {
                                lineColor = Color.GREEN
                                lineWidth = 2f
                                textColor = Color.GREEN
                                textSize = 10f
                            })
                        }

                        maxGoal?.let {
                            yAxis.addLimitLine(LimitLine(it, "Max Goal").apply {
                                lineColor = Color.RED
                                lineWidth = 2f
                                textColor = Color.RED
                                textSize = 10f
                            })
                        }

                        val newMax = listOfNotNull(maxBarValue, minGoal, maxGoal).maxOrNull() ?: maxBarValue
                        yAxis.axisMaximum = newMax * 1.2f
                        barChart.invalidate()
                    }
                }
            }
        }
    }

}
