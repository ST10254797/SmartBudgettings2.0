package com.st10254797.smartbudgetting20

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class BadgesActivity : AppCompatActivity() {

    private lateinit var recyclerBadges: RecyclerView
    private lateinit var badgeAdapter: BadgeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_badges)

        recyclerBadges = findViewById(R.id.recyclerBadges)

        // Example badges list - replace with your actual data source
        val badges = listOf(
            Badge("summary_viewer", "Summary Viewer", "Viewed your budget summary", isUnlocked = isBadgeUnlocked("summary_viewer")),
            Badge("goal_setter", "Goal Setter", "Set a financial goal", isUnlocked = isBadgeUnlocked("goal_setter")),
            Badge("category_creator", "Category Creator", "Created a category", isUnlocked = isBadgeUnlocked("category_creator")),
            Badge("getting_started", "Getting Started", "Completed the first step", isUnlocked = isBadgeUnlocked("getting_started")),
            Badge("expense_tracker", "Expense Tracker", "Logged your first expense", isUnlocked = isBadgeUnlocked("expense_tracker")) // added expense badge
        )

        badgeAdapter = BadgeAdapter(badges)
        recyclerBadges.layoutManager = GridLayoutManager(this, 2) // 2 columns grid
        recyclerBadges.adapter = badgeAdapter
    }

    private fun isBadgeUnlocked(badgeId: String): Boolean {
        val prefs = getSharedPreferences("badges_prefs", MODE_PRIVATE)
        val userId = getCurrentUserId()
        val key = "${userId}_$badgeId"
        val unlocked = prefs.getBoolean(key, false)
        Log.d("BadgeUnlock", "Checking badge unlock: key=$key unlocked=$unlocked")
        return unlocked
    }


    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    override fun onResume() {
        super.onResume()

        val badges = listOf(
            Badge("summary_viewer", "Summary Viewer", "Viewed your budget summary", isUnlocked = isBadgeUnlocked("summary_viewer")),
            Badge("goal_setter", "Goal Setter", "Set a financial goal", isUnlocked = isBadgeUnlocked("goal_setter")),
            Badge("category_creator", "Category Creator", "Created a category", isUnlocked = isBadgeUnlocked("category_creator")),
            Badge("getting_started", "Getting Started", "Completed the first step", isUnlocked = isBadgeUnlocked("getting_started")),
            Badge("expense_saver", "Expense Tracker", "Logged your first expense", isUnlocked = isBadgeUnlocked("expense_saver"))
        )

        badgeAdapter = BadgeAdapter(badges)
        recyclerBadges.adapter = badgeAdapter

        // In onCreate and onResume
        badges.forEach { badge ->
            Log.d("BadgesActivity", "Badge: ${badge.name}, unlocked: ${badge.isUnlocked}")
        }

    }

}
//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]