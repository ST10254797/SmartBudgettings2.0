package com.st10254797.smartbudgetting20

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    var isUnlocked: Boolean = false,
    var dateUnlocked: String? = null
)
