/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.di

import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.FakeActivityRepository
import com.nohex.itur.core.data.repository.FakeLocationRepository
import com.nohex.itur.core.data.repository.FakeUserRepository
import com.nohex.itur.core.data.repository.FakeUserSettingsRepository
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.ParticipantSignalRepository
import com.nohex.itur.core.data.repository.UserRepository
import com.nohex.itur.core.data.repository.UserSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeDataModule {

    @Provides
    @Singleton
    fun provideFakeActivityRepository(): FakeActivityRepository = FakeActivityRepository(initialActivities = TestFixtures.activities)

    @Provides
    fun provideActivityRepository(repository: FakeActivityRepository): ActivityRepository = repository

    @Provides
    fun provideParticipantSignalRepository(
        repository: FakeActivityRepository,
    ): ParticipantSignalRepository = repository.participantSignalRepository

    @Provides
    fun provideUserRepository(): UserRepository = FakeUserRepository()

    @Provides
    fun provideUserSettingsRepository(): UserSettingsRepository = FakeUserSettingsRepository()

    @Provides
    fun provideLocationRepository(
        activityRepository: ActivityRepository,
    ): LocationRepository = FakeLocationRepository(
        activityRepository = activityRepository,
        initialLocations = mapOf(
            TestFixtures.ONGOING_ACTIVITY_ID to TestFixtures.ongoingActivityLocations
                .associate { it.userId to it.location },
        ),
    )
}
