/*
 * Copyright 2022 The Android Open Source Project
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

package fm.corus.android.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.rememberUpdatedState

import android.graphics.Insets
import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Compose 1.7.7's interactive IME scroll implementation, with lifecycle cleanup fixed.
 * The drag consumption, fling spline and settling spring below are unchanged from AndroidX.
 * Upstream: androidx/compose/foundation/layout/WindowInsetsConnection.android.kt (1.7.7).
 *
 * Upstream dispose() resumes its waiter without clearing it or cancelling the platform request.
 * A late onReady() then resumes the same waiter again and crashes. Keep this local copy until
 * the Compose dependency provides equivalent safe cleanup.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun Modifier.safeImeNestedScroll(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return this
    return composed(debugInspectorInfo { name = "safeImeNestedScroll" }) {
        val visible = rememberUpdatedState(WindowInsets.isImeVisible)
        val view = LocalView.current
        val density = LocalDensity.current
        val connection = remember(view, density) {
            ImeNestedScrollConnection(view, density) { visible.value }
        }
        DisposableEffect(connection) { onDispose { connection.dispose() } }
        nestedScroll(connection)
    }
}

/**
 * Used in place of the standard Job cancellation pathway to avoid reflective
 * javaClass.simpleName lookups to build the exception message and stack trace collection.
 * Remove if these are changed in kotlinx.coroutines.
 */
