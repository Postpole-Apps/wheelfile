package com.postpole.wheelfile.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptTrigger
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

const val PRO_PROMPT_CARD_TAG = "pro_prompt_card"
const val PRO_PROMPT_SHEET_TAG = "pro_prompt_sheet"
const val PRO_PROMPT_CLOSE_TAG = "pro_prompt_close"
const val PRO_PROMPT_CTA_TAG = "pro_prompt_cta"
const val PRO_PROMPT_NOT_NOW_TAG = "pro_prompt_not_now"

/** What a prompt says. The price is the Play `ProductDetails` string; null until it has loaded. */
private class PromptCopy(val title: String, val body: String)

@Composable
private fun cardCopy(trigger: ProPromptTrigger, count: Int): PromptCopy = when (trigger) {
    ProPromptTrigger.SECOND_VEHICLE_ADDED ->
        PromptCopy(stringResource(R.string.pro_auto_second_vehicle_title), stringResource(R.string.pro_auto_second_vehicle_body))
    ProPromptTrigger.SERVICE_LOGGED ->
        PromptCopy(stringResource(R.string.pro_auto_service_title), stringResource(R.string.pro_auto_service_body))
    ProPromptTrigger.REPORTS_3_MONTHS ->
        PromptCopy(stringResource(R.string.pro_auto_reports_title), stringResource(R.string.pro_auto_reports_body))
    else ->
        PromptCopy(
            pluralStringResource(R.plurals.pro_auto_entries_title, count, count),
            stringResource(R.string.pro_auto_entries_body),
        )
}

@Composable
private fun ctaLabel(price: String?) =
    if (price == null) stringResource(R.string.pro_auto_cta_no_price) else stringResource(R.string.pro_auto_cta, price)

/**
 * The inline Pro card of the Garage (`surface=card`), between the hero card and the other vehicles. Close X only: no
 * "Not now". [count] is the `entries_milestone` threshold that fired.
 */
@Composable
fun ProPromptCard(
    trigger: ProPromptTrigger,
    count: Int,
    price: String?,
    onCta: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = cardCopy(trigger, count)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag(PRO_PROMPT_CARD_TAG),
    ) {
        Column(Modifier.padding(start = Sizes.cardPadding, top = Spacing.s, bottom = Sizes.cardPadding)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = Spacing.s).size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val glyph = if (trigger == ProPromptTrigger.REPORTS_3_MONTHS) PdfGlyph else SwapGlyph
                    Icon(glyph, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = Spacing.m, top = Spacing.m)) {
                    Text(copy.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        copy.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose, Modifier.size(Sizes.touchTargetMin).testTag(PRO_PROMPT_CLOSE_TAG)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pro_auto_close_cd_card))
                }
            }
            Spacer(Modifier.height(Spacing.m))
            Button(
                onClick = onCta,
                Modifier.fillMaxWidth().padding(end = Sizes.cardPadding).heightIn(min = Sizes.touchTargetMin).testTag(PRO_PROMPT_CTA_TAG),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.s))
                Text(ctaLabel(price))
            }
        }
    }
}

/**
 * The shared Pro sheet (`surface=sheet`), hosted on Vehicle detail › Overview: `service_logged`, and
 * `entries_milestone` when a service or expense was the threshold entry. Swipe, scrim tap and Back are reported as `swipe`; the X as `close`; "Not now" as `not_now`.
 * The max height keeps the vehicle top bar visible and the inner content scrolls at large font sizes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPromptSheet(
    trigger: ProPromptTrigger,
    count: Int,
    price: String?,
    onCta: () -> Unit,
    onDismiss: (ProPromptDismissMethod) -> Unit,
) {
    val service = trigger == ProPromptTrigger.SERVICE_LOGGED
    ModalBottomSheet(
        onDismissRequest = { onDismiss(ProPromptDismissMethod.SWIPE) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag(PRO_PROMPT_SHEET_TAG),
    ) {
        Column(
            Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    if (service) {
                        stringResource(R.string.pro_auto_service_title)
                    } else {
                        pluralStringResource(R.plurals.pro_auto_entries_sheet_title, count, count)
                    },
                    Modifier.weight(1f).padding(top = Spacing.m),
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(
                    onClick = { onDismiss(ProPromptDismissMethod.CLOSE) },
                    Modifier.size(Sizes.touchTargetMin).testTag(PRO_PROMPT_CLOSE_TAG),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(
                            if (service) R.string.pro_auto_close_cd_sheet_service else R.string.pro_auto_close_cd_sheet,
                        ),
                    )
                }
            }
            Text(
                stringResource(if (service) R.string.pro_auto_service_body else R.string.pro_auto_entries_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (service) {
                    BenefitRow(stringResource(R.string.pro_benefit_service_pdf))
                    BenefitRow(stringResource(R.string.pro_benefit_charts_ranges))
                    BenefitRow(stringResource(R.string.pro_benefit_unlimited_vehicles))
                } else {
                    BenefitRow(stringResource(R.string.pro_benefit_unlimited_vehicles))
                    BenefitRow(stringResource(R.string.pro_benefit_pdf_history))
                    BenefitRow(stringResource(R.string.pro_benefit_all_time_charts))
                }
            }
            PdfSamplePage()
            Button(
                onClick = onCta,
                Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin).testTag(PRO_PROMPT_CTA_TAG),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.s))
                Text(ctaLabel(price))
            }
            TextButton(
                onClick = { onDismiss(ProPromptDismissMethod.NOT_NOW) },
                Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin).testTag(PRO_PROMPT_NOT_NOW_TAG),
            ) { Text(stringResource(R.string.pro_auto_not_now)) }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A static "Service history" sample page (never user data, no price): light printed paper in light and dark, and
 * not scaled with the font size, so it looks the same at 200%.
 */
@Composable
private fun PdfSamplePage() {
    val density = LocalDensity.current
    val paper = Color(0xFFFFFDF8)
    val ink = Color(0xFF2B2620)
    val muted = Color(0xFF6B6459)
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = paper,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.pro_auto_sample_title),
                        Modifier.weight(1f),
                        color = ink,
                        fontSize = 14.sp,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.pro_auto_sample_badge),
                        Modifier.background(Color(0xFFB4452C), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(stringResource(R.string.pro_auto_sample_vehicle), color = muted, fontSize = 11.sp)
                listOf(R.string.pro_auto_sample_row_1, R.string.pro_auto_sample_row_2, R.string.pro_auto_sample_row_3).forEach {
                    Text(stringResource(it), color = ink, fontSize = 12.sp)
                }
                Text(stringResource(R.string.pro_auto_sample_total), color = muted, fontSize = 11.sp)
            }
        }
    }
}

/** PDF-document glyph (the Material "description" path) for the Reports card. */
private val PdfGlyph: ImageVector = ImageVector.Builder(
    name = "Description",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M14,2H6C4.9,2 4,2.9 4,4v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8L14,2z" +
            "M16,18H8v-2h8V18zM16,14H8v-2h8V14zM13,9V3.5L18.5,9H13z",
    ),
    fill = SolidColor(Color.Black),
).build()

/** Compare / swap glyph (the Material "swap horiz" path), drawn here because only the core icon set is a dependency. */
private val SwapGlyph: ImageVector = ImageVector.Builder(
    name = "SwapHoriz",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes("M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z"),
    fill = SolidColor(Color.Black),
).build()
