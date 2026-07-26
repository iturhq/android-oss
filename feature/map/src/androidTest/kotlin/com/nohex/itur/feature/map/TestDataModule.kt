/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.feature.map

import com.nohex.itur.core.data.TestFixtures
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.FakeActivityRepository
import com.nohex.itur.core.data.repository.FakeLocationRepository
import com.nohex.itur.core.data.repository.FakeUserRepository
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun provideActivityRepository(): ActivityRepository = FakeActivityRepository(initialActivities = TestFixtures.activities)

    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository = FakeUserRepository()

    @Provides
    @Singleton
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