private class WindowInsetsAnimationCancelledException : CancellationException(
    "Window insets animation cancelled"
) {
    override fun fillInStackTrace(): Throwable {
        // Avoid null.clone() on Android <= 6.0 when accessing stackTrace
        stackTrace = emptyArray()
        return this
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.R)
internal class ImeNestedScrollConnection(
    private val view: View,
    private val density: Density,
    private val isImeVisible: () -> Boolean,
) : NestedScrollConnection,
    WindowInsetsAnimationControlListener {

    /**
     * The [WindowInsetsAnimationController] is only available once the insets are starting
     * to be manipulated. This is used to set the current insets position.
     */
    private val sideCalculator = BottomSideCalculator
    private var disposed = false

    private var animationController: WindowInsetsAnimationController? = null

    /**
     * `true` when we've requested a [WindowInsetsAnimationController] so that we don't
     * ask for one when we've already asked for one. This should be `false` until we've
     * made a request or when we've cleared [animationController] after it is finished.
     */
    private var isControllerRequested = false

    /**
     * We never need to cancel the animation because we always control it directly instead
     * of using the [WindowInsetsAnimationController] to animate its value.
     */
    private val cancellationSignal = CancellationSignal()

    /**
     * Because touch motion has finer granularity than integers, we capture the fractions of
     * integers here so that we can keep the finger more in line with the touch. Without this,
     * we'd accumulate error.
     */
    private var partialConsumption = 0f

    /**
     * The [Job] that is launched to animate the insets during a fling. This can be canceled
     * when the user touches the screen.
     */
    private var animationJob: Job? = null

    /**
     * Request an animation controller because it is `null`. If one has already been requested,
     * this method does nothing.
     */
    private fun requestAnimationController() {
        if (!disposed && !isControllerRequested) {
            isControllerRequested = true
            view.windowInsetsController?.controlWindowInsetsAnimation(
                android.view.WindowInsets.Type.ime(), // type
                -1, // durationMillis
                null, // interpolator
                cancellationSignal,
                this
            )
        }
    }

    private var continuation: CancellableContinuation<WindowInsetsAnimationController?>? = null

    /**
     * Allows us to suspend, waiting for the animation controller to be returned.
     */
    private suspend fun getAnimationController(): WindowInsetsAnimationController? =
        if (disposed) null else animationController ?: suspendCancellableCoroutine { continuation ->
            this.continuation = continuation
            requestAnimationController()
        }

    /**
     * Handle the dragging that hides the WindowInsets.
     */
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        scroll(available, sideCalculator.hideMotion(available.x, available.y))

    /**
     * Handle the dragging that exposes the WindowInsets.
     */
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = scroll(available, sideCalculator.showMotion(available.x, available.y))

    /**
     * Scrolls [scrollAmount] and returns the consumed amount of [available].
     */
    private fun scroll(available: Offset, scrollAmount: Float): Offset {
        animationJob?.let {
            it.cancel(WindowInsetsAnimationCancelledException())
            animationJob = null
        }

        val animationController = animationController

        if (disposed || scrollAmount == 0f ||
            (isImeVisible() == (scrollAmount > 0f) && animationController == null)
        ) {
            // No motion in the right direction or this is already fully shown/hidden.
            return Offset.Zero
        }

        if (animationController == null) {
            partialConsumption = 0f
            // The animation controller isn't ready yet. Just consume the scroll.
            requestAnimationController()
            return sideCalculator.consumedOffsets(available)
        }

        val hidden = sideCalculator.valueOf(animationController.hiddenStateInsets)
        val shown = sideCalculator.valueOf(animationController.shownStateInsets)
        val currentInsets = animationController.currentInsets
        val current = sideCalculator.valueOf(currentInsets)

        val target = if (scrollAmount > 0f) shown else hidden

        if (current == target) {
            // This is already correct, so nothing to consume
            partialConsumption = 0f
            return Offset.Zero
        }

        val total = current + scrollAmount + partialConsumption
        val next = total.fastRoundToInt().coerceIn(hidden, shown)
        partialConsumption = total - total.fastRoundToInt()

        if (next != current) {
            animationController.setInsetsAndAlpha(
                sideCalculator.adjustInsets(currentInsets, next),
                1f, // alpha
                0f, // progress
            )
        }
        return sideCalculator.consumedOffsets(available)
    }

    /**
     * Handle flinging toward hiding the insets.
     */
    override suspend fun onPreFling(available: Velocity): Velocity =
        fling(available, sideCalculator.hideMotion(available.x, available.y), false)

    /**
     * Handle flinging toward showing the insets.
     */
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        fling(available, sideCalculator.showMotion(available.x, available.y), true)

    /**
     * Handle flinging by [flingAmount] and return the consumed velocity of [available].
     * [towardShown] should be `true` when the intended motion is to show the insets or `false`
     * if to hide them. We always handle flinging toward the insets if the [flingAmount] is
     * `0` so that the insets animate to a fully-shown or fully-hidden state.
     */
    private suspend fun fling(
        available: Velocity,
        flingAmount: Float,
        towardShown: Boolean
    ): Velocity {
        animationJob?.cancel(WindowInsetsAnimationCancelledException())
        animationJob = null
        partialConsumption = 0f

        if ((flingAmount == 0f && !towardShown) ||
            (animationController == null && isImeVisible() == towardShown)
        ) {
            // Either there's no motion to hide or we're certain that
            // the inset is already correct.
            return Velocity.Zero
        }

        val animationController = getAnimationController() ?: return Velocity.Zero

        val hidden = sideCalculator.valueOf(animationController.hiddenStateInsets)
        val shown = sideCalculator.valueOf(animationController.shownStateInsets)
        val currentInsets = animationController.currentInsets
        val current = sideCalculator.valueOf(currentInsets)

        if ((flingAmount <= 0 && current == hidden) || (flingAmount >= 0 && current == shown)) {
            // We've already reached the destination
            animationController.finish(current == shown)
            this@ImeNestedScrollConnection.animationController = null
            return Velocity.Zero
        }

        // Let's see if the velocity is enough to get open
        val spec = SplineBasedFloatDecayAnimationSpec(density)
        val distance = current + spec.flingDistance(flingAmount)

        val endPercent = (distance - hidden) / (shown - hidden)
        val targetShown = endPercent > 0.5f
        val target = if (targetShown) shown else hidden

        if (distance > shown || distance < hidden) {
            var endVelocity = 0f
            // This is enough to reach hidden or shown state, so we can use the Android
            // spline animation.
            coroutineScope {
                animationJob = launch {
                    animateDecay(
                        initialValue = current.toFloat(),
                        initialVelocity = flingAmount,
                        animationSpec = spec
                    ) { value, velocity ->
                        if (value in hidden.toFloat()..shown.toFloat()) {
                            adjustInsets(value)
                        } else {
                            // We've reached the end
                            endVelocity = velocity
                            animationController.finish(targetShown)
                            this@ImeNestedScrollConnection.animationController = null
                            animationJob?.cancel(WindowInsetsAnimationCancelledException())
                        }
                    }
                }
                animationJob?.join()
                animationJob = null
            }
            return sideCalculator.consumedVelocity(available, endVelocity)
        } else {
            // This fling won't make it to the end, so animate to shown or hidden state using
            // a spring animation
            coroutineScope {
                animationJob = launch {
                    val animatedValue = Animatable(current.toFloat())
                    animatedValue.animateTo(target.toFloat(), initialVelocity = flingAmount) {
                        adjustInsets(value)
                    }
                    animationController.finish(targetShown)
                    this@ImeNestedScrollConnection.animationController = null
                }
            }
            return sideCalculator.consumedVelocity(available, 0f)
        }
    }

    /**
     * Change the inset's side to [inset].
     */
    private fun adjustInsets(inset: Float) {
        animationController?.let {
            val currentInsets = it.currentInsets
            val nextInsets = sideCalculator.adjustInsets(currentInsets, inset.fastRoundToInt())
            it.setInsetsAndAlpha(
                nextInsets,
                1f, // alpha
                0f, // progress
            )
        }
    }

    /**
     * Called after [requestAnimationController] and the [animationController] is ready.
     */
    override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
        if (disposed) {
            // Android may deliver readiness after the modifier has left composition.
            if (controller.isReady) {
                controller.finish(controller.currentInsets != controller.hiddenStateInsets)
            }
            return
        }
        animationController = controller
        isControllerRequested = false
        resumePendingController(controller)
    }

    private fun resumePendingController(controller: WindowInsetsAnimationController?) {
        // Clear before resuming: resume/finish/cancel may synchronously re-enter callbacks.
        val pending = continuation
        continuation = null
        pending?.resume(controller) { _, _, _ -> }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        resumePendingController(null)
        animationJob?.cancel()
        animationJob = null
        val controller = animationController
        animationController = null
        isControllerRequested = false
        if (controller?.isReady == true) {
            controller.finish(controller.currentInsets != controller.hiddenStateInsets)
        }
        cancellationSignal.cancel()
    }

    override fun onFinished(controller: WindowInsetsAnimationController) {
        animationEnded()
    }

    override fun onCancelled(controller: WindowInsetsAnimationController?) {
        animationEnded()
    }

    /**
     * The controlled animation has been terminated.
     */
    private fun animationEnded() {
        val controller = animationController
        animationController = null
        if (controller?.isReady == true) {
            controller.finish(isImeVisible())
        }

        // The animation controller may not have been given to us, so we have to cancel animations
        // waiting for it.
        resumePendingController(null)

        // Cancel any animation that's running.
        animationJob?.cancel(WindowInsetsAnimationCancelledException())
        animationJob = null

        partialConsumption = 0f
        isControllerRequested = false
    }
}

