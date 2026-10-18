/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

const val MAX_GPX_BYTES = 5 * 1024 * 1024
const val MAX_GPX_POINTS = 100_000

sealed interface GpxParseResult {
    data class Success(val track: GpxTrack) : GpxParseResult
    data class Failure(val reason: GpxParseFailure) : GpxParseResult
}

enum class GpxParseFailure { TOO_LARGE, TOO_MANY_POINTS, MALFORMED, UNSAFE_XML, INVALID_COORDINATE }

/** Conservative GPX 1.0/1.1 reader. Waypoints are deliberately ignored. */
object GpxParser {
    fun parse(input: InputStream): GpxParseResult = try {
        val bytes = input.readBounded() ?: return GpxParseResult.Failure(GpxParseFailure.TOO_LARGE)
        if (bytes.decodeToString().contains("<!DOCTYPE", ignoreCase = true) ||
            bytes.decodeToString().contains("<?xml-stylesheet", ignoreCase = true)
        ) return GpxParseResult.Failure(GpxParseFailure.UNSAFE_XML)
        val document = DocumentBuilderFactory.newInstance().secureBuilder().parse(bytes.inputStream())
        val root = document.documentElement ?: return GpxParseResult.Failure(GpxParseFailure.MALFORMED)
        if (root.localName != "gpx" || root.namespaceURI !in setOf(GPX_10, GPX_11)) {
            return GpxParseResult.Failure(GpxParseFailure.MALFORMED)
        }
        var pointCount = 0
        val lines = mutableListOf<GpxLine>()
        root.children().forEach { child ->
            val pointSets = when (child.localName) {
                "trk" -> child.children("trkseg").map { it.points("trkpt") }
                "rte" -> listOf(child.points("rtept"))
                else -> emptyList()
            }
            pointSets.forEach { result ->
                val points = when (result) {
                    is PointResult.Failure -> return GpxParseResult.Failure(result.reason)
                    is PointResult.Success -> result.points
                }
                pointCount += points.size
                if (pointCount > MAX_GPX_POINTS) return GpxParseResult.Failure(GpxParseFailure.TOO_MANY_POINTS)
                if (points.isNotEmpty()) lines += GpxLine(points)
            }
        }
        GpxParseResult.Success(GpxTrack(lines))
    } catch (_: org.xml.sax.SAXException) {
        GpxParseResult.Failure(GpxParseFailure.MALFORMED)
    } catch (_: Exception) {
        GpxParseResult.Failure(GpxParseFailure.MALFORMED)
    }

    private fun InputStream.readBounded(): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) return output.toByteArray()
            if (output.size() + read > MAX_GPX_BYTES) return null
            output.write(buffer, 0, read)
        }
    }

    private fun DocumentBuilderFactory.secureBuilder() = apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
    }.newDocumentBuilder()

    private fun Element.children(name: String? = null): List<Element> = buildList {
        for (index in 0 until childNodes.length) {
            (childNodes.item(index) as? Element)?.takeIf { name == null || it.localName == name }?.let(::add)
        }
    }

    private fun Element.points(name: String): PointResult = children(name).map { point ->
        val latitude = point.getAttribute("lat").toDoubleOrNull()
        val longitude = point.getAttribute("lon").toDoubleOrNull()
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return PointResult.Failure(GpxParseFailure.INVALID_COORDINATE)
        GpxPoint(latitude, longitude)
    }.let(PointResult::Success)

    private sealed interface PointResult {
        data class Success(val points: List<GpxPoint>) : PointResult
        data class Failure(val reason: GpxParseFailure) : PointResult
    }

    private const val GPX_10 = "http://www.topografix.com/GPX/1/0"
    private const val GPX_11 = "http://www.topografix.com/GPX/1/1"
}
