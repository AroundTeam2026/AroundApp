// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.currentLocale
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * Why the venue rejected a proof, as the Figma component `M2 · Rejected proof row` shows it:
 * "REJECTED" over [reason], in a box outlined in the error colour. [reason] is shown as stored,
 * with the venue's note after the preset reason when it wrote one.
 */
@Composable
internal fun RejectedReason(reason: String, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier
          .fillMaxWidth()
          .border(BORDER_WIDTH, colors.error, AroundCorners.InsetField)
          .padding(horizontal = AroundSpacing.Medium, vertical = VERTICAL_PADDING)
          .testTag(C.Tag.PROOF_REJECT_REASON),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
  ) {
    Text(
        stringResource(R.string.review_proof_rejected).uppercase(currentLocale()),
        color = colors.error,
        style = AroundTextStyles.Label,
    )
    Text(reason, color = colors.onSurface, style = AroundTextStyles.BodyMedium)
  }
}

private val BORDER_WIDTH = 1.dp
private val VERTICAL_PADDING = 10.dp