/** The bottom-edge calculations used by AndroidX imeNestedScroll. */
@RequiresApi(Build.VERSION_CODES.R)
private object BottomSideCalculator {
    fun valueOf(insets: Insets): Int = insets.bottom
    fun motionOf(x: Float, y: Float): Float = -y
    fun hideMotion(x: Float, y: Float): Float = motionOf(x, y).coerceAtMost(0f)
    fun showMotion(x: Float, y: Float): Float = motionOf(x, y).coerceAtLeast(0f)
    fun adjustInsets(oldInsets: Insets, newValue: Int): Insets =
        Insets.of(oldInsets.left, oldInsets.top, oldInsets.right, newValue)
    fun consumedOffsets(available: Offset): Offset = Offset(0f, available.y)
    fun consumedVelocity(available: Velocity, remaining: Float): Velocity =
        Velocity(0f, available.y + remaining)
}

// SplineBasedFloatDecayAnimationSpec is in animation:animation library, which depends on
// foundation-layout, so I've copied it below, but a bit trimmed to only have what is needed.

// These constants are copied from the Android spline decay rate
private const val Inflection = 0.35f // Tension lines cross at (Inflection, 1)
private val PlatformFlingScrollFriction = ViewConfiguration.getScrollFriction()
private const val GravityEarth = 9.80665f
private const val InchesPerMeter = 39.37f
private val DecelerationRate = ln(0.78) / ln(0.9)
private val DecelMinusOne = DecelerationRate - 1.0
private const val StartTension = 0.5f
private const val EndTension = 1.0f
private const val P1 = StartTension * Inflection
private const val P2 = 1.0f - EndTension * (1.0f - Inflection)

