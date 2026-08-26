/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.dataStoreFile
import cat.itur.app.core.datastore.AndroidKeystoreEmailCipher
import cat.itur.app.core.datastore.EmailCipher
import cat.itur.app.core.datastore.IturPreferences
import cat.itur.app.core.datastore.IturSettingsSerializer
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
    ): DataStore<IturPreferences> = MultiProcessDataStoreFactory.create(
        serializer = iturSettingsSerializer,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ) {
        context.dataStoreFile("user_settings.pb")
    }

    @Provides
    @Singleton
    internal fun providesEmailCipher(): EmailCipher = AndroidKeystoreEmailCipher()
}
