// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/**
 * The sticky bar under the quest form: a one-line summary of the quest's rules, and the "Create
 * quest" button, enabled when the quest [CreateQuestUiState.canSave], which shows progress while it
 * is saved. If the venue couldn't be read, it says why the button is disabled and offers
 * [onRetryVenue]. The keyboard covers it.
 */
@Composable
internal fun CreateQuestBar(
    state: CreateQuestUiState,
    onSubmit: () -> Unit,
    onRetryVenue: () -> Unit,
) {
  Column(
      Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.background)
          // Not above the keyboard: typing gets the room, and the form can't be saved mid-typing
          .windowInsetsPadding(
              WindowInsets.safeDrawing
                  .exclude(WindowInsets.ime)
                  .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
          )
          .padding(horizontal = AroundSpacing.ScreenHorizontal, vertical = AroundSpacing.Large),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
  ) {
    if (state.venueLoadFailed) VenueLoadFailed(onRetryVenue, retrying = state.retryingVenue)
    Text(
        questSummary(state),
        style = AroundTextStyles.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(C.Tag.CREATE_QUEST_SUMMARY),
    )
    val saving = state.saveState == SaveState.Saving
    val savingLabel = stringResource(R.string.create_quest_saving)
    Button(
        onClick = onSubmit,
        enabled = state.canSave,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth()
                .height(BUTTON_HEIGHT)
                .testTag(C.Tag.CREATE_QUEST_SUBMIT)
                // On the button, not the spinner: the spinner's semantics aren't merged into the
                // button, so TalkBack would read an unlabelled button while it has no text
                .semantics { if (saving) contentDescription = savingLabel },
    ) {
      if (saving) {
        CircularProgressIndicator(
            color = LocalContentColor.current,
            strokeWidth = PROGRESS_STROKE,
            modifier = Modifier.size(PROGRESS_SIZE),
        )
      } else {
        Text(stringResource(R.string.create_quest_submit), style = AroundTextStyles.Button)
      }
    }
  }
}

/**
 * Why the quest can't be saved while the venue is unknown, and a button to read it again, disabled
 * while [retrying].
 */
@Composable
private fun VenueLoadFailed(onRetry: () -> Unit, retrying: Boolean) {
  Text(
      stringResource(R.string.create_quest_venue_failed),
      style = AroundTextStyles.Caption,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag(C.Tag.CREATE_QUEST_VENUE_FAILED),
  )
  TextButton(
      onClick = onRetry,
      enabled = !retrying,
      modifier = Modifier.testTag(C.Tag.CREATE_QUEST_VENUE_RETRY),
      // The default primary colour is unreadable on the dark background
      colors = ButtonDefaults.textButtonColors(contentColor = AroundTheme.colors.accentText),
  ) {
    Text(stringResource(R.string.create_quest_venue_retry), style = AroundTextStyles.Button)
  }
}

/** The quest's rules in a few words, e.g. "Photo proof · 50 m · 4+ people · No reward". */
@Composable
private fun questSummary(state: CreateQuestUiState): String {
  val proof =
      when (state.proofType) {
        ProofType.PHOTO -> R.string.create_quest_summary_photo
        ProofType.TEXT -> R.string.create_quest_summary_text
      }
  val size = state.minPartySize
  val party =
      if (state.exactPartySize) {
        pluralStringResource(R.plurals.create_quest_summary_party_exactly, size, size)
      } else {
        stringResource(R.string.create_quest_summary_party_at_least, size)
      }
  val reward =
      when (state.reward.type) {
        null -> R.string.create_quest_reward_none
        RewardType.DISCOUNT -> R.string.create_quest_reward_discount
        RewardType.FREE_ITEM -> R.string.create_quest_reward_free_item
        RewardType.OTHER -> R.string.create_quest_reward_other
      }
  return listOf(
          stringResource(proof),
          stringResource(R.string.create_quest_meters, state.radiusMeters),
          party,
          stringResource(reward),
      )
      .joinToString(SUMMARY_SEPARATOR)
}

/** Separates the rules in the summary above the button. */
private const val SUMMARY_SEPARATOR = " · "

private val BUTTON_HEIGHT = 54.dp
private val PROGRESS_SIZE = 22.dp
private val PROGRESS_STROKE = 2.dp
