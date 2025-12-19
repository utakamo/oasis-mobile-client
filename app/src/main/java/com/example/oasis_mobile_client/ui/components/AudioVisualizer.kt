package com.example.oasis_mobile_client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AudioVisualizer(isListening: Boolean, rmsDbFlow: StateFlow<Float>) {
    val rmsDb by rmsDbFlow.collectAsStateWithLifecycle()
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    // Breathing scale for idle state
    val idleScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )

    // Active scale from mic input
    val normalized = (rmsDb + 2f).coerceAtLeast(0.1f)
    val activeTargetScale = 1f + (normalized / 8f) 
    
    val activeScale by animateFloatAsState(
        targetValue = if (isListening) activeTargetScale else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "active_scale"
    )

    // Final scale depends on listening state
    val finalScale = if (isListening) activeScale else idleScale
    val baseAlpha = if (isListening) 0.2f else 0.05f
    val color = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val baseRadius = size.minDimension / 3.5f
            
            drawCircle(
                color = color.copy(alpha = baseAlpha * 0.5f),
                radius = baseRadius * finalScale * 1.5f,
                center = center
            )
            drawCircle(
                color = color.copy(alpha = baseAlpha),
                radius = baseRadius * finalScale * 1.25f,
                center = center
            )
            drawCircle(
                color = color.copy(alpha = baseAlpha * 1.5f),
                radius = baseRadius * finalScale,
                center = center
            )
        }
    }
}
