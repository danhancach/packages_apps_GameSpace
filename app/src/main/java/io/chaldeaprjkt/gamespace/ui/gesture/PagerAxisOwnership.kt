/*
 * Copyright (C) 2026 danhancach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.ui.gesture

import kotlin.math.abs

/**
 * Per-pointer axis ownership (Updater SameGesture / ContentScrollArbitration → View).
 *
 * Vertical intent khoa doc den UP/CANCEL (ke ca X drift nhe, repeated down).
 * Horizontal intent ro → pager tab — TRU khi childHorizontalClaim (slider/track).
 * Undecided trong slop.
 * POINTER_UP: chuyen primary sang pointer con lai (giu axis + claim; rebase origin).
 * Pure — host test khong Android.
 */
object PagerAxisOwnership {
    enum class Axis {
        Undecided,
        Vertical,
        Horizontal,
    }

    data class Config(
        val touchSlopPx: Float = 16f,
        /**
         * Diagonal: |dominant| >= |secondary| * ratio moi lock.
         * ratio=1.0: |dy|>=|dx| → vertical (uu tien doc khi bang).
         */
        val dominantRatio: Float = 1.0f,
    )

    data class State(
        val pointerId: Int = -1,
        val downX: Float = 0f,
        val downY: Float = 0f,
        val axis: Axis = Axis.Undecided,
        val active: Boolean = false,
        /**
         * DOWN tren Material Slider (thumb/track) hoac child requestDisallow trong
         * chuoi pointer — pager khong duoc nhan horizontal release.
         */
        val childHorizontalClaim: Boolean = false,
    )

    fun down(
        state: State,
        pointerId: Int,
        x: Float,
        y: Float,
        childHorizontalClaim: Boolean = false,
    ): State =
        State(
            pointerId = pointerId,
            downX = x,
            downY = y,
            axis = Axis.Undecided,
            active = true,
            childHorizontalClaim = childHorizontalClaim,
        )

    /** Child (slider) claim horizontal owner trong chuoi dang active. */
    fun claimChildHorizontal(state: State): State {
        if (!state.active || state.childHorizontalClaim) return state
        return state.copy(childHorizontalClaim = true)
    }

    /**
     * MOVE tren pointer dang track. Pointer khac / index mat → giu state (an toan).
     * childHorizontalClaim khong bi xoa khi axis lock Horizontal.
     * Ho tro Horizontal Takeover khi cu chi ngang ap dao ro ret (Miuix/LSPosed kc2AngleCommit).
     */
    fun move(
        state: State,
        pointerId: Int,
        x: Float,
        y: Float,
        config: Config = Config(),
    ): State {
        if (!state.active || pointerId != state.pointerId) return state
        // Da lock Horizontal: giu Horizontal de luot tron ven tab
        if (state.axis == Axis.Horizontal) return state

        val dx = x - state.downX
        val dy = y - state.downY
        val adx = abs(dx)
        val ady = abs(dy)
        val slop = config.touchSlopPx.coerceAtLeast(0f)
        if (adx <= slop && ady <= slop) return state

        val ratio = config.dominantRatio.coerceAtLeast(0.01f)
        val axis = when {
            // Horizontal Takeover (Miuix/LSPosed kc2AngleCommit): adx vuot slop va ap dao ro ret ady
            adx > slop && adx >= ady * 1.5f -> Axis.Horizontal
            // Neu da lock Vertical va chua ap dao ngang: giu Vertical (bao ve cuon doc khi X drift nhe)
            state.axis == Axis.Vertical -> Axis.Vertical
            ady >= adx * ratio && ady > slop -> Axis.Vertical
            adx >= ady * ratio && adx > slop -> Axis.Horizontal
            else -> Axis.Undecided
        }
        return state.copy(axis = axis)
    }

    /**
     * Primary POINTER_UP: neu con pointer → transfer (giu latched axis + claim;
     * Undecided rebase origin ve toa do successor). Khong successor → reset.
     */
    fun pointerEnded(
        state: State,
        endedPointerId: Int,
        successorId: Int? = null,
        successorX: Float = 0f,
        successorY: Float = 0f,
    ): State {
        if (!state.active) return state
        if (endedPointerId != state.pointerId) return state
        if (successorId == null) return reset()
        // Latched axis + child claim giu nguyen qua handoff; rebase origin.
        return state.copy(
            pointerId = successorId,
            downX = successorX,
            downY = successorY,
        )
    }

    fun cancel(state: State): State = reset()

    /** ACTION_UP cuoi (khong con pointer) — reset. */
    fun up(state: State, pointerId: Int): State {
        if (!state.active) return state
        return if (pointerId == state.pointerId) reset() else state
    }

    fun reset(): State = State()

    fun locksVertical(state: State): Boolean =
        state.active && state.axis == Axis.Vertical

