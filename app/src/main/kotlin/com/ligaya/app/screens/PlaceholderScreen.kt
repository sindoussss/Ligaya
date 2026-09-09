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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ligaya.app.navigation.LigayaDestination

/**
 * Shared bare placeholder for every non-Home destination — content, layout, and each screen's
 * real information architecture (e.g. section 18's Emergency Active fields) are built in their
 * own later roadmap steps. This step only proves the route exists and is navigable.
 */
@Composable
fun PlaceholderScreen(
    destination: LigayaDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = destination.title, style = MaterialTheme.typography.headlineMedium)
        Text(text = "Route: ${destination.route}", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
