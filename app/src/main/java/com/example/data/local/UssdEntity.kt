package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ExecutionType
import com.example.data.model.UssdResult

@Entity(tableName = "ussd_history")
data class UssdEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val responseMessage: String,
    val isSuccess: Boolean,
    val failureReason: String?,
    val executionType: String,
    val timestamp: Long,
    val durationMs: Long,
    val simName: String = ""
) {
    fun toModel(): UssdResult {
        val type = try {
            ExecutionType.valueOf(executionType)
        } catch (e: Exception) {
            ExecutionType.AUTOMATIC_IN_APP
        }
        return UssdResult(
            id = id,
            code = code,
            responseMessage = responseMessage,
            isSuccess = isSuccess,
            failureReason = failureReason,
            executionType = type,
            timestamp = timestamp,
            durationMs = durationMs,
            simName = simName
        )
    }

    companion object {
        fun fromModel(model: UssdResult): UssdEntity {
            return UssdEntity(
                id = model.id,
                code = model.code,
                responseMessage = model.responseMessage,
                isSuccess = model.isSuccess,
                failureReason = model.failureReason,
                executionType = model.executionType.name,
                timestamp = model.timestamp,
                durationMs = model.durationMs,
                simName = model.simName
            )
        }
    }
}
