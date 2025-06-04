package com.st10254797.smartbudgetting20

import android.content.Context
import android.widget.Toast
import android.util.Log


fun unlockBadge(context: Context, userId: String, badgeId: String) {
    val prefs = context.getSharedPreferences("badges_prefs", Context.MODE_PRIVATE)
    val key = "${userId}_$badgeId"
    val alreadyUnlocked = prefs.getBoolean(key, false)
    Log.d("BadgeUnlock", "Unlocking badge: key=$key alreadyUnlocked=$alreadyUnlocked")
    if (!alreadyUnlocked) {
        prefs.edit().putBoolean(key, true).apply()
        Toast.makeText(context, "🏅 Badge Unlocked!", Toast.LENGTH_SHORT).show()
    }
}
//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]
