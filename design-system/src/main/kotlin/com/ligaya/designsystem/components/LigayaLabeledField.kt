package com.ligaya.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ligaya.designsystem.LigayaColors
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography

/**
 * A [LigayaTextField] with a persistent label above it, for form screens where several inputs sit
 * together and a placeholder alone is not enough — once a few fields are filled, placeholder-only
 * labelling leaves the user scrolling a form of unlabelled values trying to remember which is
 * which.
 *
 * The label is visual; the field underneath still carries its own accessibility label (see
 * [LigayaTextField]), so a screen reader announces it whether or not the field has content.
 */
@Composable
fun LigayaLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    errorMessage: String? = null,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = LigayaTypography.label,
            color = LigayaColors.inkSoft,
            modifier = Modifier.padding(bottom = LigayaSpacing.xs, start = LigayaSpacing.xs),
        )
        LigayaTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardOptions = keyboardOptions,
            errorMessage = errorMessage,
            singleLine = singleLine,
            accessibilityLabel = label,
        )
    }
}
