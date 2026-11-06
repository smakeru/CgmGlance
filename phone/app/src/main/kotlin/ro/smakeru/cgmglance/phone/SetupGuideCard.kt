package ro.smakeru.cgmglance.phone

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Step-by-step instructions for installing the watch companion app and setting up the three
 * places where glucose appears: watch face complication, watch tile, and phone widget.
 *
 * Neither Wear OS nor Android discovers these surfaces on the user's behalf after install;
 * someone who installs the phone app and stops here sees only a status screen. Interactive tabs
 * keep all four guides on the main screen without quadrupling its scroll height.
 */
@Composable
fun SetupGuideCard(
    onOpenPlayStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(GuideSurface.INSTALL) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = COLOR_GUIDE_BG),
        border = BorderStroke(1.dp, COLOR_GUIDE_BORDER),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = COLOR_GUIDE_TEXT_PRIMARY,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.guide_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = COLOR_GUIDE_TEXT_SECONDARY,
            )

            Spacer(Modifier.height(14.dp))

            // 2x2 grid of pill tabs so labels stay readable in every language and font scale.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuideTabPill(
                        label = stringResource(GuideSurface.INSTALL.tabLabelRes),
                        selected = selectedTab == GuideSurface.INSTALL,
                        onClick = { selectedTab = GuideSurface.INSTALL },
                        modifier = Modifier.weight(1f),
                    )
                    GuideTabPill(
                        label = stringResource(GuideSurface.COMPLICATION.tabLabelRes),
                        selected = selectedTab == GuideSurface.COMPLICATION,
                        onClick = { selectedTab = GuideSurface.COMPLICATION },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuideTabPill(
                        label = stringResource(GuideSurface.TILE.tabLabelRes),
                        selected = selectedTab == GuideSurface.TILE,
                        onClick = { selectedTab = GuideSurface.TILE },
                        modifier = Modifier.weight(1f),
                    )
                    GuideTabPill(
                        label = stringResource(GuideSurface.WIDGET.tabLabelRes),
                        selected = selectedTab == GuideSurface.WIDGET,
                        onClick = { selectedTab = GuideSurface.WIDGET },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(selectedTab.introRes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = COLOR_BRAND_TEAL,
            )

            Spacer(Modifier.height(12.dp))

            selectedTab.steps.forEachIndexed { index, step ->
                GuideStepRow(
                    number = index + 1,
                    title = stringResource(step.titleRes),
                    body = stringResource(step.bodyRes),
                )
                if (index < selectedTab.steps.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (selectedTab == GuideSurface.INSTALL) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenPlayStore,
                    colors = ButtonDefaults.buttonColors(containerColor = COLOR_BRAND_TEAL),
                ) {
                    Text(stringResource(R.string.action_open_play_store), color = Color.White)
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = COLOR_GUIDE_TIP_BG,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(selectedTab.tipRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = COLOR_GUIDE_TEXT_PRIMARY,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) COLOR_BRAND_TEAL else Color.White,
        border = if (selected) null else BorderStroke(1.dp, COLOR_GUIDE_BORDER),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else COLOR_GUIDE_TEXT_PRIMARY,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GuideStepRow(
    number: Int,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(26.dp)
                    .background(color = COLOR_BRAND_TEAL, shape = CircleShape),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = COLOR_GUIDE_TEXT_PRIMARY,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = COLOR_GUIDE_TEXT_SECONDARY,
            )
        }
    }
}

private data class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private enum class GuideSurface(
    @StringRes val tabLabelRes: Int,
    @StringRes val introRes: Int,
    val steps: List<GuideStep>,
    @StringRes val tipRes: Int,
) {
    INSTALL(
        tabLabelRes = R.string.guide_tab_install,
        introRes = R.string.guide_install_intro,
        steps =
            listOf(
                GuideStep(R.string.guide_install_step1_title, R.string.guide_install_step1_body),
                GuideStep(R.string.guide_install_step2_title, R.string.guide_install_step2_body),
                GuideStep(R.string.guide_install_step3_title, R.string.guide_install_step3_body),
            ),
        tipRes = R.string.guide_install_tip,
    ),
    COMPLICATION(
        tabLabelRes = R.string.guide_tab_complication,
        introRes = R.string.guide_complication_intro,
        steps =
            listOf(
                GuideStep(R.string.guide_complication_step1_title, R.string.guide_complication_step1_body),
                GuideStep(R.string.guide_complication_step2_title, R.string.guide_complication_step2_body),
                GuideStep(R.string.guide_complication_step3_title, R.string.guide_complication_step3_body),
            ),
        tipRes = R.string.guide_complication_tip,
    ),
    TILE(
        tabLabelRes = R.string.guide_tab_tile,
        introRes = R.string.guide_tile_intro,
        steps =
            listOf(
                GuideStep(R.string.guide_tile_step1_title, R.string.guide_tile_step1_body),
                GuideStep(R.string.guide_tile_step2_title, R.string.guide_tile_step2_body),
                GuideStep(R.string.guide_tile_step3_title, R.string.guide_tile_step3_body),
            ),
        tipRes = R.string.guide_tile_tip,
    ),
    WIDGET(
        tabLabelRes = R.string.guide_tab_widget,
        introRes = R.string.guide_widget_intro,
        steps =
            listOf(
                GuideStep(R.string.guide_widget_step1_title, R.string.guide_widget_step1_body),
                GuideStep(R.string.guide_widget_step2_title, R.string.guide_widget_step2_body),
                GuideStep(R.string.guide_widget_step3_title, R.string.guide_widget_step3_body),
            ),
        tipRes = R.string.guide_widget_tip,
    ),
}

private val COLOR_BRAND_TEAL = Color(0xFF0F5F52)
private val COLOR_GUIDE_BG = Color(0xFFF3F8F7)
private val COLOR_GUIDE_BORDER = Color(0xFFD0E2DF)
private val COLOR_GUIDE_TIP_BG = Color(0xFFE2F0ED)
private val COLOR_GUIDE_TEXT_PRIMARY = Color(0xFF142623)
private val COLOR_GUIDE_TEXT_SECONDARY = Color(0xFF425955)
