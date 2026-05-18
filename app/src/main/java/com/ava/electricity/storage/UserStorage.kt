package com.ava.electricity.storage

import android.content.Context
import com.ava.electricity.data.HistoryItem

class UserStorage(context: Context) {
    private val prefs = context.getSharedPreferences("ava_user_storage", Context.MODE_PRIVATE)
    private val usersKey = "registered_users"
    private val signedInUserKey = "signed_in_user"

    fun saveSignedInUser(nickname: String) {
        val cleanName = nickname.trim()
        prefs.edit().putString(signedInUserKey, cleanName).apply()
        saveUser(cleanName)
    }

    fun loadSignedInUser(): String {
        return prefs.getString(signedInUserKey, "").orEmpty()
    }

    fun signOut() {
        prefs.edit().remove(signedInUserKey).apply()
    }

    fun saveUser(nickname: String) {
        val cleanName = nickname.trim()
        if (cleanName.isBlank()) return
        val users = loadUsers() + cleanName
        prefs.edit()
            .putStringSet(usersKey, users.toSet())
            .apply()
    }

    fun loadUsers(): List<String> {
        return prefs.getStringSet(usersKey, emptySet())
            .orEmpty()
            .filter { it.isNotBlank() }
            .sorted()
    }

    fun deleteUsers(nicknames: Set<String>) {
        val remainingUsers = loadUsers().filterNot { nicknames.contains(it) }.toSet()
        val editor = prefs.edit().putStringSet(usersKey, remainingUsers)
        nicknames.forEach { nickname ->
            editor.remove("favorites_$nickname")
            editor.remove("history_$nickname")
        }
        editor.apply()
    }

    fun saveFavorites(nickname: String, favoriteIds: Set<Int>) {
        saveUser(nickname)
        prefs.edit()
            .putStringSet("favorites_$nickname", favoriteIds.map { it.toString() }.toSet())
            .apply()
    }

    fun loadFavorites(nickname: String): Set<Int> {
        return prefs.getStringSet("favorites_$nickname", emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    fun saveHistory(nickname: String, item: HistoryItem) {
        saveUser(nickname)
        val old = prefs.getString("history_$nickname", "").orEmpty()
        val line = "${item.deviceName}|${item.timeSchedule}|${item.price}"
        val newValue = listOf(old, line).filter { it.isNotBlank() }.joinToString("\n")
        prefs.edit().putString("history_$nickname", newValue).apply()
    }

    fun loadHistory(nickname: String): List<HistoryItem> {
        return prefs.getString("history_$nickname", "").orEmpty()
            .lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size == 3) {
                    HistoryItem(parts[0], parts[1], parts[2].toDoubleOrNull() ?: 0.0)
                } else {
                    null
                }
            }
    }

    fun clearHistory(nickname: String) {
        prefs.edit().remove("history_$nickname").apply()
    }
}


