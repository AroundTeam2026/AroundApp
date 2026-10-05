package com.github.aroundteam2026.aroundapp.model

data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: Role?,
    val createdAt: Long,
)
