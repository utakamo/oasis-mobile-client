package com.example.oasis_mobile_client

// Data model
data class Message(
    val text: String,
    val isUser: Boolean,
    val toolUsed: Boolean = false,
    val toolLabel: String? = null,
    val id: Long = System.nanoTime()
)
