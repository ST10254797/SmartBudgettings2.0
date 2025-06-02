package com.st10254797.smartbudgetting20

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent


class GoalSettingsActivity : AppCompatActivity() {

    private lateinit var minGoalInput: EditText
    private lateinit var maxGoalInput: EditText
    private lateinit var goalTextView: TextView
    private lateinit var userId: String
    private val firestore = FirebaseFirestore.getInstance()
    private val goalsCollection = firestore.collection("goals")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goal_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Set Spending Goals"

        minGoalInput = findViewById(R.id.editTextMinGoal)
        maxGoalInput = findViewById(R.id.editTextMaxGoal)
        goalTextView = findViewById(R.id.textViewGoalValues)
        val saveButton = findViewById<Button>(R.id.buttonSaveGoals)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.selectedItemId = R.id.goals // Highlight current menu item

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
                    // Already on goals screen
                    Toast.makeText(this, "You are already on Goals", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.categories -> {
                    startActivity(Intent(this, CategoryActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }

    // Get current user's UID from Firebase Auth
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        userId = currentUser.uid

        // Load existing goal from Firestore
        goalsCollection.document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val minGoal = document.getDouble("minGoal") ?: 0.0
                    val maxGoal = document.getDouble("maxGoal") ?: 0.0

                    minGoalInput.setText(minGoal.toString())
                    maxGoalInput.setText(maxGoal.toString())
                    goalTextView.text = "Min: R%.2f | Max: R%.2f".format(minGoal, maxGoal)
                } else {
                    goalTextView.text = "No goals set yet"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load goals", Toast.LENGTH_SHORT).show()
            }

        // Save goal to Firestore
        saveButton.setOnClickListener {
            val minGoal = minGoalInput.text.toString().toDoubleOrNull()
            val maxGoal = maxGoalInput.text.toString().toDoubleOrNull()

            if (minGoal == null || maxGoal == null || minGoal < 0 || maxGoal < 0 || minGoal > maxGoal) {
                Toast.makeText(this, "Please enter valid goal values", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val goalMap = mapOf(
                "minGoal" to minGoal,
                "maxGoal" to maxGoal
            )

            goalsCollection.document(userId).set(goalMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Goals saved successfully!", Toast.LENGTH_SHORT).show()
                    goalTextView.text = "Min: R$minGoal | Max: R$maxGoal"
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save goals", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
