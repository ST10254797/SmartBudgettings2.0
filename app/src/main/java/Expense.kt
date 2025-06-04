package com.st10254797.smartbudgetting20

data class Expense(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: String = "",
    val category: String = "", // this is category ID
    val imageUrl: String? = null,
    val userId: String = ""
)
//Firebase, 2025. Add Firebase to your Android project. [online] Available at: https://firebase.google.com/docs/android/setup [Accessed 29 May 2025]