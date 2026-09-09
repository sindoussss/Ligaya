package com.ligaya.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.launch

/**
 * Section 9 Path B, wired for real (Step 13) — still visually bare (that's the later UI/UX
 * steps' job; see the brief's own note that the SOS control's prominence is a design concern,
 * not this step's). What matters here: pressing this button reaches EMERGENCY_ACTIVE with zero
 * dependency on core-ai, exactly like the placeholder screens' Back button, except this one
 * actually does something safety-critical.
 */
@Composable
fun SosScreen(
    controller: EmergencyController,
    onActivated: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var statusText by remember { mutableStateOf("Press to activate SOS") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "SOS", style = MaterialTheme.typography.headlineMedium)
        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            statusText = "Activating..."
            scope.launch {
                when (val result = controller.triggerSos()) {
                    is SosResult.Activated -> onActivated()
                    is SosResult.AlreadyInProgress -> onActivated()
                }
            }
        }) {
            Text("Activate SOS")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
