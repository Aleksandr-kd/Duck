package com.duck.app.domain.repository

import com.duck.app.domain.model.Quack
import com.duck.app.domain.model.QuackPreset
import com.duck.app.domain.model.Result

interface DuckCatalogRepository {
    suspend fun getPresets(): Result<List<QuackPreset>>
    suspend fun getActiveQuack(): Result<Quack>
    suspend fun getPresetById(id: String): QuackPreset?
}