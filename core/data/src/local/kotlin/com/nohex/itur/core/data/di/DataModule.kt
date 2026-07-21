/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.di

import com.google.firebase.firestore.FirebaseFirestore
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.FirebaseActivityRepository
import com.nohex.itur.core.data.repository.FirebaseLocationRepository
import com.nohex.itur.core.data.repository.LocationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // 10.0.2.2 is the loopback alias that routes to the host machine from an Android emulator.
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val FIRESTORE_EMULATOR_PORT = 8080

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().also {
        it.useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
    }

    @Provides
    fun provideActivityRepository(firestore: FirebaseFirestore): ActivityRepository = FirebaseActivityRepository(firestore)

    @Provides
    fun provideLocationRepository(firestore: FirebaseFirestore): LocationRepository = FirebaseLocationRepository(firestore)
}
