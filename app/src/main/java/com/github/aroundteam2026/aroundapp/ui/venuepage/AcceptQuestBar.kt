// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * The bar under a venue's quests, once the explorer picked one: a full-width "Accept quest" button,
 * styled like the one in the quest popup of the Figma frame, and what [state] says about accepting.
 *
 * - Ready: the button calls [onAccept].
 * - Accepting: a pulsing skeleton takes the button's place, and can't be tapped.
 * - Accepted: the button is disabled and says the venue is yet to approve.
 * - Failed: an error shows above the button, which now says "Try again" and calls [onAccept].
 * - A quest that needs more people says how many, whatever the state: it can still be accepted.
 * - Accepting unavailable: the button is disabled and says why.
 *
 * It draws nothing while no quest is picked. It only shows the state: [onAccept] is the
 * ViewModel's.
 */
@Composable
fun AcceptQuestBar(state: VenuePageUiState, onAccept: () -> Unit, modifier: Modifier = Modifier) {
  if (state.selectedQuest == null) return
  val status = state.acceptStatus
  Column(
      modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface, AroundCorners.BottomSheet)
          .padding(AroundSpacing.CardPadding)
          .testTag(C.Tag.ACCEPT_QUEST_BAR),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (!state.acceptAvailable) {
      Note(stringResource(R.string.accept_quest_unavailable), C.Tag.ACCEPT_QUEST_UNAVAILABLE)
    }
    if (status == AcceptStatus.FAILED) {
      Note(
          stringResource(R.string.accept_quest_failed),
          C.Tag.ACCEPT_QUEST_FAILED,
          MaterialTheme.colorScheme.error,
      )
    }
    val more = state.morePeopleNeeded
    if (more > 0) {
      Note(
          pluralStringResource(R.plurals.accept_quest_needs_more, more, more),
          C.Tag.ACCEPT_QUEST_NEEDS_MORE,
      )
    }
    if (status == AcceptStatus.ACCEPTING) {
      AcceptingSkeleton(stringResource(R.string.accept_quest_accepting))
    } else {
      AcceptButton(status, enabled = state.acceptAvailable, onAccept)
    }
  }
}

/** The button: "Accept quest", "Try again" after a failure, or disabled once accepted. */
@Composable
private fun AcceptButton(status: AcceptStatus, enabled: Boolean, onAccept: () -> Unit) {
  Button(
      onClick = onAccept,
      enabled = enabled && status != AcceptStatus.ACCEPTED,
      shape = AroundPillShape,
      // Done, not unavailable: readable in the soft colour of a finished thing, not Material's
      // faded
      colors =
          if (status == AcceptStatus.ACCEPTED) {
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          } else {
            ButtonDefaults.buttonColors()
          },
      modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT).testTag(C.Tag.ACCEPT_QUEST_BUTTON),
  ) {
    when (status) {
      AcceptStatus.ACCEPTED -> {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(16.dp))
        Spacer(Modifier.width(AroundSpacing.Small))
        Text(stringResource(R.string.accept_quest_accepted), style = AroundTextStyles.Button)
      }
      AcceptStatus.FAILED ->
          Text(stringResource(R.string.accept_quest_retry), style = AroundTextStyles.Button)
      else -> Text(stringResource(R.string.accept_quest), style = AroundTextStyles.Button)
    }
  }
}

/**
 * A pulsing placeholder in the shape of the button while the quest is being accepted, in place of a
 * spinner. It stands for the button: same tag and size, disabled, described as [description].
 */
@Composable
private fun AcceptingSkeleton(description: String) {
  val pulse = rememberInfiniteTransition(label = "accepting")
  val alpha by
      pulse.animateFloat(
          initialValue = SKELETON_ALPHA_LOW,
          targetValue = SKELETON_ALPHA_HIGH,
          animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
          label = "accepting alpha",
      )
  Box(
      Modifier.fillMaxWidth()
          .height(BUTTON_HEIGHT)
          .clip(AroundPillShape)
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
          .testTag(C.Tag.ACCEPT_QUEST_BUTTON)
          .semantics {
            contentDescription = description
            disabled()
          }
  )
}

/** A line of explanation above the button. */
@Composable
private fun Note(
    text: String,
    tag: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Text(
      text,
      style = AroundTextStyles.Caption,
      color = color,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag(tag),
  )
}

/** The popup's "Accept quest" button is 52 dp high. */
private val BUTTON_HEIGHT = 52.dp
private const val SKELETON_PULSE_MS = 800
private const val SKELETON_ALPHA_LOW = 0.14f
private const val SKELETON_ALPHA_HIGH = 0.32f
