// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import java.time.Clock

/**
 * One proof waiting for the venue on the dashboard, as the Figma component `S4 · Proof row`: the
 * explorer's initials, their name, the quest and party, what kind of proof it is and when it was
 * sent, then the kind of proof as an icon and a chevron. The whole row opens the proof.
 *
 * @param proof The proof to draw.
 * @param onOpen Opens the proof, to approve or reject it.
 * @param clock Tells how long ago it was sent; read again each time the minute shown changes, so a
 *   dashboard left open keeps up.
 */
@Composable
internal fun DashboardProofRow(
    proof: DashboardProof,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
  val colors = MaterialTheme.colorScheme
  val now = rememberNow(proof.sentAt, clock)
  Row(
      modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.large)
          .background(colors.surface)
          .clickable(onClickLabel = stringResource(R.string.dashboard_proof_open), onClick = onOpen)
          .padding(horizontal = AroundSpacing.Large, vertical = AroundSpacing.InsetPadding)
          .testTag("${C.Tag.DASHBOARD_PROOF_ROW}_${proof.completionId}"),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // The name beside it says the same, so screen readers skip the initials
    Box(
        Modifier.size(AVATAR_SIZE)
            .background(colors.primary, AroundPillShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
      Text(
          proof.explorerName?.let(::initialsOf)
              ?: stringResource(R.string.dashboard_unknown_initials),
          color = colors.onPrimary,
          style = AroundTextStyles.AvatarSmall,
          maxLines = 1,
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
      Text(
          proof.explorerName ?: stringResource(R.string.dashboard_unknown_explorer),
          Modifier.testTag(C.Tag.DASHBOARD_PROOF_EXPLORER),
          color = colors.onSurface,
          style = AroundTextStyles.RowTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          detailsText(proof),
          Modifier.testTag(C.Tag.DASHBOARD_PROOF_DETAILS),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Caption,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          sentText(proof, now),
          Modifier.testTag(C.Tag.DASHBOARD_PROOF_SENT),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Small,
          maxLines = 1,
      )
    }
    ProofIcon(proof.proofType)
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        Modifier.size(CHEVRON_SIZE),
        tint = colors.onSurfaceVariant,
    )
  }
}

/**
 * "Find the hidden fox · Party of 4", or "… · Solo visit" for one explorer. A party of 0, as for a
 * proof whose reservation can't be read, is left out.
 */
@Composable
private fun detailsText(proof: DashboardProof): String {
  val quest = proof.questTitle ?: stringResource(R.string.dashboard_unknown_quest)
  val party =
      when {
        proof.partySize <= 0 -> null
        proof.partySize == 1 -> stringResource(R.string.dashboard_proof_solo)
        else ->
            pluralStringResource(R.plurals.dashboard_party_size, proof.partySize, proof.partySize)
      }
  return if (party == null) quest
  else stringResource(R.string.dashboard_proof_quest_and_party, quest, party)
}

/**
 * "Photo · sent 2 min. ago", in the phone's language; "sent just now" under a minute, rather than
 * "0 min. ago".
 */
@Composable
private fun sentText(proof: DashboardProof, now: Long): String {
  val kind =
      stringResource(
          when (proof.proofType) {
            ProofType.PHOTO -> R.string.dashboard_proof_photo
            ProofType.TEXT -> R.string.dashboard_proof_answer
          }
      )
  return if (now - proof.sentAt < DateUtils.MINUTE_IN_MILLIS) {
    stringResource(R.string.dashboard_proof_sent_just_now, kind)
  } else {
    stringResource(
        R.string.dashboard_proof_sent,
        kind,
        DateUtils.getRelativeTimeSpanString(
                proof.sentAt,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
            .toString(),
    )
  }
}

/**
 * The kind of proof as an icon in a tile: a camera on sage for a photo, a speech bubble on the
 * panel colour for a written answer. The text under the name says the same, so it has no
 * description.
 */
@Composable
private fun ProofIcon(type: ProofType) {
  // An if rather than a when on the type: a when gets a branch for unknown values that no test
  // can reach
  val photo = type == ProofType.PHOTO
  val icon = if (photo) R.drawable.ic_proof_photo else R.drawable.ic_proof_text
  val background = if (photo) AroundTheme.colors.sage else AroundTheme.colors.panel
  val colors = MaterialTheme.colorScheme
  val tint = if (photo) colors.onPrimary else colors.onSurface
  Box(
      Modifier.size(PROOF_TILE_SIZE)
          .background(background, PROOF_TILE_SHAPE)
          .testTag(C.Tag.DASHBOARD_PROOF_ICON),
      contentAlignment = Alignment.Center,
  ) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(PROOF_ICON_SIZE), tint)
  }
}

private val TEXT_GAP = 2.dp
private val AVATAR_SIZE = 40.dp
private val PROOF_TILE_SIZE = 48.dp
private val PROOF_TILE_SHAPE = RoundedCornerShape(12.dp)
private val PROOF_ICON_SIZE = 22.dp
private val CHEVRON_SIZE = 20.dp

// Internal, not private, so a test can draw it: the previews are code too
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DashboardProofRowPreview() {
  val now = System.currentTimeMillis()
  AroundAppTheme {
    Column(
        Modifier.background(AroundTheme.colors.panel).padding(AroundSpacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      DashboardProofRow(
          DashboardProof(
              completionId = "photo",
              explorerName = "Yusuf S.",
              questTitle = "Find the hidden fox",
              partySize = 4,
              proofType = ProofType.PHOTO,
              sentAt = now - 2 * DateUtils.MINUTE_IN_MILLIS,
          ),
          onOpen = {},
      )
      DashboardProofRow(
          DashboardProof(
              completionId = "text",
              explorerName = "Nora B.",
              questTitle = "Name the café cat",
              partySize = 1,
              proofType = ProofType.TEXT,
              sentAt = now - 10 * DateUtils.MINUTE_IN_MILLIS,
          ),
          onOpen = {},
      )
    }
  }
}
