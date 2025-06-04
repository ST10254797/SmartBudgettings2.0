package com.st10254797.smartbudgetting20

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

@Suppress("DEPRECATION")
class ImageViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        // Get references to the views
        val imageView = findViewById<ImageView>(R.id.fullScreenImageView)
        val backButton = findViewById<ImageButton>(R.id.backButton) // Find the back button by ID

        // Get the image URL from the Intent extras
        val imageUrl = intent.getStringExtra("imageUrl")

        // Load the image using Glide
        if (imageUrl != null) {
            Glide.with(this).load(imageUrl).into(imageView)
        }

        // Set a click listener for the back button
        backButton.setOnClickListener {
            onBackPressed() // Go back to the previous activity
        }
    }

    // Handle the Up button (back arrow) click
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed() // Go back to the previous activity
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]