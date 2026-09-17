/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.ui

/**
 * Marks Compose tooling entry points that do not ship user-visible behaviour.
 *
 * The name deliberately contains `Generated`: JaCoCo excludes methods carrying a binary- or
 * runtime-retained annotation with that name. Production composables called by a preview remain
 * part of coverage; only the preview wrapper itself is excluded.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class GeneratedPreview
