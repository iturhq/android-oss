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

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    fun provideActivityRepository(firestore: FirebaseFirestore): ActivityRepository = FirebaseActivityRepository(firestore)

    @Provides
    fun provideLocationRepository(firestore: FirebaseFirestore): LocationRepository = FirebaseLocationRepository(firestore)
}
