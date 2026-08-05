/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.data.health.BackendHealthCheck
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Supplies the demo repositories needed by [MapScreenTest] without pulling the application-level
 * `core:data` wiring (and its Firebase implementation dependencies) into the test APK.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestDataModule {

    @Provides
    @Singleton
    fun provideScenarioBackendHealthCheck(): ScenarioBackendHealthCheck =
        ScenarioBackendHealthCheck()

    @Provides
    @IntoSet
    fun provideBackendHealthCheck(
        healthCheck: ScenarioBackendHealthCheck,
    ): BackendHealthCheck = healthCheck

    @Provides
    @Singleton
    fun provideScenarioActivityRepository(): ScenarioActivityRepository = ScenarioActivityRepository()

    @Provides
    @Singleton
    fun provideActivityRepository(
        repository: ScenarioActivityRepository,
    ): ActivityRepository = repository

    @Provides
    @Singleton
    fun provideScenarioUserRepository(): ScenarioUserRepository = ScenarioUserRepository()

    @Provides
    @Singleton
    fun provideUserRepository(repository: ScenarioUserRepository): UserRepository = repository

    @Provides
    @Singleton
    fun provideScenarioLocationRepository(
        activityRepository: ScenarioActivityRepository,
    ): ScenarioLocationRepository = ScenarioLocationRepository(activityRepository)

    @Provides
    @Singleton
    fun provideLocationRepository(
        repository: ScenarioLocationRepository,
    ): LocationRepository = repository
}
