// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChips
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import kotlinx.coroutines.launch

/**
 * The whole of [quest], in a sheet over the venue page: its title and chips, then its full
 * description, what to do, the proof to send and the reward's details. The rows only fit part of a
 * quest, so this is where the rest is read.
 *
 * It closes with its close button, a drag down or system back; each calls [onDismiss] once the
 * sheet is gone.
 *
 * @param quest The quest to show.
 * @param onDismiss Called once the sheet has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestDetailSheet(
    quest: VenuePageQuest,
    onDismiss: () -> Unit,
) {
  // Fully open, with no half-open stop
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(C.Tag.QUEST_DETAIL_SHEET),
      sheetState = sheetState,
      shape = AroundCorners.BottomSheet,
      containerColor = MaterialTheme.colorScheme.surface,
  ) {
    QuestDetails(
        quest,
        onClose = {
          scope
              .launch { sheetState.hide() }
              .invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
        },
    )
  }
}

/** What the sheet shows of [quest], under a close button that calls [onClose]. */
@Composable
private fun QuestDetails(quest: VenuePageQuest, onClose: () -> Unit) {
  Column(
      Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(
              start = AroundSpacing.ScreenHorizontal,
              end = AroundSpacing.ScreenHorizontal,
              bottom = AroundSpacing.ScreenBottom,
          ),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.SectionGap),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium)) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
          verticalAlignment = Alignment.Top,
      ) {
        Text(
            quest.title,
            Modifier.weight(1f).semantics { heading() }.testTag(C.Tag.QUEST_DETAIL_TITLE),
            color = MaterialTheme.colorScheme.onSurface,
            style = AroundTextStyles.SheetTitle,
        )
        CloseButton(onClose)
      }
      QuestChips(quest.reward, quest.minPartySize, distanceMeters = null, venueQuestChipStyle())
    }
    if (quest.description.isNotBlank()) {
      Section(R.string.quest_detail_about, quest.description, C.Tag.QUEST_DETAIL_DESCRIPTION)
    }
    if (quest.requirements.isNotBlank()) {
      Section(R.string.quest_detail_to_do, quest.requirements, C.Tag.QUEST_DETAIL_REQUIREMENTS)
    }
    Proof(quest.proofType)
    rewardSpecifics(quest.reward)?.let {
      Section(R.string.quest_detail_reward, it, C.Tag.QUEST_DETAIL_REWARD_SPECIFICS)
    }
  }
}

/** The round button that closes the sheet. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  OutlinedIconButton(
      onClose,
      Modifier.size(CLOSE_SIZE).testTag(C.Tag.QUEST_DETAIL_CLOSE),
      shape = CircleShape,
      colors =
          IconButtonDefaults.outlinedIconButtonColors(
              containerColor = colors.background,
              contentColor = colors.onSurface,
          ),
      border = BorderStroke(1.dp, colors.outline),
  ) {
    Icon(
        painterResource(R.drawable.ic_close),
        stringResource(R.string.quest_detail_close),
        // The icon is padded within its box, like the map's close button
        Modifier.size(CLOSE_SIZE),
    )
  }
}

/** A part of the quest: its [label] over [text], tagged [tag]. */
@Composable
private fun Section(label: Int, text: String, tag: String) {
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall)) {
    SectionLabel(label)
    Text(
        text,
        Modifier.testTag(tag),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = AroundTextStyles.Description,
    )
  }
}

/** The proof explorers send to complete the quest, with its icon. */
@Composable
private fun Proof(proofType: ProofType) {
  val (icon, text) =
      when (proofType) {
        ProofType.PHOTO -> R.drawable.ic_proof_photo to R.string.quest_detail_proof_photo
        ProofType.TEXT -> R.drawable.ic_proof_text to R.string.quest_detail_proof_text
      }
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall)) {
    SectionLabel(R.string.quest_detail_proof)
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(icon),
          contentDescription = null,
          Modifier.size(PROOF_ICON_SIZE),
          tint = MaterialTheme.colorScheme.tertiary,
      )
      Text(
          stringResource(text),
          Modifier.testTag(C.Tag.QUEST_DETAIL_PROOF),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.Description,
      )
    }
  }
}

/** The heading of a section, [label]. */
@Composable
private fun SectionLabel(label: Int) {
  Text(
      stringResource(label),
      Modifier.semantics { heading() },
      color = MaterialTheme.colorScheme.onSurface,
      style = AroundTextStyles.Subtitle,
  )
}

/** What [reward] applies to, beyond what its chip says, or null when there is nothing more. */
internal fun rewardSpecifics(reward: Reward?): String? =
    when (reward) {
      is Reward.Discount -> reward.specifics
      is Reward.FreeItem -> reward.specifics
      is Reward.Other,
      null -> null
    }?.takeIf { it.isNotBlank() }

private val CLOSE_SIZE = 36.dp
private val PROOF_ICON_SIZE = 20.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QuestDetailsPreview() {
  AroundAppTheme {
    QuestDetails(
        VenuePageQuest(
            questId = "quest",
            title = "Find the hidden fox",
            description =
                "Spot the little ceramic fox hidden somewhere in the café and snap a photo.",
            requirements = "Show the photo at the counter before you leave.",
            proofType = ProofType.PHOTO,
            minPartySize = 2,
            reward = Reward.FreeItem("croissant", specifics = "Plain or almond"),
        ),
        onClose = {},
    )
  }
}