    fun locksHorizontal(state: State): Boolean =
        state.active && state.axis == Axis.Horizontal && !state.childHorizontalClaim

    /**
     * Kiem tra cu chi chuyen sang Horizontal tiep quan (Miuix §4A.3 / SameGesturePagerScrollOwner).
     */
    fun shouldClearTensionOnHorizontalTakeover(prevState: State, nextState: State): Boolean =
        locksHorizontal(nextState) && !locksHorizontal(prevState)

    /** Can protect pager (optimistic Undecided / Vertical / slider claim). */
    fun wantsPagerDisallow(state: State): Boolean =
        state.active && (state.childHorizontalClaim || state.axis != Axis.Horizontal)

    /**
     * Co duoc release disallow cho pager o PRE-dispatch MOVE khong?
     * Frame Horizontal dau (chua arm): false — child kip claim.
     * Sau POST release (armed): true — pager intercept tab swipe.
     */
    fun mayReleasePagerDisallowPreMove(state: State, horizontalReleaseArmed: Boolean): Boolean =
        state.active &&
            state.axis == Axis.Horizontal &&
            !state.childHorizontalClaim &&
            horizontalReleaseArmed

    fun mayReleasePagerDisallowPostMove(state: State): Boolean =
        state.active && state.axis == Axis.Horizontal && !state.childHorizontalClaim

    /**
     * Gate disallow theo pre/post MOVE (mirror ViewPager2AxisGuard).
     * @return Pair(disallowWanted, armedAfter)
     */
    fun disallowGateStep(
        state: State,
        armed: Boolean,
        preDispatch: Boolean,
    ): Pair<Boolean, Boolean> {
        if (!state.active) return false to false
        if (wantsPagerDisallow(state)) return true to false
        // Horizontal && !claim
        return if (preDispatch && !armed) {
            true to armed // defer release frame dau
        } else {
            val nextArmed = if (!preDispatch) true else armed
            false to nextArmed
        }
    }

    sealed class Event {
        data class Down(
            val id: Int,
            val x: Float,
            val y: Float,
            val childHorizontalClaim: Boolean = false,
        ) : Event()
        data class Move(val id: Int, val x: Float, val y: Float) : Event()
        data class Up(val id: Int) : Event()
        data class PointerUp(
            val id: Int,
            val successorId: Int? = null,
            val successorX: Float = 0f,
            val successorY: Float = 0f,
        ) : Event()
        data object ClaimChildHorizontal : Event()
        data object Cancel : Event()
    }

    fun reduce(events: List<Event>, config: Config = Config()): State {
        var s = reset()
        for (e in events) {
            s = when (e) {
                is Event.Down -> down(s, e.id, e.x, e.y, e.childHorizontalClaim)
                is Event.Move -> move(s, e.id, e.x, e.y, config)
                is Event.Up -> up(s, e.id)
                is Event.PointerUp ->
                    pointerEnded(s, e.id, e.successorId, e.successorX, e.successorY)
                Event.ClaimChildHorizontal -> claimChildHorizontal(s)
                Event.Cancel -> cancel(s)
            }
        }
        return s
    }

    /**
     * Fixture hop dong ViewGroup: ACTION_DOWN xoa FLAG_DISALLOW_INTERCEPT.
     * setBeforeDown bi mat; restoreAfterDown moi con hieu luc o MOVE.
     * @return flag quan sat duoc o thoi diem MOVE (truoc intercept pager).
     */
    fun viewGroupDownClearsDisallowFixture(
        setBeforeDown: Boolean,
        restoreAfterDown: Boolean,
    ): Boolean {
        var disallow = false
        if (setBeforeDown) disallow = true
        // ViewGroup.dispatchTouchEvent(ACTION_DOWN) clear flag.
        disallow = false
        if (restoreAfterDown) disallow = true
        return disallow
    }

    /**
     * Fixture race PRE-release vs child claim trong cung MOVE.
     * @return disallow tai thoi diem intercept trong super.dispatch (true = pager bi chan).
     *
     * Bug cu: releaseBeforeDispatch → intercept thay disallow=false → steal du child
     * sau do claim. Fix: giu disallow qua PRE; child claim trong super van thay true.
     */
    fun sliderClaimVsPreReleaseFixture(
        releaseBeforeDispatch: Boolean,
        childClaimsDuringDispatch: Boolean,
    ): Boolean {
        var disallow = true // optimistic Undecided/Vertical sau DOWN restore
        // PRE MOVE: classify Horizontal
        if (releaseBeforeDispatch) disallow = false
        // Snapshot tai intercept (ViewPager2 RV onIntercept trong super).
        // childClaimsDuringDispatch chi anh huong SAU intercept — khong undo steal.
        @Suppress("UNUSED_VARIABLE")
        val childWouldClaim = childClaimsDuringDispatch
        return disallow
    }
}
