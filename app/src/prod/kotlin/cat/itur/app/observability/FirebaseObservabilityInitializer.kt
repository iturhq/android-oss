/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.observability

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import javax.inject.Inject

/**
 * Enables Firebase Crashlytics and Performance Monitoring collection. Both already run on the
 * real Firebase project this flavor requires for Auth/Firestore, so this adds no new credential
 * or account requirement.
 */
class FirebaseObservabilityInitializer @Inject constructor() : ObservabilityInitializer {
    override fun initialize() {
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true
    }
}
