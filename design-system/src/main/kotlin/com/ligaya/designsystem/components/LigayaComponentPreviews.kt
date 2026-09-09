package com.ligaya.designsystem.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 34's own acceptance criterion applied uniformly: "each component renders correctly in a
 * Compose preview across light/dark and multiple font-scale settings." One multipreview
 * annotation, applied to every primitive component's `@Preview` function below, rather than each
 * component repeating the same four `@Preview` blocks — the four axis combinations this step asks
 * for (light/dark x 100%/200% font scale) are defined exactly once, here.
 */
@Preview(name = "Light 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Dark 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Light 200%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 2f)
@Preview(name = "Dark 200%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
annotation class LigayaComponentPreviews
