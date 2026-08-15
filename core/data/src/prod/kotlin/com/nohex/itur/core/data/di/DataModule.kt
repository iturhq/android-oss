/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.nohex.itur.core.data.health.BackendHealthCheck
import com.nohex.itur.core.data.health.FirestoreBackendHealthCheck
import com.nohex.itur.core.data.repository.ActivityRepository
import com.nohex.itur.core.data.repository.FirebaseActivityRepository
import com.nohex.itur.core.data.repository.FirebaseLocationRepository
import com.nohex.itur.core.data.repository.LocationRepository
import com.nohex.itur.core.data.repository.ParticipantSignalRepository
import com.nohex.itur.core.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    fun provideFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @IntoSet
    fun provideFirestoreHealthCheck(
        healthCheck: FirestoreBackendHealthCheck,
    ): BackendHealthCheck = healthCheck

    @Provides
    @Singleton
    fun provideFirebaseActivityRepository(
        firestore: FirebaseFirestore,
        functions: FirebaseFunctions,
    ): FirebaseActivityRepository = FirebaseActivityRepository(firestore, functions)

    @Provides
    fun provideActivityRepository(repository: FirebaseActivityRepository): ActivityRepository = repository

    @Provides
    fun provideParticipantSignalRepository(
        repository: FirebaseActivityRepository,
    ): ParticipantSignalRepository = repository.participantSignalRepository

    @Provides
    fun provideLocationRepository(
        firestore: FirebaseFirestore,
        userRepository: UserRepository,
    ): LocationRepository = FirebaseLocationRepository(firestore, userRepository)
}
