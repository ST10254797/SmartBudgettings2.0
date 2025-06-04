package com.st10254797.smartbudgetting20

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    var isUnlocked: Boolean = false,
    var dateUnlocked: String? = null
)
//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]