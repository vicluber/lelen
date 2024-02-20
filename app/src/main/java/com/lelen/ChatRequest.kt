package com.lelen
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val messages: MutableList<Message>,
    val model: String
)
