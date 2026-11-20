package com.postpole.wheelfile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ui.theme.ChipShape
import com.postpole.wheelfile.ui.theme.FieldShape
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import com.postpole.wheelfile.ui.theme.WheelfileTheme

/** Filled text field with the design pack's shape; [supporting] carries errors and soft warnings. */
@Composable
fun WfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    isWarning: Boolean = false,
    decimal: Boolean = false,
    number: Boolean = false,
    suffix: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = WheelfileTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.fieldMinHeight),
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        shape = FieldShape,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it, color = if (isWarning && !isError) colors.onWarningContainer else androidx.compose.ui.graphics.Color.Unspecified) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                decimal -> KeyboardType.Decimal
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

/** Wrapping row of single-select chips; each has a 48 dp touch target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            val text = label(option)
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(text) },
                shape = ChipShape,
                modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
            )
        }
    }
}

/** Primary action pinned to the bottom of a form. Content scrolls under it. */
@Composable
fun SaveBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().height(Sizes.saveButtonHeight),
            ) { Text(text, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Economy delta pill: arrow plus text, never colour alone. */
@Composable
fun DeltaPill(text: String, better: Boolean, modifier: Modifier = Modifier) {
    val colors = WheelfileTheme.colors
    val container = if (better) colors.positiveContainer else colors.negativeContainer
    val content = if (better) colors.onPositiveContainer else colors.onNegativeContainer
    Surface(modifier, color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (better) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Sizes.cardPadding),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val inner: @Composable () -> Unit = { Box(Modifier.padding(contentPadding)) { content() } }
    if (outlined) {
        if (onClick != null) {
            OutlinedCard(onClick, modifier, shape = shape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { inner() }
        } else {
            OutlinedCard(modifier, shape = shape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { inner() }
        }
    } else {
        val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        if (onClick != null) Card(onClick, modifier, shape = shape, colors = colors) { inner() } else Card(modifier, shape = shape, colors = colors) { inner() }
    }
}

/** Switch row with title and summary, at least 56 dp high. */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).semantics(mergeDescendants = true) {
            selected = checked
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A hero number block used by the forms: label, big tabular number, optional auto chip. */
@Composable
fun TotalHero(
    label: String,
    value: String,
    chip: String?,
    electric: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (electric) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (electric) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(modifier.fillMaxWidth(), color = container, contentColor = content, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = WheelfileTheme.type.numberHero)
            if (chip != null) {
                Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shape = PillShape) {
                    Text(chip, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Plain empty state: text only. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(Sizes.screenMargin * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/**
 * Places a FAB 16 dp above the banner slot (DECISIONS.md items 3-5): [adSlotHeight] is the slot's current
 * height, 0 when collapsed or hidden, so the FAB drops to 16 dp above the gesture inset.
 */
@Composable
fun BoxScope.FabAboveAd(adSlotHeight: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = Sizes.screenMargin, bottom = adSlotHeight + Sizes.fabGap),
    ) { content() }
}

