package cat.itur.app.core.model

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GpxParserTest {
    @Test
    fun `parses GPX 10 tracks and GPX 11 routes`() {
        val gpx10 = """<gpx xmlns="http://www.topografix.com/GPX/1/0"><trk><trkseg><trkpt lat="1" lon="2"/><trkpt lat="3" lon="4"/></trkseg><trkseg><trkpt lat="5" lon="6"/></trkseg></trk></gpx>"""
        val gpx11 = """<gpx xmlns="http://www.topografix.com/GPX/1/1"><rte><rtept lat="1" lon="2"/><rtept lat="3" lon="4"/></rte></gpx>"""
        val tracks = listOf(gpx10, gpx11).map(::parse)
        assertEquals(listOf(2, 1), tracks.map { it.lines.size })
        assertEquals(2, tracks[1].lines.single().points.size)
    }

    @Test
    fun `rejects malformed unsafe and invalid coordinates`() {
        assertFailure("<gpx", GpxParseFailure.MALFORMED)
        assertFailure("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///x'>]><gpx xmlns=\"http://www.topografix.com/GPX/1/1\"/>", GpxParseFailure.UNSAFE_XML)
        assertFailure("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><rte><rtept lat=\"91\" lon=\"0\"/></rte></gpx>", GpxParseFailure.INVALID_COORDINATE)
    }

    @Test
    fun `accepts an empty GPX file`() {
        assertEquals(0, parse("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"/>").lines.size)
    }

    @Test
    fun `rejects byte and point limits`() {
        assertFailure(" ".repeat(MAX_GPX_BYTES + 1), GpxParseFailure.TOO_LARGE)
        val points = "<trkpt lat=\"1\" lon=\"2\"/>".repeat(MAX_GPX_POINTS + 1)
        assertFailure("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>$points</trkseg></trk></gpx>", GpxParseFailure.TOO_MANY_POINTS)
    }

    private fun parse(xml: String): GpxTrack = assertIs<GpxParseResult.Success>(GpxParser.parse(ByteArrayInputStream(xml.encodeToByteArray()))).track

    private fun assertFailure(xml: String, expected: GpxParseFailure) {
        assertEquals(expected, assertIs<GpxParseResult.Failure>(GpxParser.parse(ByteArrayInputStream(xml.encodeToByteArray()))).reason)
    }
}
