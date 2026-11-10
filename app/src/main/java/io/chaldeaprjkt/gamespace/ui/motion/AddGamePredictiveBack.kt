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
package io.chaldeaprjkt.gamespace.ui.motion

/**
 * Pure ownership cho predictive edge Back cua Add-game submenu.
 *
 * Activity dung AndroidX OnBackPressedCallback; fragment search dang ky sau
 * (backWhileSearching) nen uu tien khi enabled. Helper nay chi quyet dinh
 * scrub / cancel / commit khi submenu callback dang so huu gesture.
 */
object AddGamePredictiveBack {
    data class Session(
        val generation: Long,
        val anchorPageProgress: Float,
    )

    enum class CancelDecision {
        Ignore,
        SpringFullyOpen,
    }

    enum class CommitDecision {
        Ignore,
        CloseRoute,
    }

    /**
     * Search/IME dang consume Back -> submenu khong duoc scrub/close.
     * (Thuc te: callback fragment enabled + dang ky sau; helper dung cho host).
     */
    fun submenuOwnsPredictive(
        desiredOpen: Boolean,
        searchConsumesBack: Boolean,
    ): Boolean = desiredOpen && !searchConsumesBack

    fun beginSession(
        desiredOpen: Boolean,
        generation: Long,
        currentPageProgress: Float,
    ): Session? {
        if (!desiredOpen || generation == 0L) return null
        return Session(
            generation = generation,
            anchorPageProgress = currentPageProgress.coerceIn(0f, 1f),
        )
    }

    /** null = ignore (stale gen / route da dong). */
    fun scrubPageProgress(
        session: Session?,
        desiredOpen: Boolean,
        currentGeneration: Long,
        backProgress: Float,
    ): Float? {
        if (session == null || !desiredOpen) return null
        if (session.generation != currentGeneration) return null
        return MiuixPageMotion.pageProgressFromPredictiveBack(
            backProgress,
            session.anchorPageProgress,
        )
    }

    fun onCancelled(
        session: Session?,
        desiredOpen: Boolean,
        currentGeneration: Long,
    ): CancelDecision {
        if (session == null || !desiredOpen) return CancelDecision.Ignore
        if (session.generation != currentGeneration) return CancelDecision.Ignore
        return CancelDecision.SpringFullyOpen
    }

    /**
     * Commit / hardware / toolbar Back khi submenu callback enabled.
     * Search ownership nam o callback khac — khong di qua day.
     */
    fun onCommitted(
        desiredOpen: Boolean,
    ): CommitDecision =
        if (desiredOpen) CommitDecision.CloseRoute else CommitDecision.Ignore

    /** Cancel / reopen / destroy -> bo session; tranh stale scrub. */
    fun invalidateSession(): Session? = null

    /**
     * Scrub khong bao gio cleanup child (ke ca progress~0).
     */
    fun scrubAllowsCleanup(): Boolean = false

    /**
     * Tinh toan van toc dau khi tha tay predictive back (commit hoac cancel).
     */
    fun releaseVelocity(
        session: Session?,
        desiredOpen: Boolean,
        currentProgress: Float,
        target: Float,
        swipeVelocity: Float,
    ): Float? {
        if (session == null || !desiredOpen) return null
        return MiuixPageMotion.predictiveReleaseVelocity(
            currentProgress = currentProgress,
            target = target,
            savedSwipeVelocity = swipeVelocity,
        )
    }
}
