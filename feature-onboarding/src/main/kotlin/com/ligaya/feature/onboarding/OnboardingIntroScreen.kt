package com.ligaya.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ligaya.designsystem.LigayaIcons
import com.ligaya.designsystem.LigayaLogo
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.LigayaEmotion
import com.ligaya.designsystem.components.LigayaFrame
import com.ligaya.designsystem.components.LigayaMascot
import com.ligaya.designsystem.components.LigayaPrimaryButton
import com.ligaya.designsystem.components.rememberLigayaMascotController
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Visual design, screen 12: the introduction that runs between the splash and the real setup flow
 * ([OnboardingScreen], which is section 3's account + emergency-profile work).
 *
 * Ligaya herself is on the page, as the reference draws her, and she is one mascot across all four pages
 * rather than one per page — she changes expression as the copy changes, instead of being reloaded under a
 * swipe.
 *
 * Copy is drawn from the architecture doc's own language rather than invented marketing — the wake-phrase
 * example, "your voice starts the call for help", the Safety Circle's alerting role, and the companion
 * staying with the user are all section 5/6/19 concepts, phrased for a first-run audience. Nothing here
 * claims an outcome the system cannot deliver (section 23): the companion page says Ligaya stays with you
 * rather than that help will arrive, and the Safety Circle page says the app shows whether each message got
 * through rather than promising it did.
 *
 * The motion is finger-driven rather than time-driven: each page's heading and body translate at different
 * fractions of the pager's own offset, so the depth tracks the drag frame-for-frame instead of playing a
 * canned animation afterwards.
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
    val ligaya = rememberLigayaMascotController()

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
    // Settled page, not the fractional one: her face changes once the page arrives rather than flickering
    // through three expressions during a single swipe.
    LaunchedEffect(pagerState.currentPage) {
        ligaya.setEmotion(INTRO_PAGES[pagerState.currentPage].emotion)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LigayaTheme.colors.cream)
            // Background first, insets second: the canvas fills the whole window (including behind
            // the transparent system bars, which targetSdk 35 forces) while content stays clear of
            // them.
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = LigayaSpacing.lg, end = LigayaSpacing.sm, top = 6.dp)
                .alpha(entranceSlice(entrance.value, HEADING_START, HEADING_END)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LigayaLogo(modifier = Modifier.size(30.dp))
            Text(
                text = "Ligaya",
                style = LigayaTypography.homeBrand,
                color = LigayaTheme.colors.cocoaInk,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Skip",
                style = LigayaTypography.label,
                color = LigayaTheme.colors.taupe,
                modifier = Modifier
                    // A 48dp target on a small word: the text itself is far below the touch floor.
                    .clip(LigayaShapes.pill)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = LigayaSpacing.md, vertical = LigayaSpacing.md),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Square and pinned to the bottom, rather than filling the whole area: her artwork is about as
            // wide as it is tall, so in a taller box it is drawn to fit the width and then simply stops,
            // leaving a straight edge across the middle of the page with a gap under it. Squaring the
            // region puts her own bottom edge exactly where the fade below is.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .alpha(entranceSlice(entrance.value, BODY_START, BODY_END)),
            ) {
                LigayaMascot(
                    controller = ligaya,
                    frame = LigayaFrame.Bust,
                    modifier = Modifier.fillMaxSize(),
                )
                // Her artwork ends on a straight edge at the bottom of its canvas; this washes it into the
                // page so she fades out above the button rather than stopping on a line.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, LigayaTheme.colors.cream),
                            ),
                        ),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                // How far this page sits from settled, in pages. 0 = centred, ±1 = fully off.
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val depth = if (reduceMotion) 0f else offset
                // Falls off faster than the drag itself (hence the multiplier) so the outgoing page is
                // essentially gone by the time the incoming one reaches centre. A plain 1 - |offset| left
                // both pages at ~50% opacity at the crossover, which read as text on text.
                val presence = (1f - offset.absoluteValue * PRESENCE_FALLOFF).coerceIn(0f, 1f)
                val intro = INTRO_PAGES[page]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = LigayaSpacing.lg, vertical = LigayaSpacing.md),
                ) {
                    Text(
                        text = intro.heading,
                        style = LigayaTypography.homeName,
                        color = LigayaTheme.colors.cocoaInk,
                        modifier = Modifier
                            .widthIn(max = COPY_WIDTH)
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
                        style = LigayaTypography.homeIntro,
                        color = LigayaTheme.colors.taupe,
                        modifier = Modifier
                            .widthIn(max = COPY_WIDTH)
                            .graphicsLayer {
                                translationX = depth * size.width * BODY_PARALLAX
                                alpha = presence
                            }
                            .alpha(entranceSlice(entrance.value, BODY_START, BODY_END)),
                    )
                }
            }
        }

        // The CTA walks the user through the pages ("Next") and only offers to leave for setup on the last
        // one. A deliberate departure from the reference, which shows a Next button beneath an active first
        // dot: this way each page actually gets read rather than three of four going unseen by anyone who
        // taps immediately. "Skip" above stays as the escape hatch, so nobody is trapped by the change.
        //
        // Crossfaded rather than swapped outright: the two labels are different widths, and swapping the
        // text of a centred pill makes it visibly jump.
        Crossfade(
            targetState = isLastPage,
            animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle),
            label = "ctaLabel",
            modifier = Modifier
                .padding(horizontal = LigayaSpacing.lg)
                .alpha(entranceSlice(entrance.value, CTA_START, CTA_END)),
        ) { last ->
            LigayaPrimaryButton(
                text = if (last) "Get Started" else "Next",
                trailingIcon = LigayaIcons.arrowForward,
                onClick = {
                    if (last) {
                        onGetStarted()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }

        PageIndicator(
            position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            count = INTRO_PAGES.size,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = LigayaSpacing.lg)
                .alpha(entranceSlice(entrance.value, INDICATOR_START, INDICATOR_END)),
        )

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
                    .background(lerp(LigayaTheme.colors.blushDeep, LigayaTheme.colors.berry, activeness)),
            )
        }
    }
}

