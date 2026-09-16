package com.ligaya.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaPrimaryButton
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Screen 2: the value-proposition carousel that runs between the splash and the real setup flow
 * ([OnboardingScreen], which is section 3's account + emergency-profile work).
 *
 * Copy is drawn from the architecture doc's own language rather than invented marketing — the
 * wake-phrase example, "your voice starts the call for help", the Safety Circle's alerting role,
 * and the companion staying with the user are all section 5/6/19 concepts, phrased for a first-run
 * audience. Nothing here claims an outcome the system can't deliver (section 23), which is why the
 * companion page says Ligaya stays with you rather than that help will arrive.
 *
 * The motion here is deliberately finger-driven rather than time-driven: each page's heading and
 * body translate at different fractions of the pager's own offset, so the depth effect tracks the
 * drag frame-for-frame instead of playing a canned animation after the fact. A time-based
 * transition would feel smooth only when swiped at exactly the speed it was tuned for.
 */
@Composable
fun OnboardingIntroScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { INTRO_PAGES.size })
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberIsReduceMotionEnabled()

    // One entrance pass for the whole screen, same shape as the splash's: linear master, each
    // element easing its own slice.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(1f, tween(LigayaMotion.durationEntrance, easing = LigayaMotion.easingEntrance))
        }
    }

    val isLastPage by remember {
        derivedStateOf { pagerState.currentPage == INTRO_PAGES.lastIndex }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.canvas)
            // Background first, insets second: the canvas fills the whole window (including behind
            // the transparent system bars, which targetSdk 35 forces) while content stays clear of
            // them. Without this the "Skip" affordance sat directly under the status-bar clock.
            .safeDrawingPadding()
            .padding(horizontal = LigayaSpacing.lg),
    ) {
        Text(
            text = "Skip",
            style = LigayaTypography.label,
            color = LigayaTheme.colors.inkSoft,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = LigayaSpacing.md)
                // A 48dp target on a small word: the text itself is far below the touch floor.
                .clip(LigayaShapes.pill)
                .clickable(onClick = onSkip)
                .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.md)
                .alpha(entrance.value),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            // How far this page sits from settled, in pages. 0 = centred, ±1 = fully off.
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val depth = if (reduceMotion) 0f else offset
            // Falls off faster than the drag itself (hence the multiplier) so the outgoing page is
            // essentially gone by the time the incoming one reaches centre. A plain 1 - |offset|
            // left both pages sitting at ~50% opacity at the crossover, and with two left-aligned
            // text blocks occupying the same band that read as unreadable text-on-text — caught on
            // a mid-swipe screenshot, which is the only place it's visible.
            val presence = (1f - offset.absoluteValue * PRESENCE_FALLOFF).coerceIn(0f, 1f)

            Column(modifier = Modifier.fillMaxSize()) {
                // Weighted rather than centred: the reference sits this copy in the upper third,
                // and centring it in the pager's own (tall) area dropped it roughly a tenth of the
                // screen too low. The uneven weights are what hold that upper-third position
                // across screen heights, where a fixed top padding would not.
                Spacer(modifier = Modifier.weight(COPY_SPACE_ABOVE))
                val intro = INTRO_PAGES[page]
                Text(
                    text = intro.heading,
                    style = LigayaTypography.introHeading,
                    color = LigayaTheme.colors.ink,
                    modifier = Modifier
                        .graphicsLayer {
                            // Heading drifts less than the page itself; body drifts more. That
                            // difference is the whole parallax — matched rates would just be a slide.
                            translationX = depth * size.width * HEADING_PARALLAX
                            alpha = presence
                        }
                        .alpha(entranceSlice(entrance.value, HEADING_START, HEADING_END)),
                )
                Spacer(modifier = Modifier.height(LigayaSpacing.md))
                Text(
                    text = intro.body,
                    style = LigayaTypography.body,
                    color = LigayaTheme.colors.inkSoft,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = depth * size.width * BODY_PARALLAX
                            alpha = presence
                        }
                        .alpha(entranceSlice(entrance.value, BODY_START, BODY_END)),
                )
                Spacer(modifier = Modifier.weight(COPY_SPACE_BELOW))
            }
        }

        PageIndicator(
            position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            count = INTRO_PAGES.size,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = LigayaSpacing.xl)
                .alpha(entranceSlice(entrance.value, INDICATOR_START, INDICATOR_END)),
        )

        // The CTA walks the user through the pages ("Continue") and only offers to leave for setup
        // on the last one. A deliberate departure from the reference, which shows "Get Started"
        // beneath an active first dot: this way each value prop actually gets read rather than
        // three of four pages going unseen by anyone who taps the button immediately. "Skip" above
        // stays as the escape hatch for people who don't want the tour at all, so nobody is
        // trapped by the change.
        //
        // Crossfaded rather than swapped outright: the two labels are different widths, and
        // swapping the text of a centred pill makes it visibly jump.
        Crossfade(
            targetState = isLastPage,
            animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle),
            label = "ctaLabel",
            modifier = Modifier.alpha(entranceSlice(entrance.value, CTA_START, CTA_END)),
        ) { last ->
            LigayaPrimaryButton(
                text = if (last) "Get Started" else "Continue",
                onClick = {
                    if (last) {
                        onGetStarted()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(LigayaSpacing.xl))
    }
}

/**
 * A continuous indicator, not a set of discrete states: [position] is the pager's own fractional
 * scroll, so the active pill grows and shrinks under the finger mid-drag rather than snapping when
 * the page settles.
 */
@Composable
private fun PageIndicator(
    position: Float,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "Page ${position.toInt() + 1} of $count"
        },
        horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val activeness = 1f - (position - index).absoluteValue.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .height(DOT_SIZE)
                    .width(lerp(DOT_SIZE, DOT_ACTIVE_WIDTH, activeness))
                    .clip(LigayaShapes.pill)
                    .background(lerp(LigayaTheme.colors.blushDeep, LigayaTheme.colors.roseDeep, activeness)),
            )
        }
    }
}

