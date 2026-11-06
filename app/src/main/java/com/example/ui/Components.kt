package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.theme.Care

/** Semantic tone shared by pills, strips, meters and notices. */
enum class Tone { OK, WARNING, CRITICAL, NEUTRAL, PAUSED, ACCENT }

@Composable fun Tone.color(): Color = when (this) {
    Tone.OK -> Care.colors.ok
    Tone.WARNING -> Care.colors.warning
    Tone.CRITICAL -> Care.colors.critical
    Tone.NEUTRAL -> Care.colors.neutral
    Tone.PAUSED -> Care.colors.paused
    Tone.ACCENT -> MaterialTheme.colorScheme.primary
}

@Composable fun Tone.container(): Color = when (this) {
    Tone.OK -> Care.colors.okContainer
    Tone.WARNING -> Care.colors.warningContainer
    Tone.CRITICAL -> Care.colors.criticalContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh
    Tone.PAUSED -> Care.colors.pausedContainer
    Tone.ACCENT -> MaterialTheme.colorScheme.primaryContainer
}

@Composable fun StatusDot(tone: Tone, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tone.color()))
}

/** Compact status label: coloured dot and text on a faint tinted background. */
@Composable fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Surface(modifier, color = tone.container(), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatusDot(tone, 7.dp)
            Text(text, color = tone.color(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small, muted section label placed above a group of panels. */
@Composable fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.labelSmall,
            color = Care.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/**
 * Flat container with a hairline border. An optional [tone] draws a narrow status strip on the
 * leading edge, so state remains readable without tinting the whole surface.
 */
@Composable fun Panel(modifier: Modifier = Modifier, tone: Tone? = null, container: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = container, contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Care.colors.border)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (tone != null) Box(Modifier.width(3.dp).fillMaxHeight().background(tone.color()))
            Column(Modifier.weight(1f).padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Label / value line used for technical details. */
@Composable fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Care.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = valueColor)
    }
}

/** Inline notice for warnings or neutral guidance. */
@Composable fun Notice(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val icon: ImageVector = when (tone) {
        Tone.CRITICAL -> Icons.Outlined.ErrorOutline
        Tone.WARNING, Tone.PAUSED -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.Info
    }
    val foreground = if (tone == Tone.NEUTRAL) Care.colors.textSecondary else tone.color()
    Surface(modifier.fillMaxWidth(), color = tone.container(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = foreground)
                Text(text, style = MaterialTheme.typography.bodyMedium,
                    color = if (tone == Tone.NEUTRAL) Care.colors.textSecondary else MaterialTheme.colorScheme.onSurface)
            }
            action?.let { Box(Modifier.padding(start = 28.dp)) { it() } }
        }
    }
}

/**
 * Relative level bar with a threshold tick. The fill turns critical at or above the threshold.
 */
@Composable fun LevelMeter(level: Int, threshold: Int, enabled: Boolean, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val max = 91f
    val fraction by animateFloatAsState(if (enabled) level.coerceIn(0, 91) / max else 0f, label = "level")
    val fill = if (!enabled) Care.colors.neutral else if (level >= threshold) Care.colors.critical else Care.colors.ok
    val track = Care.colors.track
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
        val barHeight = height.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
        if (fraction > 0f) drawRoundRect(fill, Offset(0f, top), Size(size.width * fraction, barHeight), radius)
        val x = size.width * (threshold.coerceIn(0, 91) / max)
        drawRect(tick, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

/** Collapsible section with a rotating chevron. */
@Composable fun DisclosurePanel(title: String, expanded: Boolean, toggle: () -> Unit, summary: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Panel(contentPadding = PaddingValues(0.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) "Replier" else "Déplier", onClick = toggle)
                .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (summary != null && !expanded) Text(summary, style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.KeyboardArrowDown, if (expanded) "Replier $title" else "Déplier $title",
                    Modifier.rotate(rotation), tint = Care.colors.textSecondary)
            }
            if (expanded) {
                HorizontalDivider(color = Care.colors.border)
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

/** A settings line: title, optional supporting text, trailing control. */
@Composable fun SettingRow(title: String, supporting: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary) }
        }
        trailing()
    }
}

/** Installed launcher icon, so the in-app mark always matches the home screen. */
@Composable fun BrandMark(dimension: Dp = 28.dp) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) { dimension.roundToPx() }
    val icon = remember(context, size) { context.applicationInfo.loadIcon(context.packageManager).toBitmap(size, size).asImageBitmap() }
    Image(icon, contentDescription = null, modifier = Modifier.size(dimension).clip(RoundedCornerShape(dimension / 4)))
}

/** Square icon badge used on role and empty-state panels. */
@Composable fun IconBadge(icon: ImageVector, tone: Tone = Tone.ACCENT, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(MaterialTheme.shapes.medium).background(tone.container()), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tone.color())
    }
}
