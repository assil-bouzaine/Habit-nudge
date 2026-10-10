package me.habitnudge.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.habitnudge.data.Strictness

/**
 * Screen title with optional one-line context below it. Icons on the right: whatever the screen adds
 * ([trailing]), the master-pause bell, then the Setup gear.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    showGear: Boolean = true,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val setup = LocalSetup.current
    val pause = LocalPause.current
    Row(
        Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
        if (pause != null) {
            IconButton(onClick = pause.toggle) {
                if (pause.paused) {
                    Icon(Glyphs.BellOff, contentDescription = "Resume alerts", tint = MaterialTheme.colorScheme.error)
                } else {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Pause all alerts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showGear && setup != null) {
            IconButton(onClick = setup.open) {
                if (setup.problem) {
                    BadgedBox(badge = { Badge() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Setup, needs attention")
                    }
                } else {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Setup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One tappable list row: content on a plain surface with a divider below.
 * Hierarchy comes from typography, not containers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick ?: {},
                            onLongClick = onLongClick,
                            role = Role.Button,
                        )
                    } else Modifier
                )
                .padding(horizontal = AppSpacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = AppSpacing.md),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** Quiet section container for settings blocks (one per group, never nested). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = { Column(Modifier.padding(AppSpacing.md), content = content) },
    )
}

/**
 * The one segmented control in the app (screen switches, AM/PM, note kind, strictness):
 * a tinted track with the chosen option raised on a plain surface.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    val track = RoundedCornerShape(10.dp)
    val thumb = RoundedCornerShape(8.dp)
    // The chosen option sits on the lighter tone in both themes, so it reads as raised, not pressed in.
    val dark = isSystemInDarkTheme()
    val trackColor = if (dark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
    val thumbColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLowest
    Row(
        modifier
            .fillMaxWidth()
            .clip(track)
            .background(trackColor)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        // Three or more options get a smaller label with the leading mark stacked above it, so a
        // four-way switch still fits inside a dialog on a 360dp-wide screen.
        val many = options.size > 2
        for (option in options) {
            val on = option == selected
            val cell = Modifier
                .weight(1f)
                .heightIn(min = 36.dp)
                .clip(thumb)
                .background(if (on) thumbColor else Color.Transparent)
                .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                .padding(horizontal = 2.dp, vertical = if (many) 6.dp else 8.dp)
            val text: @Composable () -> Unit = {
                Text(
                    label(option),
                    style = if (many) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (many) {
                Column(cell, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (leading != null) {
                        leading(option)
                        Spacer(Modifier.height(4.dp))
                    }
                    text()
                }
            } else {
                Row(cell, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading(option)
                        Spacer(Modifier.size(5.dp))
                    }
                    text()
                }
            }
        }
    }
}

/** Strictness as a colored dot plus its text label — never color alone. */
@Composable
fun StrictnessLabel(strictness: Strictness) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StrictnessDot(strictness)
        Spacer(Modifier.size(6.dp))
        Text(
            strictness.label(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StrictnessDot(strictness: Strictness, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(strictness.color(), CircleShape))
}

/** Plain semantic icon — no decorative circle. */
@Composable
fun StatusIcon(icon: ImageVector, tint: Color, size: Dp = 24.dp, description: String? = null) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

/** Fallback glyph when a real icon (e.g. an app launcher icon) is unavailable. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** What an empty list says: a quiet glyph, one line of title, one line of what to do. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(2.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            action()
        }
    }
}

/** Slim persistent strip while the master pause is on: state at a glance, one tap to resume. */
@Composable
fun PausedBanner(onResume: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(start = AppSpacing.md, end = AppSpacing.sm, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.BellOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(AppSpacing.sm))
            Text(
                "Alerts paused — nothing will ring",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onResume) {
                Text("Resume", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