private class SplineBasedFloatDecayAnimationSpec(density: Density) :
    FloatDecayAnimationSpec {

    override val absVelocityThreshold: Float get() = 0f

    /**
     * A density-specific coefficient adjusted to physical values.
     */
    private val magicPhysicalCoefficient: Float =
        GravityEarth * InchesPerMeter * density.density * 160f * 0.84f

    private fun getSplineDeceleration(velocity: Float): Double =
        AndroidFlingSpline.deceleration(
            velocity,
            PlatformFlingScrollFriction * magicPhysicalCoefficient
        )

    /**
     * Compute the distance of a fling in units given an initial [velocity] of units/second
     */
    fun flingDistance(velocity: Float): Float {
        val l = getSplineDeceleration(velocity)
        return (
            PlatformFlingScrollFriction * magicPhysicalCoefficient
                * exp(DecelerationRate / DecelMinusOne * l)
            ).toFloat() * sign(velocity)
    }

    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        initialValue + flingDistance(initialVelocity)

    @Suppress("MethodNameUnits")
    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val duration = getDurationNanos(0f, initialVelocity)
        val splinePos = if (duration > 0) playTimeNanos / duration.toFloat() else 1f
        val distance = flingDistance(initialVelocity)
        return initialValue + distance *
            AndroidFlingSpline.flingPosition(splinePos).distanceCoefficient
    }

    @Suppress("MethodNameUnits")
    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long {
        val l = getSplineDeceleration(initialVelocity)
        return (1_000_000_000.0 * exp(l / DecelMinusOne)).toLong()
    }

    @Suppress("MethodNameUnits")
    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        initialVelocity: Float
    ): Float {
        val duration = getDurationNanos(0f, initialVelocity)
        val splinePos = if (duration > 0L) playTimeNanos / duration.toFloat() else 1f
        val distance = flingDistance(initialVelocity)
        return AndroidFlingSpline.flingPosition(splinePos).velocityCoefficient *
            distance / duration * 1_000_000_000.0f
    }
}

private object AndroidFlingSpline {
    private const val NbSamples = 100
    private val SplinePositions = FloatArray(NbSamples + 1)
    private val SplineTimes = FloatArray(NbSamples + 1)

    init {
        var xMin = 0.0f
        var yMin = 0.0f
        for (i in 0 until NbSamples) {
            val alpha = i.toFloat() / NbSamples
            var xMax = 1.0f
            var x: Float
            var tx: Float
            var coef: Float
            while (true) {
                x = xMin + (xMax - xMin) / 2.0f
                coef = 3.0f * x * (1.0f - x)
                tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x
                if (abs(tx - alpha) < 1E-5) break
                if (tx > alpha) xMax = x else xMin = x
            }
            SplinePositions[i] = coef * ((1.0f - x) * StartTension + x) + x * x * x
            var yMax = 1.0f
            var y: Float
            var dy: Float
            while (true) {
                y = yMin + (yMax - yMin) / 2.0f
                coef = 3.0f * y * (1.0f - y)
                dy = coef * ((1.0f - y) * StartTension + y) + y * y * y
                if (abs(dy - alpha) < 1E-5) break
                if (dy > alpha) yMax = y else yMin = y
            }
            SplineTimes[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y
        }
        SplineTimes[NbSamples] = 1.0f
        SplinePositions[NbSamples] = SplineTimes[NbSamples]
    }

    /**
     * Compute an instantaneous fling position along the scroller spline.
     *
     * @param time progress through the fling animation from 0-1
     */
    fun flingPosition(time: Float): FlingResult {
        val index = (NbSamples * time).toInt()
        var distanceCoef = 1f
        var velocityCoef = 0f
        if (index < NbSamples) {
            val tInf = index.toFloat() / NbSamples
            val tSup = (index + 1).toFloat() / NbSamples
            val dInf = SplinePositions[index]
            val dSup = SplinePositions[index + 1]
            velocityCoef = (dSup - dInf) / (tSup - tInf)
            distanceCoef = dInf + (time - tInf) * velocityCoef
        }
        return FlingResult(packFloats(distanceCoef, velocityCoef))
    }

    /**
     * The rate of deceleration along the spline motion given [velocity] and [friction].
     */
    fun deceleration(velocity: Float, friction: Float): Double =
        ln(Inflection * abs(velocity) / friction.toDouble())

    /**
     * Result coefficients of a scroll computation
     */
    @JvmInline
    value class FlingResult(private val packedValue: Long) {
        /**
         * Linear distance traveled from 0-1, from source (0) to destination (1)
         */
        val distanceCoefficient: Float get() = unpackFloat1(packedValue)
        /**
         * Instantaneous velocity coefficient at this point in the fling expressed in
         * total distance per unit time
         */
        val velocityCoefficient: Float get() = unpackFloat2(packedValue)
    }
}
