package com.st10254797.smartbudgetting20

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    // -------------------- GOAL --------------------
    suspend fun insertOrUpdateGoal(goal: Goal) {
        db.collection("goals").document(goal.userId).set(goal).await()
    }

    suspend fun getGoal(userId: String): Goal? {
        val snapshot = db.collection("goals").document(userId).get().await()
        return snapshot.toObject(Goal::class.java)
    }

    // -------------------- CATEGORY --------------------
    suspend fun insertCategory(category: Category) {
        db.collection("categories").add(category).await()
    }

    suspend fun getAllCategories(): List<Category> {
        val snapshot = db.collection("categories").get().await()
        return snapshot.toObjects(Category::class.java)
    }

    suspend fun getCategoryByName(name: String): Category? {
        val snapshot = db.collection("categories")
            .whereEqualTo("name", name)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(Category::class.java)
    }

    suspend fun getCategoryByNameAndUser(name: String, userId: String): Category? {
        val snapshot = db.collection("categories")
            .whereEqualTo("name", name)
            .whereEqualTo("userId", userId)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(Category::class.java)
    }

    suspend fun getCategoriesByUser(userId: String): List<Category> {
        val snapshot = db.collection("categories")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.toObjects(Category::class.java)
    }

    suspend fun deleteCategoryByNameAndUser(name: String, userId: String) {
        val querySnapshot = db.collection("categories")
            .whereEqualTo("name", name)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        for (doc in querySnapshot.documents) {
            db.collection("categories").document(doc.id).delete().await()
        }
    }

    // -------------------- EXPENSE --------------------
    suspend fun insertExpense(expense: Expense) {
        db.collection("expenses").add(expense).await()
    }

    suspend fun updateExpense(expenseId: String, expense: Expense) {
        db.collection("expenses").document(expenseId).set(expense).await()
    }

    suspend fun deleteExpense(expenseId: String) {
        db.collection("expenses").document(expenseId).delete().await()
    }

    suspend fun getExpensesByUser(userId: String): List<Expense> {
        val snapshot = db.collection("expenses")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.toObjects(Expense::class.java)
    }

    suspend fun getExpensesByCategory(categoryId: Long): List<Expense> {
        val snapshot = db.collection("expenses")
            .whereEqualTo("category", categoryId)
            .get()
            .await()
        return snapshot.toObjects(Expense::class.java)
    }

    suspend fun getExpensesByCategoryAndUser(categoryId: Long, userId: String): List<Expense> {
        val snapshot = db.collection("expenses")
            .whereEqualTo("category", categoryId)
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.toObjects(Expense::class.java)
    }

    // NOTE: Firestore does NOT support joins or aggregations like SUM in queries.
    // You must fetch the data and calculate totals in Kotlin.
}