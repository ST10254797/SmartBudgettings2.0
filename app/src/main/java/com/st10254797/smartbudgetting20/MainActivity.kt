package com.st10254797.smartbudgetting20

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.st10254797.smartbudgetting20.databinding.ActivityMainBinding

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
            binding.textViewWelcome.text = "Welcome, $email"
        }

        // Logout button functionality
        binding.buttonLogout.setOnClickListener {
            firebaseAuth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
        // Redirect to CategoryActivity
        binding.buttonManageCategories.setOnClickListener {
            val intent = Intent(this, CategoryActivity::class.java)
            startActivity(intent)
        }

        val goalButton = findViewById<Button>(R.id.buttonSetGoals)
        goalButton.setOnClickListener {
            val intent = Intent(this, GoalSettingsActivity::class.java)
            startActivity(intent)
        }

        // Redirect to CategoryGraphActivity (new)
        binding.buttonShowCategoryGraph.setOnClickListener {
            val intent = Intent(this, CategoryGraphActivity::class.java)
            startActivity(intent)
        }

        binding.buttonBalanceOverview.setOnClickListener {
            val intent = Intent(this, BalanceOverviewActivity::class.java)
            startActivity(intent)
        }


    }
}