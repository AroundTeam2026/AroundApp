// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewQuestRow
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.aroundFieldColors

/**
 * The Join a party screen, as the Figma frames "Join a party": the explorer types a party code,
 * sees the party it leads to, or why it can't be joined, and joins it.
 *
 * @param onBack Closes the screen, on its back arrow.
 * @param onJoined Called once the explorer has joined the party, to close the screen: its quest
 *   then shows on the Quests tab.
 */
@Composable
fun JoinPartyScreen(
    viewModel: JoinPartyViewModel,
    onBack: () -> Unit,
    onJoined: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.join) { if (state.join == JoinPartyUiState.Join.JOINED) onJoined() }
  JoinPartyContent(state, viewModel::updateCode, viewModel::join, onBack, modifier)
}

/**
 * Stateless Join a party screen: the back arrow, the title and what to do, the code field with the
 * party it leads to or the reason it can't be joined below it, and the button.
 *
 * @param onCodeChange Called with the code as the explorer edits it.
 * @param onSubmit Called when the explorer taps the button, or the keyboard's Done key.
 */
@Composable
fun JoinPartyContent(
    state: JoinPartyUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val joining = state.join == JoinPartyUiState.Join.JOINING
  Scaffold(
      modifier = modifier.testTag(C.Tag.JOIN_PARTY_SCREEN).imePadding(),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(AroundSpacing.SectionGap)) {
          SubmitButton(state, onSubmit)
        }
      },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(AroundSpacing.ScreenHorizontal, AroundSpacing.SectionGap),
    ) {
      OutlinedIconButton(
          onClick = onBack,
          enabled = !joining,
          modifier = Modifier.size(CONTROL_SIZE),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.join_party_back))
      }
      Spacer(Modifier.size(AroundSpacing.HeadingGap))
      Text(
          stringResource(R.string.join_party_title),
          Modifier.semantics { heading() },
          style = AroundTextStyles.SetupTitle,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.size(AroundSpacing.CompactGap))
      Text(
          stringResource(R.string.join_party_body),
          style = AroundTextStyles.BodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.size(AroundSpacing.SetupGap))
      CodeField(state, onCodeChange, onSubmit)
      LookupResult(state.lookup)
    }
  }
}

/** The labelled party code field, red once the code can't be joined. */
@Composable
private fun CodeField(
    state: JoinPartyUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
    Text(
        stringResource(R.string.join_party_code_label),
        style = AroundTextStyles.FieldLabel,
        color = MaterialTheme.colorScheme.onBackground,
    )
    OutlinedTextField(
        value = state.code,
        onValueChange = onCodeChange,
        modifier =
            Modifier.fillMaxWidth().heightIn(min = FIELD_MIN_HEIGHT).testTag(C.Tag.JOIN_PARTY_CODE),
        enabled = state.join == JoinPartyUiState.Join.IDLE,
        isError = state.lookup is JoinPartyUiState.Lookup.Refused,
        singleLine = true,
        textStyle = AroundTextStyles.FieldValue,
        shape = MaterialTheme.shapes.small,
        colors = aroundFieldColors(),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
  }
}

/** Below the field: the party being looked for, the one found with its card, or why not. */
@Composable
private fun LookupResult(lookup: JoinPartyUiState.Lookup) {
  when (lookup) {
    JoinPartyUiState.Lookup.Idle -> Unit
    JoinPartyUiState.Lookup.Looking ->
        Row(
            Modifier.padding(top = AroundSpacing.Small).semantics {
              liveRegion = LiveRegionMode.Polite
            },
            horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(
              Modifier.size(PROGRESS_SIZE).testTag(C.Tag.JOIN_PARTY_LOOKING),
              strokeWidth = PROGRESS_STROKE,
          )
          Text(
              stringResource(R.string.join_party_looking),
              style = AroundTextStyles.Caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
    is JoinPartyUiState.Lookup.Found ->
        Column(
            Modifier.padding(top = AroundSpacing.ExtraLarge).testTag(C.Tag.JOIN_PARTY_FOUND),
            verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
        ) {
          Text(
              stringResource(R.string.join_party_you_are_joining),
              style = AroundTextStyles.Label,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OverviewQuestRow(lookup.party, onClick = null)
        }
    is JoinPartyUiState.Lookup.Refused -> ErrorLine(lookup.error)
  }
}

/** The reason a party can't be joined, with the info mark, in the place of the party's card. */
@Composable
private fun ErrorLine(error: JoinPartyUiState.Error) {
  val message =
      when (error) {
        JoinPartyUiState.Error.NotFound -> stringResource(R.string.join_party_not_found)
        is JoinPartyUiState.Error.Full ->
            stringResource(R.string.join_party_full, error.joined, error.capacity)
        JoinPartyUiState.Error.Ended -> stringResource(R.string.join_party_ended)
        JoinPartyUiState.Error.Closed -> stringResource(R.string.join_party_closed)
        JoinPartyUiState.Error.AlreadyJoined -> stringResource(R.string.join_party_already_joined)
        JoinPartyUiState.Error.Left -> stringResource(R.string.join_party_left)
        JoinPartyUiState.Error.Offline -> stringResource(R.string.join_party_offline)
        JoinPartyUiState.Error.Failed -> stringResource(R.string.join_party_failed)
      }
  Row(
      Modifier.padding(top = AroundSpacing.Small)
          .semantics { liveRegion = LiveRegionMode.Polite }
          .testTag(C.Tag.JOIN_PARTY_ERROR),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        painterResource(R.drawable.ic_info_filled),
        contentDescription = null,
        modifier = Modifier.size(INFO_SIZE),
        tint = MaterialTheme.colorScheme.error,
    )
    Text(message, style = AroundTextStyles.Caption, color = MaterialTheme.colorScheme.error)
  }
}

/** The bottom button: joins the party found, or looks the typed code up again. */
@Composable
private fun SubmitButton(state: JoinPartyUiState, onSubmit: () -> Unit) {
  val joining = state.join == JoinPartyUiState.Join.JOINING
  Button(
      onClick = onSubmit,
      enabled = state.canSubmit,
      colors =
          ButtonDefaults.buttonColors(
              disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
              disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
          ),
      shape = MaterialTheme.shapes.medium,
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = SUBMIT_MIN_HEIGHT)
              .testTag(C.Tag.JOIN_PARTY_SUBMIT),
  ) {
    if (joining) {
      CircularProgressIndicator(
          Modifier.size(PROGRESS_SIZE),
          color = LocalContentColor.current,
          strokeWidth = PROGRESS_STROKE,
      )
      Spacer(Modifier.width(AroundSpacing.Small))
    }
    Text(
        stringResource(if (joining) R.string.join_party_joining else R.string.join_party_join),
        style = AroundTextStyles.Button,
    )
  }
}

private val CONTROL_SIZE = 44.dp
private val FIELD_MIN_HEIGHT = 52.dp
private val SUBMIT_MIN_HEIGHT = 54.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp
private val INFO_SIZE = 16.dp
