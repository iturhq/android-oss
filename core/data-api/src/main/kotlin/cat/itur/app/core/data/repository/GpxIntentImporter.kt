/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("MatchingDeclarationName")

package cat.itur.app.core.data.repository

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import cat.itur.app.core.model.GpxParseResult
import cat.itur.app.core.model.GpxParser
import java.io.File

sealed interface GpxImportResult {
    data object Imported : GpxImportResult
    data object Rejected : GpxImportResult
    data object Ignored : GpxImportResult
}

/** Reads exactly the temporary URI permission supplied by Android's open/share intent. */
suspend fun GpxTrackRepository.importFrom(intent: Intent, resolver: ContentResolver): GpxImportResult {
    val uri = intent.gpxUri() ?: return GpxImportResult.Ignored
    return importUri(uri, resolver)
}

private suspend fun GpxTrackRepository.importUri(uri: Uri, resolver: ContentResolver): GpxImportResult {
    val parsed = runCatching { uri.openInputStream(resolver)?.use(GpxParser::parse) }.getOrNull()
        ?: return GpxImportResult.Rejected
    return when (parsed) {
        is GpxParseResult.Success -> {
            setTrack(parsed.track)
            GpxImportResult.Imported
        }
        is GpxParseResult.Failure -> GpxImportResult.Rejected
    }
}

private fun Uri.openInputStream(resolver: ContentResolver) = when (scheme) {
    ContentResolver.SCHEME_FILE -> path?.let(::File)?.inputStream()
    else -> resolver.openInputStream(this)
}

private fun Intent.gpxUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM)
    else -> null
}
