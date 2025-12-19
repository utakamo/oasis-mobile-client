package com.example.oasis_mobile_client

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AudioVisualizer(isListening: Boolean, rmsDbFlow: StateFlow<Float>) {
    if (!isListening) return

    val rmsDb by rmsDbFlow.collectAsStateWithLifecycle()
    // Normalize RMS (-2 to 10 usually from Android SpeechRecognizer) to a scale factor.
    // Values vary by device, but usually range from -2 (silence) to 10+ (loud).
    val normalized = (rmsDb + 2f).coerceAtLeast(0.1f)
    val targetScale = 1f + (normalized / 10f) 

    // Smooth animation
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "visualizer_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Draw multiple circles with different opacities and scales
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val baseRadius = size.minDimension / 4f
            
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.1f),
                radius = baseRadius * scale * 1.5f,
                center = center
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.2f),
                radius = baseRadius * scale * 1.25f,
                center = center
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = baseRadius * scale,
                center = center
            )
        }
    }
}