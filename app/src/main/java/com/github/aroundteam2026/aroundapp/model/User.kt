package com.github.aroundteam2026.aroundapp.model

import com.google.firebase.Timestamp

data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: Role?,
    val createdAt: Timestamp,
)
