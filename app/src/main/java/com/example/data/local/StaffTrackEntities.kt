package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_leave_requests")
data class CachedLeaveEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val employeeName: String,
    val teamName: String,
    val leaveType: String,
    val startDate: String,
    val endDate: String,
    val dayCount: Int,
    val status: String,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_audit_logs")
data class CachedAuditEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val action: String,
    val employeeName: String,
    val actorName: String,
    val leaveType: String,
    val daysChanged: Int,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val role: String, // "user" or "model"
    val persona: String,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class LocalSqlStats(
    val leaveRows: Int = 0,
    val auditRows: Int = 0,
    val chatRows: Int = 0
)
