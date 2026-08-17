package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phone: String,
    val uid: String,
    val cookie: String,
    val password: String,
    val name: String,
    val otp: String? = null,
    val service: String = "Facebook",
    val rangeCode: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
