package com.duck.app.domain.model

enum class AppTheme(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppTheme =
            entries.find { it.storageValue == value } ?: SYSTEM
    }
}