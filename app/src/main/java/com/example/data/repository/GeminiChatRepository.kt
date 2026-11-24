package com.example.data.repository

import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.domain.LeaveRequest
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.RequestStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val CHAT_TAG = "GeminiChatRepo"

enum class ChatPersona(
    val label: String,
    val subtitle: String,
    val modelName: String
) {
    LEAVE_ADVISOR(
        label = "Leave Advisor",
        subtitle = "Personal balances, date planning & notes",
        modelName = "gemini-3.8-flash"
    ),
    COVERAGE_PLANNER(
        label = "Manager Coach",
        subtitle = "Team coverage & approval impact analysis",
        modelName = "gemini-3.8-flash"
    ),
    HR_ANALYST(
        label = "HR Analyst",
        subtitle = "Absence trends, audit & pilot ROI insights",
        modelName = "gemini-3.5-flash-lite"
    )
}

class GeminiChatRepository {

    fun sendMessageStream(
        persona: ChatPersona,
        userMessage: String,
        history: List<ChatMessageEntity>,
        workspaceName: String,
        currentMember: Member?,
        allRequests: List<LeaveRequest>
    ): Flow<String> = flow {
        val systemText = buildSystemInstruction(
            persona = persona,
            workspaceName = workspaceName,
            currentMember = currentMember,
            allRequests = allRequests
        )

        val model = Firebase.ai.generativeModel(
            modelName = persona.modelName,
            systemInstruction = content { text(systemText) }
        )

        val priorHistory = history.takeLast(20).map { msg ->
            content(role = if (msg.role == "user") "user" else "model") {
                text(msg.content)
            }
        }

        val chat = model.startChat(history = priorHistory)
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(userMessage).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(CHAT_TAG, "Gemini chat stream timed out", e)
            throw IllegalStateException("Response timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(CHAT_TAG, "Gemini chat stream failed", e)
            throw e
        }
    }

    private fun buildSystemInstruction(
        persona: ChatPersona,
        workspaceName: String,
        currentMember: Member?,
        allRequests: List<LeaveRequest>
    ): String {
        val today = LeaveValidationEngine.todayIsoDate()
        val realRequests = allRequests.filter { !it.syntheticTestRecord }
        val pendingCount = realRequests.count { it.parsedStatus == RequestStatus.PENDING }
        val approvedCount = realRequests.count { it.parsedStatus == RequestStatus.APPROVED }

        val memberContext = if (currentMember != null) {
            val myReqs = realRequests.filter { it.employeeUid == currentMember.uid }.take(5)
            val reqSummary = if (myReqs.isEmpty()) "none yet" else myReqs.joinToString("; ") {
                "${it.parsedType.label} ${it.startDate}..${it.endDate} (${it.dayCount}d, ${it.parsedStatus.label})"
            }
            """
            Signed-in user: ${currentMember.displayName}
            Role: ${currentMember.parsedRole.label}
            Team: ${currentMember.teamName}
            Live Balances: Annual=${currentMember.annualDays}/${currentMember.annualAllowance}d, Sick=${currentMember.sickDays}/${currentMember.sickAllowance}d, Personal=${currentMember.personalDays}/${currentMember.personalAllowance}d, Unpaid Used=${currentMember.unpaidUsedDays}d.
            Recent personal requests: $reqSummary.
            """.trimIndent()
        } else {
            "User profile loading."
        }

        val roleGuidance = when (persona) {
            ChatPersona.LEAVE_ADVISOR ->
                "You are the StrainX Bioworks Leave Advisor inside StaffTrack. Help employees check their exact balances, avoid overlapping dates, draft polite leave notes, and understand Annual/Sick/Personal/Unpaid rules. Remind users that human managers always make final approval decisions."
            ChatPersona.COVERAGE_PLANNER ->
                "You are the StaffTrack Manager Coverage Coach for StrainX Bioworks. Help managers evaluate pending requests, plan lab shift coverage, write clear constructive approval/decline comments, and avoid burnout or lab understaffing."
            ChatPersona.HR_ANALYST ->
                "You are the StaffTrack HR Analytics Copilot for StrainX Bioworks. Help HR interpret monthly absence patterns, pilot KPIs (approval turnaround time and monthly balance corrections), audit traceability, and workforce policy improvements."
        }

        return """
            $roleGuidance
            Organization: $workspaceName. Today's date: $today.
            Workspace snapshot: $pendingCount pending requests, $approvedCount approved requests total.
            $memberContext
            Style rules: Be concise, structured, helpful, and grounded ONLY in the live figures above. Use short bullet points when listing steps or summaries.
        """.trimIndent()
    }
}
