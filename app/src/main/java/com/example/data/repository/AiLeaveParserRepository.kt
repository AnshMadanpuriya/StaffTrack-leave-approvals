package com.example.data.repository

import android.util.Log
import com.example.domain.AiParseState
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val AI_TAG = "AiLeaveParser"

class AiLeaveParserRepository {

    private val leaveSuggestionSchema = Schema.obj(
        properties = mapOf(
            "isClear" to Schema.boolean(
                description = "True only when BOTH a specific leave category and unambiguous calendar start and end dates are identified."
            ),
            "leaveType" to Schema.enumeration(
                values = listOf("ANNUAL", "SICK", "PERSONAL", "UNPAID", "UNKNOWN"),
                description = "Suggested leave category matching the employee text, or UNKNOWN if unclear."
            ),
            "startDate" to Schema.string(
                description = "ISO-8601 start date YYYY-MM-DD, or empty string if unclear."
            ),
            "endDate" to Schema.string(
                description = "ISO-8601 end date YYYY-MM-DD (same as startDate for single-day leave), or empty string if unclear."
            ),
            "clarificationMessage" to Schema.string(
                description = "If isClear is false, a concise helpful question asking the employee for missing dates or leave type. Empty string if clear."
            ),
            "summaryNote" to Schema.string(
                description = "Short 1-sentence confirmation summary of the extracted suggestion for the employee to verify."
            )
        )
    )

    // Lazily initialize Firebase.ai so App Check has already completed in MainActivity.onCreate()
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = leaveSuggestionSchema
            }
        )
    }

    suspend fun parseLeavePhrase(userPhrase: String): AiParseState {
        val trimmed = userPhrase.trim()
        if (trimmed.isEmpty()) {
            return AiParseState.NeedsClarification(
                promptMessage = "Type a phrase like \"Annual leave on 23 and 24 November 2026\" to pre-fill the form.",
                partialLeaveType = null,
                partialStartDate = null,
                partialEndDate = null
            )
        }

        val today = LeaveValidationEngine.todayIsoDate()
        val prompt = """
            You are StaffTrack's leave request field assistant for StrainX Bioworks.
            Today's date is $today.
            Extract the leave category and exact inclusive start/end calendar dates from the employee's message.
            Allowed leaveType values: ANNUAL, SICK, PERSONAL, UNPAID, UNKNOWN.
            Rules:
            - Never approve or decide leave; only suggest form fields for the employee to verify.
            - For single-day leave (e.g. "Sick leave on 12 Dec 2026"), set both startDate and endDate to that date.
            - If the year is omitted for a specific month/day, infer the nearest upcoming date on or after $today.
            - If dates are missing, vague (e.g. "sometime next month", "a couple days off"), or contradictory, set isClear=false and write a clear, polite clarificationMessage asking for exact dates.
            - Always output YYYY-MM-DD format for dates.
            Employee input: "$trimmed"
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return AiParseState.Error("AI returned an empty response. Please fill in the fields manually.")

            val json = JSONObject(rawJson)
            val isClear = json.optBoolean("isClear", false)
            val rawType = json.optString("leaveType", "UNKNOWN")
            val parsedType = if (rawType == "UNKNOWN") null else LeaveType.fromRaw(rawType)
            val startDate = json.optString("startDate", "").trim()
            val endDate = json.optString("endDate", "").trim()
            val clarification = json.optString("clarificationMessage", "").trim()
            val summary = json.optString("summaryNote", "").trim()

            val validStart = startDate.takeIf { LeaveValidationEngine.isValidIsoDate(it) }
            val validEnd = endDate.takeIf { LeaveValidationEngine.isValidIsoDate(it) }

            if (isClear && parsedType != null && validStart != null && validEnd != null &&
                LeaveValidationEngine.inclusiveDayCount(validStart, validEnd) > 0
            ) {
                AiParseState.Suggested(
                    leaveType = parsedType,
                    startDate = validStart,
                    endDate = validEnd,
                    summaryNote = summary.ifBlank {
                        "Suggested ${parsedType.label} leave from ${LeaveValidationEngine.formatDisplayDate(validStart)} to ${LeaveValidationEngine.formatDisplayDate(validEnd)}."
                    }
                )
            } else {
                AiParseState.NeedsClarification(
                    promptMessage = clarification.ifBlank {
                        "Could not confirm exact start and end dates. Please pick dates below before submitting."
                    },
                    partialLeaveType = parsedType,
                    partialStartDate = validStart,
                    partialEndDate = validEnd
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "AI leave parsing timed out", e)
            AiParseState.Error("AI suggestion timed out. You can still select leave type and dates manually.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Firebase AI leave parsing failed", e)
            AiParseState.Error(e.localizedMessage ?: "AI parsing failed. Please select dates manually.")
        }
    }
}
