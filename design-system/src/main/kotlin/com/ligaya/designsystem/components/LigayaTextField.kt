package com.ligaya.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaMotion
import com.ligaya.designsystem.LigayaShapes
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography

/**
 * The app's text input. Built on [BasicTextField] rather than Material's `OutlinedTextField`
 * because that component's floating label, its own 56dp internal metrics and its indicator line
 * are all Material's look rather than this design's — fighting them with colour overrides produces
 * something that is *nearly* right, which on a screen made almost entirely of input fields is
 * worse than building the small thing directly.
 *
 * The focus ring animates rather than switching: an instant border colour change on a soft palette
 * reads as a glitch, where a short tween reads as the field waking up.
 *
 * [errorMessage] is both shown and announced — it is set on the field's own semantics via
 * [androidx.compose.ui.semantics.error], so a screen reader ties the message to the input that
 * caused it rather than leaving it as loose text somewhere after it.
 */
@Composable
fun LigayaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    /** Defaults to [placeholder]; override when a visible label above the field says it better. */
    accessibilityLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hasError = errorMessage != null

    val borderColor by animateColorAsState(
        targetValue = when {
            hasError -> LigayaTheme.colors.colorStatusFailed
            focused -> LigayaTheme.colors.roseDeep
            else -> LigayaTheme.colors.blushDeep
        },
        animationSpec = tween(LigayaMotion.durationStateTransition, easing = LigayaMotion.easingGentle),
        label = "fieldBorder",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_MIN_HEIGHT)
                .clip(LigayaShapes.field)
                .background(LigayaTheme.colors.surface)
                .border(width = BORDER_WIDTH, color = borderColor, shape = LigayaShapes.field)
                .padding(horizontal = LigayaSpacing.md, vertical = if (singleLine) 0.dp else LigayaSpacing.md),
            // A multi-line field grows downward, so centring would leave the first line drifting
            // toward the middle as the user types.
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = LigayaTypography.body, color = LigayaTheme.colors.inkSoft)
                }
                CompositionLocalProvider(LocalTextStyle provides LigayaTypography.body) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = singleLine,
                        minLines = if (singleLine) 1 else MULTILINE_MIN_LINES,
                        textStyle = LigayaTypography.body.copy(color = LigayaTheme.colors.ink),
                        cursorBrush = SolidColor(LigayaTheme.colors.roseDeep),
                        keyboardOptions = keyboardOptions,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                // The placeholder above is the only visible label this field has,
                                // and it disappears the moment anything is typed — so on its own
                                // it leaves a filled field completely unlabelled to a screen
                                // reader. Naming the input itself is what makes the label
                                // permanent. (Also what makes the field addressable in tests,
                                // which is how the gap surfaced.)
                                contentDescription = accessibilityLabel ?: placeholder
                                if (errorMessage != null) error(errorMessage)
                            },
                    )
                }
            }
            trailing?.invoke()
        }

        // Animated so the field below doesn't jump when a message appears or clears mid-typing.
        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn(tween(LigayaMotion.durationFast)) + expandVertically(tween(LigayaMotion.durationFast)),
            exit = fadeOut(tween(LigayaMotion.durationFast)) + shrinkVertically(tween(LigayaMotion.durationFast)),
        ) {
            Text(
                text = errorMessage.orEmpty(),
                style = LigayaTypography.label,
                color = LigayaTheme.colors.colorStatusFailed,
                modifier = Modifier.padding(top = LigayaSpacing.xs, start = LigayaSpacing.sm),
            )
        }
    }
}

private val FIELD_MIN_HEIGHT = 56.dp
private val BORDER_WIDTH = 1.5.dp

/** Enough room that a multi-line field reads as "write a few lines here", not as a tall input. */
private const val MULTILINE_MIN_LINES = 3