private fun entranceSlice(master: Float, start: Float, end: Float): Float =
    ((master - start) / (end - start)).coerceIn(0f, 1f)

private data class IntroPage(val heading: String, val body: String, val emotion: LigayaEmotion)

private val INTRO_PAGES = listOf(
    IntroPage(
        heading = "I'm your\nFilipino AI\nassistant.",
        body = "I'm here to help with school, home, safety, and more. Just talk to me, I'm here!",
        emotion = LigayaEmotion.Content,
    ),
    IntroPage(
        heading = "Your voice\nstarts the call\nfor help.",
        body = "Say \"Ligaya, tulong\" and the emergency begins. No unlocking, no searching for a button.",
        emotion = LigayaEmotion.Attentive,
    ),
    IntroPage(
        heading = "The people\nwho matter,\nkept in the loop.",
        body = "Your Safety Circle is alerted with your location when an emergency starts, and you can see " +
            "whether each message actually got through.",
        emotion = LigayaEmotion.Reassuring,
    ),
    IntroPage(
        heading = "You're never\nalone in an\nemergency.",
        body = "Ligaya stays with you and keeps talking you through it, even while everything else is happening.",
        emotion = LigayaEmotion.Reassuring,
    ),
)

private val DOT_SIZE = 8.dp
private val DOT_ACTIVE_WIDTH = 28.dp

/** How wide the copy is allowed to run, so it stays a column beside her rather than crossing her face. */
private val COPY_WIDTH = 250.dp

/**
 * Fractions of the pager offset each element moves by. The gap between them is the depth.
 * Deliberately small: at 0.20/0.45 the body drifted nearly a quarter of the screen width toward
 * centre mid-swipe and collided with the neighbouring page's copy.
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
