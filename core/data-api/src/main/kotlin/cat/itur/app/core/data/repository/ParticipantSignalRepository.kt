/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.core.data.repository

import cat.itur.app.core.domain.id.IturActivityId
import cat.itur.app.core.domain.id.UserId
import cat.itur.app.core.model.IturActivity
import cat.itur.app.core.model.ParticipantSignal

interface ParticipantSignalRepository {
    /**
     * Atomically sets or replaces [userId]'s non-okay safety [signal] in [activityId].
     *
     * Argument order is deliberately activity then participant, matching activity mutations.
     * The participant must be a current non-organiser member of an ongoing activity. Repeating
     * the already stored value is idempotent.
     */
    suspend fun setParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
        signal: ParticipantSignal,
    ): DataResult<IturActivity>

    /**
     * Clears [userId]'s safety signal in [activityId]. Absence is the canonical okay state.
     * Clearing an already absent signal is idempotent.
     */
    suspend fun clearParticipantSignal(
        activityId: IturActivityId,
        userId: UserId,
    ): DataResult<IturActivity>
}
