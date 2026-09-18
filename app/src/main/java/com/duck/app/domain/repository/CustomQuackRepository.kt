package com.duck.app.domain.repository

import com.duck.app.domain.model.QuackCustom
import com.duck.app.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.io.File

interface CustomQuackRepository {
    suspend fun hasCustom(): Boolean
    suspend fun saveRecording(tmpPath: File, durationMs: Long): Result<Unit>
    suspend fun deleteCustom(): Result<Unit>
    fun observeCustomMeta(): Flow<QuackCustom?>
    suspend fun getCustomMeta(): QuackCustom?
    fun getCustomFile(): File?
    fun provideTmpFile(): File
}