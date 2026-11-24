package com.example.data.repository

import com.example.data.local.CachedAuditEntity
import com.example.data.local.CachedLeaveEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.LocalSqlStats
import com.example.data.local.StaffTrackDao
import com.example.domain.AuditLog
import com.example.domain.LeaveRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Abstracts on-device Room SQLite persistence for offline-first caching of Firestore records
 * and durable multi-turn AI Copilot chat history.
 */
class LocalSqlCacheRepository(private val dao: StaffTrackDao) {

    fun observeSqlStats(workspaceId: String, userId: String): Flow<LocalSqlStats> =
        combine(
            dao.observeCachedLeaveCount(workspaceId),
            dao.observeCachedAuditCount(workspaceId),
            dao.observeChatMessageCount(userId)
        ) { leaveRows, auditRows, chatRows ->
            LocalSqlStats(
                leaveRows = leaveRows,
                auditRows = auditRows,
                chatRows = chatRows
            )
        }

    suspend fun mirrorLeavesToSql(workspaceId: String, requests: List<LeaveRequest>) {
        val entities = requests.filter { !it.syntheticTestRecord }.map { req ->
            CachedLeaveEntity(
                id = req.id,
                workspaceId = workspaceId,
                employeeName = req.employeeName,
                teamName = req.teamName,
                leaveType = req.leaveType,
                startDate = req.startDate,
                endDate = req.endDate,
                dayCount = req.dayCount,
                status = req.status
            )
        }
        dao.replaceWorkspaceLeaves(workspaceId, entities)
    }

    suspend fun mirrorAuditsToSql(workspaceId: String, logs: List<AuditLog>) {
        val entities = logs.filter { !it.syntheticTestRecord }.map { log ->
            CachedAuditEntity(
                id = log.id,
                workspaceId = workspaceId,
                action = log.action,
                employeeName = log.employeeName,
                actorName = log.actorName,
                leaveType = log.leaveType,
                daysChanged = log.daysChanged
            )
        }
        dao.replaceWorkspaceAudits(workspaceId, entities)
    }

    fun observeChatHistory(userId: String): Flow<List<ChatMessageEntity>> =
        dao.observeChatMessages(userId)

    suspend fun appendChatMessage(
        userId: String,
        role: String,
        persona: String,
        content: String
    ): Long = dao.insertChatMessage(
        ChatMessageEntity(
            userId = userId,
            role = role,
            persona = persona,
            content = content
        )
    )

    suspend fun clearChatHistory(userId: String) = dao.clearChatHistory(userId)
}