private fun entranceSlice(master: Float, start: Float, end: Float): Float =
    ((master - start) / (end - start)).coerceIn(0f, 1f)

private data class IntroPage(val heading: String, val body: String)

private val INTRO_PAGES = listOf(
    IntroPage(
        heading = "Be ready,\neven before\nyou need to be.",
        body = "Ligaya helps you stay safe, connected, and supported — when it matters most.",
    ),
    IntroPage(
        heading = "Your voice\nstarts the call\nfor help.",
        body = "Say \"Ligaya, tulong\" and the emergency begins — no unlocking, no searching for a button.",
    ),
    IntroPage(
        heading = "The people\nwho matter,\nkept in the loop.",
        body = "Your Safety Circle is alerted with your location the moment an emergency starts.",
    ),
    IntroPage(
        heading = "You're never\nalone in an\nemergency.",
        body = "Ligaya stays with you and keeps talking you through it, even while everything else is happening.",
    ),
)

private val DOT_SIZE = 8.dp
private val DOT_ACTIVE_WIDTH = 28.dp

/** Space above vs below the page copy. Uneven on purpose — see the comment at the use site. */
private const val COPY_SPACE_ABOVE = 0.55f
private const val COPY_SPACE_BELOW = 1f

/**
 * Fractions of the pager offset each element moves by. The gap between them is the depth.
 * Deliberately small: at 0.20/0.45 the body drifted nearly a quarter of the screen width toward
 * centre mid-swipe and collided with the neighbouring page's copy. Depth this subtle is still
 * clearly felt in motion while keeping each page's text inside its own column.
 */
private const val HEADING_PARALLAX = 0.10f
private const val BODY_PARALLAX = 0.22f

/** How much faster than the drag a leaving page fades. 1.0 would fade exactly with the swipe. */
private const val PRESENCE_FALLOFF = 1.6f

private const val HEADING_START = 0f
private const val HEADING_END = 0.55f
private const val BODY_START = 0.20f
private const val BODY_END = 0.75f
private const val INDICATOR_START = 0.45f
private const val INDICATOR_END = 0.9f
private const val CTA_START = 0.55f
private const val CTA_END = 1f
