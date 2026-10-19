/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

/** Explicit test fake, allowing map tests and later import work to set a track without I/O. */
class FakeGpxTrackRepository : InMemoryGpxTrackRepository()
