package com.duck.app.di

import com.duck.app.data.catalog.DuckCatalogRepositoryImpl
import com.duck.app.data.custom.CustomQuackRepositoryImpl
import com.duck.app.data.datastore.DuckSettingsRepositoryImpl
import com.duck.app.domain.repository.CustomQuackRepository
import com.duck.app.domain.repository.DuckCatalogRepository
import com.duck.app.domain.repository.DuckSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI-модуль: связывание интерфейсов репозиториев с реализациями (Hilt).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DuckSettingsRepositoryImpl
    ): DuckSettingsRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(
        impl: DuckCatalogRepositoryImpl
    ): DuckCatalogRepository

    @Binds
    @Singleton
    abstract fun bindCustomQuackRepository(
        impl: CustomQuackRepositoryImpl
    ): CustomQuackRepository
}