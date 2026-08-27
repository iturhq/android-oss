/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.di

import cat.itur.app.core.data.health.BackendHealthCheck
import cat.itur.app.core.data.health.FirestoreBackendHealthCheck
import cat.itur.app.core.data.repository.ActivityRepository
import cat.itur.app.core.data.repository.FirebaseActivityRepository
import cat.itur.app.core.data.repository.FirebaseLocationRepository
import cat.itur.app.core.data.repository.LocationRepository
import cat.itur.app.core.data.repository.ParticipantSignalRepository
import cat.itur.app.core.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // 10.0.2.2 is the loopback alias that routes to the host machine from an Android emulator.
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val FIRESTORE_EMULATOR_PORT = 8080
    private const val FUNCTIONS_EMULATOR_PORT = 5001

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().also {
        it.useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
    }

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance().also {
        it.useEmulator(EMULATOR_HOST, FUNCTIONS_EMULATOR_PORT)
    }

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
