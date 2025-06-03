package com.st10254797.smartbudgetting20

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.st10254797.smartbudgetting20.databinding.ActivityMainBinding
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Redirect to SignInActivity if not signed in
        if (firebaseAuth.currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        } else {
            // Show welcome message
            val email = firebaseAuth.currentUser?.email
            val name = email?.substringBefore("@")
            binding.textViewWelcome.text = "Welcome, $name"
        }

        // Logout button functionality
        binding.buttonLogout.setOnClickListener {
            firebaseAuth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
        // Redirect to CategoryActivity
//        binding.buttonManageCategories.setOnClickListener {
//            val intent = Intent(this, CategoryActivity::class.java)
//            startActivity(intent)
//        }
//
//        val goalButton = findViewById<Button>(R.id.buttonSetGoals)
//        goalButton.setOnClickListener {
//            val intent = Intent(this, GoalSettingsActivity::class.java)
//            startActivity(intent)
//        }
//
//        // Redirect to CategoryGraphActivity (new)
//        binding.buttonShowCategoryGraph.setOnClickListener {
//            val intent = Intent(this, CategoryGraphActivity::class.java)
//            startActivity(intent)
//        }
//
//        binding.buttonBalanceOverview.setOnClickListener {
//            val intent = Intent(this, BalanceOverviewActivity::class.java)
//            startActivity(intent)
//        }

        binding.bottomNavigationView.selectedItemId = R.id.Back_Home
        binding.bottomNavigationView.setOnItemSelectedListener {

            when(it.itemId){

                R.id.Add_Expense -> startActivity(Intent(this, ExpenseActivity::class.java))
                R.id.goals -> startActivity(Intent(this, GoalSettingsActivity::class.java))
                R.id.graph -> startActivity(Intent(this, CategoryGraphActivity::class.java))
                R.id.Back_Home -> {
                    // Already on home, do nothing or show toast
                    Toast.makeText(this, "You are already on Home", Toast.LENGTH_SHORT).show()
                }
                R.id.categories-> startActivity(Intent(this, CategoryActivity::class.java))
                else -> {

                }
            }
            true
        }


    }
}