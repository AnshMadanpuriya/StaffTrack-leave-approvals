package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffTrackDao {

    // --- Leave Requests Local SQL Mirror ---
    @Query("SELECT * FROM cached_leave_requests WHERE workspaceId = :workspaceId ORDER BY startDate DESC")
    fun observeCachedLeaves(workspaceId: String): Flow<List<CachedLeaveEntity>>

    @Query("SELECT COUNT(*) FROM cached_leave_requests WHERE workspaceId = :workspaceId")
    fun observeCachedLeaveCount(workspaceId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeaves(items: List<CachedLeaveEntity>)

    @Query("DELETE FROM cached_leave_requests WHERE workspaceId = :workspaceId")
    suspend fun clearLeavesForWorkspace(workspaceId: String)

    @Transaction
    suspend fun replaceWorkspaceLeaves(workspaceId: String, items: List<CachedLeaveEntity>) {
        clearLeavesForWorkspace(workspaceId)
        if (items.isNotEmpty()) upsertLeaves(items)
    }

    // --- Audit Logs Local SQL Mirror ---
    @Query("SELECT COUNT(*) FROM cached_audit_logs WHERE workspaceId = :workspaceId")
    fun observeCachedAuditCount(workspaceId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudits(items: List<CachedAuditEntity>)

    @Query("DELETE FROM cached_audit_logs WHERE workspaceId = :workspaceId")
    suspend fun clearAuditsForWorkspace(workspaceId: String)

    @Transaction
    suspend fun replaceWorkspaceAudits(workspaceId: String, items: List<CachedAuditEntity>) {
        clearAuditsForWorkspace(workspaceId)
        if (items.isNotEmpty()) upsertAudits(items)
    }

    // --- Multi-Turn Gemini Chat History ---
    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestampMillis ASC, id ASC")
    fun observeChatMessages(userId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE userId = :userId")
    fun observeChatMessageCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    suspend fun clearChatHistory(userId: String)
}
