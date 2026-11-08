/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import cat.itur.app.core.data.TestFixtures
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.config.LocationUpdateConfig
import cat.itur.app.feature.map.config.MapStyleConfig
import cat.itur.app.feature.map.ui.MapScreen
import cat.itur.app.feature.map.ui.MapViewModel
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.Layer
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private const val SCREEN_ORGANIZER_SOURCE = "organizer-source"
private const val SCREEN_PARTICIPANT_SOURCE = "participants-source"

/**
 * Exercises the production repository -> ViewModel -> Compose -> MapLibre source path rather than
 * supplying [cat.itur.app.feature.map.ui.components.map.MapLibreViewInput] directly. Removing and
 * restoring [MapScreen] recreates its MapView while the activity-scoped ViewModel retains the same
 * already-loaded participant snapshot.
 */
@HiltAndroidTest
@UninstallModules(TestConfigModule::class)
class MapScreenPointerRecreationDeviceTest {

    @BindValue
    @JvmField
    val mapStyleConfig = MapStyleConfig(styleUrl = "https://demotiles.maplibre.org/style.json")

    @BindValue
    @JvmField
    val locationUpdateConfig = LocationUpdateConfig(updateIntervalMillis = 2_000L)

    @Inject
    lateinit var users: ScenarioUserRepository

    @Inject
    lateinit var locations: ScenarioLocationRepository

    @Inject
    lateinit var activities: ScenarioActivityRepository

    @get:Rule(order = -2)
    val wakeDeviceRule = WakeDeviceRule()

