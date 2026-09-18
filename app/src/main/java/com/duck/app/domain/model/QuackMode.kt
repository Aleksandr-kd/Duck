package com.duck.app.domain.model

enum class QuackMode(val storageValue: String) {
    SINGLE("single"),
    RANDOM("random");

    companion object {
        fun fromStorage(value: String?): QuackMode = entries.find { it.storageValue == value } ?: SINGLE
    }
}