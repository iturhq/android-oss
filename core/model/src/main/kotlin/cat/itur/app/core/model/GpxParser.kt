/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("TooManyFunctions")

/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.model

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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
        parseBytes(input.readBounded())
    } catch (_: Exception) {
        malformed()
    }
}

private fun parseBytes(bytes: ByteArray?): GpxParseResult = when (bytes) {
    null -> tooLarge()
    else -> if (bytes.containsUnsafeXml()) unsafeXml() else parseDocument(bytes)
}

private fun parseDocument(bytes: ByteArray): GpxParseResult = try {
    val root = DocumentBuilderFactory.newInstance().secureBuilder()
        .parse(bytes.inputStream())
        .documentElement
    root?.takeIf(Element::isGpxRoot)?.let(::parseLines) ?: malformed()
} catch (_: Exception) {
    malformed()
}

private fun parseLines(root: Element): GpxParseResult {
    val lines = mutableListOf<GpxLine>()
    var pointsSeen = 0
    var failure: GpxParseResult.Failure? = null
    for (pointSet in root.children().flatMap(Element::pointSets)) {
        when (pointSet) {
            PointResult.InvalidCoordinate -> failure = invalidCoordinate()
            is PointResult.Points -> {
                pointsSeen += pointSet.points.size
                if (pointsSeen > MAX_GPX_POINTS) failure = tooManyPoints()
                pointSet.points.takeIf(List<GpxPoint>::isNotEmpty)?.let { lines += GpxLine(it) }
            }
        }
        if (failure != null) break
    }
    return failure ?: GpxParseResult.Success(GpxTrack(lines))
}

private fun malformed() = GpxParseResult.Failure(GpxParseFailure.MALFORMED)

private fun tooLarge() = GpxParseResult.Failure(GpxParseFailure.TOO_LARGE)

private fun unsafeXml() = GpxParseResult.Failure(GpxParseFailure.UNSAFE_XML)

private fun invalidCoordinate() = GpxParseResult.Failure(GpxParseFailure.INVALID_COORDINATE)

private fun tooManyPoints() = GpxParseResult.Failure(GpxParseFailure.TOO_MANY_POINTS)

private fun ByteArray.containsUnsafeXml(): Boolean {
    val xml = decodeToString()
    return xml.contains("<!DOCTYPE", ignoreCase = true) ||
        xml.contains("<?xml-stylesheet", ignoreCase = true)
}

private fun Element.isGpxRoot() = localName == "gpx" && namespaceURI in setOf(GPX_10, GPX_11)

private fun Element.pointSets(): List<PointResult> = when (localName) {
    "trk" -> children("trkseg").map { it.points("trkpt") }
    "rte" -> listOf(points("rtept"))
    else -> emptyList()
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

private fun Element.points(name: String): PointResult {
    val points = mutableListOf<GpxPoint>()
    for (element in children(name)) {
        val point = element.coordinates() ?: return PointResult.InvalidCoordinate
        points += point
    }
    return PointResult.Points(points)
}

private fun Element.coordinates(): GpxPoint? {
    val latitude = getAttribute("lat").toDoubleOrNull()
    val longitude = getAttribute("lon").toDoubleOrNull()
    return latitude?.takeIf(Double::isValidLatitude)
        ?.let { validLatitude -> longitude?.takeIf(Double::isValidLongitude)?.let { GpxPoint(validLatitude, it) } }
}

private fun Double.isValidLatitude() = isFinite() && this in MIN_LATITUDE..MAX_LATITUDE

private fun Double.isValidLongitude() = isFinite() && this in MIN_LONGITUDE..MAX_LONGITUDE

private sealed interface PointResult {
    data class Points(val points: List<GpxPoint>) : PointResult

    data object InvalidCoordinate : PointResult
}

private const val GPX_10 = "http://www.topografix.com/GPX/1/0"
private const val GPX_11 = "http://www.topografix.com/GPX/1/1"
private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