    @get:Rule(order = -1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        users.current = users.participant
        val ongoing = requireNotNull(activities.activity(TestFixtures.ONGOING_ACTIVITY_ID))
        activities.replaceActivities(
            listOf(
                ongoing.copy(
                    participantIds = ongoing.participantIds + TestFixtures.PARTICIPANT_1_ID,
                ),
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MapLibre.getInstance(
                ApplicationProvider.getApplicationContext<Context>(),
                "",
                WellKnownTileServer.MapLibre,
            )
        }
    }

    @Test
    fun unchangedParticipantSnapshotMaterializesBothSourcesAfterMapRecreation() {
        val repositorySnapshot = kotlinx.coroutines.runBlocking {
            locations.getForActivity(TestFixtures.ONGOING_ACTIVITY_ID)
        }
        assertEquals(3, repositorySnapshot.size)

        var showMap by mutableStateOf(true)
        var viewModel: MapViewModel? = null
        composeRule.setContent {
            val currentViewModel = androidx.hilt.navigation.compose.hiltViewModel<MapViewModel>()
            SideEffect { viewModel = currentViewModel }
            IturTheme {
                if (showMap) {
                    MapScreen(
                        viewModel = currentViewModel,
                        locationPermissionCheck = { true },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10.seconds.inWholeMilliseconds) {
            viewModel?.currentUser?.value?.id == users.participant.id &&
                viewModel?.ongoingActivityId?.value == TestFixtures.ONGOING_ACTIVITY_ID &&
                viewModel?.organizerId?.value == TestFixtures.ORGANIZER_ID &&
                viewModel?.participantLocations?.value?.size == repositorySnapshot.size
        }
        println("PSIG-A17C initial ViewModel ${viewModelDiagnostic(requireNotNull(viewModel))}")

        val firstView = awaitCurrentMapView(previous = null)
        val firstMap = awaitMapWithSources(
            mapView = firstView,
            viewModel = requireNotNull(viewModel),
            expectedOrganizer = 1,
            expectedParticipants = 1,
        )

        composeRule.runOnIdle { showMap = false }
        composeRule.waitUntil(timeoutMillis = 10.seconds.inWholeMilliseconds) {
            currentMapView() == null
        }
        composeRule.runOnIdle { showMap = true }

        val recreatedView = awaitCurrentMapView(previous = firstView)
        assertNotSame(firstView, recreatedView)
        val recreatedMap = awaitMapWithSources(
            recreatedView,
            requireNotNull(viewModel),
            expectedOrganizer = 1,
            expectedParticipants = 1,
        )
        assertNotSame(firstMap, recreatedMap)

        // The input remained unchanged across recreation: organiser + anonymous remote participant
        // are rendered, while the registered current participant is intentionally the self marker.
        assertEquals(3, locations.getForActivityBlocking().size)
    }

    private fun awaitCurrentMapView(previous: MapView?): MapView {
        var candidate: MapView? = null
        composeRule.waitUntil(timeoutMillis = 30.seconds.inWholeMilliseconds) {
            candidate = currentMapView()
            candidate != null && candidate !== previous && candidate?.isAttachedToWindow == true &&
                candidate?.visibility == View.VISIBLE
        }
        return requireNotNull(candidate)
    }

    private fun currentMapView(): MapView? {
        var result: MapView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = findMapView(composeRule.activity.window.decorView)
        }
        return result
    }

    private fun findMapView(view: View): MapView? {
        if (view is MapView && view.isAttachedToWindow && view.visibility == View.VISIBLE) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findMapView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun awaitMapWithSources(
        mapView: MapView,
        viewModel: MapViewModel,
        expectedOrganizer: Int,
        expectedParticipants: Int,
    ): MapLibreMap {
        var map: MapLibreMap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mapView.getMapAsync { candidate -> map = candidate }
        }
        try {
            composeRule.waitUntil(timeoutMillis = 30.seconds.inWholeMilliseconds) {
                val current = map ?: return@waitUntil false
                sourceCount(current, SCREEN_ORGANIZER_SOURCE) == expectedOrganizer &&
                    sourceCount(current, SCREEN_PARTICIPANT_SOURCE) == expectedParticipants
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            val current = map
            val diagnostic = if (current == null) {
                "map callback missing"
            } else {
                mapDiagnostic(mapView, current)
            }
            throw AssertionError(
                "Pointer sources did not reach organiser=$expectedOrganizer, " +
                    "participants=$expectedParticipants; $diagnostic; " +
                    viewModelDiagnostic(viewModel),
                error,
            )
        }
        return requireNotNull(map)
    }

    private fun mapDiagnostic(mapView: MapView, map: MapLibreMap): String {
        var diagnostic = "not sampled"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val style = map.style
            fun Layer?.present() = this != null
            diagnostic = "mapView=${System.identityHashCode(mapView)}, " +
                "map=${System.identityHashCode(map)}, attached=${mapView.isAttachedToWindow}, " +
                "visible=${mapView.visibility == View.VISIBLE}, style=${style != null}, " +
                "organizerSource=${style?.getSource(SCREEN_ORGANIZER_SOURCE) != null}, " +
                "participantSource=${style?.getSource(SCREEN_PARTICIPANT_SOURCE) != null}, " +
                "organizerLayer=${style?.getLayer("organizer-layer").present()}, " +
                "participantLayer=${style?.getLayer("participants-layer").present()}, " +
                "counts=${sourceCountOnMain(style?.getSourceAs(SCREEN_ORGANIZER_SOURCE))}/" +
                sourceCountOnMain(style?.getSourceAs(SCREEN_PARTICIPANT_SOURCE))
        }
        return diagnostic
    }

    private fun viewModelDiagnostic(viewModel: MapViewModel): String =
        "viewModel(currentUser=${viewModel.currentUser.value?.id?.value}, " +
            "activity=${viewModel.ongoingActivityId.value?.value}, " +
            "organizer=${viewModel.organizerId.value?.value}, " +
            "participants=${viewModel.participantLocations.value.joinToString { location ->
                "${location.userId.value}@${location.location.latitude},${location.location.longitude}"
            }})"

    private fun sourceCount(map: MapLibreMap, sourceId: String): Int {
        var count = -1
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            count = sourceCountOnMain(map.style?.getSourceAs(sourceId))
        }
        return count
    }

    private fun sourceCountOnMain(source: GeoJsonSource?): Int =
        source?.querySourceFeatures(null)?.size ?: -1

    private fun ScenarioLocationRepository.getForActivityBlocking() =
        kotlinx.coroutines.runBlocking { getForActivity(TestFixtures.ONGOING_ACTIVITY_ID) }
}
