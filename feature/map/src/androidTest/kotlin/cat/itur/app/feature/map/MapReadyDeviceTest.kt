/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.Location
import cat.itur.app.core.model.ParticipantLocation
import cat.itur.app.core.ui.theme.IturTheme
import cat.itur.app.feature.map.ui.components.map.MapLibreView
import cat.itur.app.feature.map.ui.components.map.MapLibreViewCallbacks
import cat.itur.app.feature.map.ui.components.map.MapLibreViewInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.time.Duration.Companion.seconds

// Mirrors the private source IDs `MapLibreView` publishes its GeoJSON features under; not part
// of its public API, so duplicated here rather than exported just for this test.
private const val ORGANIZER_SOURCE = "organizer-source"
private const val PARTICIPANT_SOURCE = "participants-source"

/**
 * Renders [MapLibreView] in isolation -- no full [cat.itur.app.feature.map.ui.MapScreen], no
 * organiser/join flow -- against a real, reachable style, and asserts the map-ready callback and
 * every expected participant marker arrive within budget on whatever device runs the test. On an
 * AVD this is fast and incidental; it exists to catch the physical-device lifecycle gap
 * [WakeDeviceRule] fixes, which does not reproduce on an emulator.
 */
@HiltAndroidTest
class MapReadyDeviceTest {

    @get:Rule(order = -2)
    val wakeDeviceRule = WakeDeviceRule()

    @get:Rule(order = -1)
    val hiltRule = HiltAndroidRule(this)

    // MapLibreView unconditionally activates its LocationComponent once the style loads
    // (disabled, but activation itself touches the location engine), so grant these even though
    // this test never enables it.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val organizerId = UserId("mapReadyDeviceTestOrganizer")
    private val activityId = IturActivityId("mapReadyDeviceTest01")

    private fun participants(count: Int): List<ParticipantLocation> = (0 until count).map { index ->
        ParticipantLocation(
            activityId = activityId,
            userId = if (index == 0) organizerId else UserId("mapReadyDeviceTestParticipant$index"),
            userName = "Participant $index",
            location = Location(
                latitude = 41.38 + index * 0.0001,
                longitude = 2.17 + index * 0.0001,
            ),
        )
    }

    private fun assertMapReadyAndMarkersLoad(participantCount: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MapLibre.getInstance(
                ApplicationProvider.getApplicationContext<Context>(),
                "",
                WellKnownTileServer.MapLibre,
            )
        }

        var readyMap: MapLibreMap? = null
        composeRule.setContent {
            IturTheme {
                MapLibreView(
                    input = MapLibreViewInput(
                        styleUrl = mapStyleUrl(),
                        isActivityOngoing = false,
                        locationPermissionGranted = true,
                        currentUserId = null,
                        organizerId = organizerId,
                        participantLocations = participants(participantCount),
                    ),
                    callbacks = MapLibreViewCallbacks(onMapReady = { readyMap = it }),
                )
            }
        }

        // TIER-DB8E's acceptance budget: the map-ready callback must arrive within 30 seconds.
        composeRule.waitUntil(timeoutMillis = 30.seconds.inWholeMilliseconds) {
            readyMap != null
        }
        val map = requireNotNull(readyMap)

        fun sourceFeatureCount(sourceId: String): Int {
            var count = -1
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                count = map.style?.getSourceAs<GeoJsonSource>(sourceId)?.querySourceFeatures(null)?.size ?: -1
            }
            return count
        }

        // One organiser feature plus one participant feature per non-organiser participant --
        // TIER-DB8E's "feature-count assertions" against the actual GeoJSON sources, not just an
        // accessibility label.
        composeRule.waitUntil(timeoutMillis = 15.seconds.inWholeMilliseconds) {
            sourceFeatureCount(ORGANIZER_SOURCE) == 1 &&
                sourceFeatureCount(PARTICIPANT_SOURCE) == participantCount - 1
        }
    }

    // MapLibre's own public demo style: real, reachable, and keyless, so this device harness
    // doesn't depend on a MAPTILER_API_KEY secret this module has no other reason to hold.
    private fun mapStyleUrl(): String = "https://demotiles.maplibre.org/style.json"

    @Test
    fun mapReadyAndMarkersLoadAt25Participants() {
        assertMapReadyAndMarkersLoad(participantCount = 25)
    }

    @Test
    fun mapReadyAndMarkersLoadAt100Participants() {
        assertMapReadyAndMarkersLoad(participantCount = 100)
    }

    @Test
    fun mapReadyAndMarkersLoadAt125Participants() {
        assertMapReadyAndMarkersLoad(participantCount = 125)
    }
}
