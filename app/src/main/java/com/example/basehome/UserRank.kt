package com.example.basehome

data class UserRank(
    val userId: String = "",
    val userName: String = "",
    val addressCount: Int = 0
) {
    companion object {
        // Глобальный список ID топ-3 пользователей для отображения значков по всему приложению
        var top3UserIds: List<String> = emptyList()
    }
}
