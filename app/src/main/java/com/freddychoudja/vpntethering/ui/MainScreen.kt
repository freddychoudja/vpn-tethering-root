package com.freddychoudja.vpntethering.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freddychoudja.vpntethering.ui.theme.Blue
import com.freddychoudja.vpntethering.ui.theme.BlueDark
import com.freddychoudja.vpntethering.ui.theme.Danger
import com.freddychoudja.vpntethering.ui.theme.Mist
import com.freddychoudja.vpntethering.ui.theme.Navy
import com.freddychoudja.vpntethering.ui.theme.Ok
import com.freddychoudja.vpntethering.viewmodel.UiState

@Composable
fun MainScreen(
    state: UiState,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Mist, Mist, Navy.copy(alpha = 0.08f))
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "VPN Tethering",
                style = MaterialTheme.typography.headlineMedium,
                color = Navy
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = state.statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    state.rootGranted == false -> Danger
                    state.isActive -> Ok
                    else -> Navy.copy(alpha = 0.75f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onToggle,
                enabled = !state.isBusy && state.rootGranted != false,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isActive) Danger else Blue,
                    contentColor = Mist,
                    disabledContainerColor = BlueDark.copy(alpha = 0.35f)
                ),
                modifier = Modifier.size(180.dp)
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        color = Mist,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Text(
                        text = if (state.isActive) "Désactiver" else "Activer",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
