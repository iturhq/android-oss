/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.nohex.itur.core.datastore.IturPreferences
import com.nohex.itur.core.datastore.IturSettingsSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    internal fun providesUserSettingsDataStore(
        @ApplicationContext context: Context,
        iturSettingsSerializer: IturSettingsSerializer,
    ): DataStore<IturPreferences> = DataStoreFactory.create(
        serializer = iturSettingsSerializer,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ) {
        context.dataStoreFile("user_settings.pb")
    }
}
