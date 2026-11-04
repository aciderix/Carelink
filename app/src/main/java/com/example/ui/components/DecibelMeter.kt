package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CryAlertRed
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.WarningAmber

@Composable
fun DecibelMeter(
    currentDb: Int,
    thresholdDb: Int,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val progress = ((currentDb - 20).coerceIn(0, 80) / 80f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 150),
        label = "dbProgress"
    )

    val thresholdProgress = ((thresholdDb - 20).coerceIn(0, 80) / 80f)

    val isAlert = currentDb >= thresholdDb
    val isWarning = currentDb in (thresholdDb - 10) until thresholdDb

    val meterColor by animateColorAsState(
        targetValue = when {
            isAlert -> CryAlertRed
            isWarning -> WarningAmber
            else -> SafeGreen
        },
        label = "meterColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isCompact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAlert) Icons.Default.Warning else Icons.Default.GraphicEq,
                        contentDescription = "Niveau sonore",
                        tint = meterColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isAlert -> "Bruit anormal / Appel !"
                            isWarning -> "Bruit modéré"
                            else -> "Calme / Silence"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = meterColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = meterColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$currentDb dB",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = meterColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 10.dp else 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(meterColor)
            )

            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val notchX = size.width * thresholdProgress
                drawLine(
                    color = Color.White,
                    start = Offset(notchX, 0f),
                    end = Offset(notchX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        if (!isCompact) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "20 dB (silence)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Seuil d'alerte : $thresholdDb dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "100 dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
