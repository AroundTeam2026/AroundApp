// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.MAX_TEXT_ANSWER_LENGTH
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.OfflineHint
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.navigation.OfflineBanner
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** The proof form. Navigation supplies the reservation's ViewModel and the venue's display name. */
@Composable
fun CompletionScreen(
    viewModel: CompletionViewModel,
    venueName: String,
    onBack: () -> Unit,
    onSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val currentOnSubmitted by rememberUpdatedState(onSubmitted)
  val permission = rememberCompletionPermission { if (state.canSubmit) viewModel.submit() }
  LaunchedEffect(state.saveState) {
    (state.saveState as? SaveState.Saved)?.let { currentOnSubmitted(it.id) }
  }
  BackHandler(state.saveState == SaveState.Saving) {}
  CompletionContent(
      state = state,
      venueName = venueName,
      onAnswerChange = viewModel::updateAnswer,
      onSubmit = permission.submit,
      onBack = onBack,
      onRetry = viewModel::onRetry,
      onRequestLocation = permission.request,
      preciseLocationRefused = permission.refused,
      locationServicesEnabled = permission.servicesEnabled,
      modifier = modifier,
      photoPicker = {
        ProofPhotoPicker(
            venueName,
            state.photo,
            viewModel::pickPhoto,
            { viewModel.pickPhoto(null) },
            enabled = state.isEditable,
            questTitle = state.questTitle,
        )
      },
  )
}

/** Stateless screen; the photo slot is shared with #330 and replaceable in previews and tests. */
@Composable
fun CompletionContent(
    state: CompletionUiState,
    venueName: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
    preciseLocationRefused: Boolean = false,
    locationServicesEnabled: Boolean = true,
    photoPicker: @Composable () -> Unit,
) {
  val showOfflineHint = state.isEditable && state.photoSentLater
  Scaffold(
      modifier = modifier.testTag(C.Tag.COMPLETION_SCREEN).imePadding(),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        if (state.status == CompletionFormStatus.READY) {
          CompletionBottomBar(state, venueName, onSubmit)
        }
      },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AroundSpacing.SectionGap, vertical = AroundSpacing.SectionGap),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
    ) {
      CompletionHeader(
          onBack = onBack,
          backEnabled = state.saveState != SaveState.Saving && state.saveState !is SaveState.Saved,
      )
      // The app-wide banner, unless the hint already says the device is offline
      if (state.offline && !showOfflineHint) OfflineBanner()
      when (state.status) {
        CompletionFormStatus.LOADING ->
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        CompletionFormStatus.FAILED -> {
          CompletionMessage(stringResource(R.string.completion_load_failed))
          TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
        CompletionFormStatus.UNAVAILABLE ->
            CompletionMessage(stringResource(R.string.completion_unavailable))
        CompletionFormStatus.READY -> {
          if (showOfflineHint) OfflineHint(message = R.string.offline_photo_sent_later)
          CompletionQuestTitle(venueName, state.questTitle)
          if (state.requirements.isNotBlank()) CompletionRequirements(state.requirements)
          when (state.proofType) {
            ProofType.TEXT -> CompletionAnswerField(state, onAnswerChange)
            ProofType.PHOTO -> photoPicker()
            null -> Unit
          }
          CompletionLocation(
              state,
              preciseLocationRefused,
              locationServicesEnabled,
              onRequestLocation,
          )
          if (
              state.saveState == SaveState.Failed &&
                  state.locationError == null &&
                  !preciseLocationRefused
          ) {
            CompletionSaveError()
          }
        }
      }
    }
  }
}

/** The hint above the send button, and the button itself, which shows how the send is going. */
@Composable
private fun CompletionBottomBar(state: CompletionUiState, venueName: String, onSubmit: () -> Unit) {
  Column(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(AroundSpacing.SectionGap),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    Text(
        when {
          state.sentByTeammate -> stringResource(R.string.completion_sent_by_teammate)
          state.sendingLater -> stringResource(R.string.waiting_to_send_note)
          state.photoWaiting -> stringResource(R.string.completion_photo_waiting)
          else -> stringResource(R.string.completion_review_hint, venueName)
        },
        Modifier.fillMaxWidth().testTag(C.Tag.COMPLETION_HINT),
        style = AroundTextStyles.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    CompletionSubmitButton(state, onSubmit)
  }
}

@Composable
private fun CompletionSubmitButton(state: CompletionUiState, onSubmit: () -> Unit) {
  Button(
      onClick = onSubmit,
      enabled = state.canSubmit,
      colors =
          if (state.sendingLater) {
            // Saved, not unavailable: container colours instead of the faded ones
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          } else {
            ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
            )
          },
      shape = MaterialTheme.shapes.medium,
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = SUBMIT_MIN_HEIGHT)
              .testTag(C.Tag.COMPLETION_SUBMIT),
  ) {
    if (state.saveState == SaveState.Saving) {
      CircularProgressIndicator(
          Modifier.size(PROGRESS_SIZE),
          color = LocalContentColor.current,
          strokeWidth = PROGRESS_STROKE,
      )
      Spacer(Modifier.width(AroundSpacing.Small))
    }
    if (state.sendingLater) {
      Icon(
          painterResource(R.drawable.ic_cloud_upload),
          contentDescription = null,
          Modifier.size(SENDING_ICON_SIZE),
      )
      Spacer(Modifier.width(AroundSpacing.Small))
    }
    Text(stringResource(submitLabel(state)), style = AroundTextStyles.Button)
  }
}

/** What the send button says for [state]. */
private fun submitLabel(state: CompletionUiState): Int =
    when {
      state.saveState == SaveState.Saving -> R.string.completion_sending
      state.saveState is SaveState.Saved || state.sentByTeammate -> R.string.completion_sent
      state.sendingLater -> R.string.waiting_to_send
      state.saveState == SaveState.Failed -> R.string.try_again
      else -> R.string.completion_send
    }

/** The back button and the screen's title. */
@Composable
private fun CompletionHeader(onBack: () -> Unit, backEnabled: Boolean) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    OutlinedIconButton(
        onClick = onBack,
        enabled = backEnabled,
        modifier = Modifier.align(Alignment.CenterStart).size(HEADER_CONTROL_SIZE),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Icon(
          painterResource(R.drawable.ic_chevron_left),
          stringResource(R.string.completion_back),
      )
    }
    Text(stringResource(R.string.completion_heading), style = AroundTextStyles.CardTitle)
  }
}

/** The venue's initials and name, above the quest's title. */
@Composable
private fun CompletionQuestTitle(venueName: String, questTitle: String) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
        modifier = Modifier.size(HEADER_CONTROL_SIZE),
        shape = AroundPillShape,
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
            initialsOf(venueName),
            style = AroundTextStyles.AvatarSmall,
        )
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TITLE_GAP)) {
      Text(
          venueName,
          style = AroundTextStyles.Caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          questTitle,
          Modifier.semantics { heading() },
          style = AroundTextStyles.OverlayTitle,
          color = MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}

/** The panel listing what the quest asks for. */
@Composable
private fun CompletionRequirements(requirements: String) {
  Surface(
      shape = MaterialTheme.shapes.large,
      color = AroundTheme.colors.panel,
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
        Modifier.fillMaxWidth().padding(AroundSpacing.InsetPadding),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
    ) {
      Text(
          stringResource(R.string.completion_requirements),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.Label,
      )
      Text(requirements, style = AroundTextStyles.Body)
    }
  }
}

/** The text proof's label and field, with an error under it when the answer is too long. */
@Composable
private fun CompletionAnswerField(state: CompletionUiState, onAnswerChange: (String) -> Unit) {
  // The label is a separate Text, so TalkBack gets it as the field's name
  val answerLabel = stringResource(R.string.completion_answer)
  Text(answerLabel, style = AroundTextStyles.FieldLabel)
  OutlinedTextField(
      value = state.answer,
      onValueChange = onAnswerChange,
      enabled = state.isEditable,
      isError = state.answerTooLong,
      supportingText =
          if (state.answerTooLong) {
            {
              CompletionMessage(
                  pluralStringResource(
                      R.plurals.completion_answer_too_long,
                      MAX_TEXT_ANSWER_LENGTH,
                      MAX_TEXT_ANSWER_LENGTH,
                  )
              )
            }
          } else null,
      placeholder = { Text(stringResource(R.string.completion_answer_hint)) },
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = ANSWER_MIN_HEIGHT)
              .testTag(C.Tag.COMPLETION_ANSWER)
              .semantics { contentDescription = answerLabel },
      shape = MaterialTheme.shapes.small,
      textStyle = AroundTextStyles.TextArea,
      colors =
          OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surface
          ),
  )
}

/** Why the location check failed, if it did, and the button that fixes it when one can. */
@Composable
private fun CompletionLocation(
    state: CompletionUiState,
    preciseLocationRefused: Boolean,
    locationServicesEnabled: Boolean,
    onRequestLocation: () -> Unit,
) {
  if (preciseLocationRefused) {
    CompletionMessage(stringResource(R.string.completion_precise_required))
  } else {
    when (state.locationError) {
      CompletionLocationError.TOO_FAR ->
          CompletionMessage(stringResource(R.string.completion_too_far))
      CompletionLocationError.UNAVAILABLE ->
          CompletionMessage(stringResource(R.string.completion_location_unavailable))
      null -> Unit
    }
  }
  if (preciseLocationRefused || state.locationError == CompletionLocationError.UNAVAILABLE) {
    OutlinedButton(
        onClick = onRequestLocation,
        enabled = state.isEditable,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AroundTheme.colors.accentText),
    ) {
      Text(
          stringResource(
              when {
                !locationServicesEnabled -> R.string.completion_location_settings
                preciseLocationRefused -> R.string.completion_enable_location
                else -> R.string.completion_check_location
              }
          )
      )
    }
  }
}

@Composable
private fun CompletionMessage(message: String) {
  Text(
      message,
      Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      style = AroundTextStyles.Caption,
      color = MaterialTheme.colorScheme.error,
  )
}

/** The outlined error panel from the send-failed frame. The proof stays editable for retry. */
@Composable
private fun CompletionSaveError() {
  Surface(
      modifier =
          Modifier.fillMaxWidth().testTag(C.Tag.COMPLETION_SAVE_ERROR).semantics(
              mergeDescendants = true
          ) {
            liveRegion = LiveRegionMode.Polite
          },
      shape = AroundCorners.LocationBanner,
      color = AroundTheme.colors.panel,
      contentColor = MaterialTheme.colorScheme.error,
      border = BorderStroke(ERROR_BORDER_WIDTH, MaterialTheme.colorScheme.error),
  ) {
    Text(
        stringResource(R.string.completion_save_failed),
        Modifier.padding(horizontal = AroundSpacing.Large, vertical = AroundSpacing.InsetPadding),
        style = AroundTextStyles.BodyMedium,
    )
  }
}

private val HEADER_CONTROL_SIZE = 44.dp
private val SUBMIT_MIN_HEIGHT = 54.dp
private val ANSWER_MIN_HEIGHT = 150.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp
private val SENDING_ICON_SIZE = 20.dp
private val TITLE_GAP = 2.dp
private val ERROR_BORDER_WIDTH = 1.dp

@Preview(name = "Text - Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Text - Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun CompletionTextPreview() {
  CompletionPreview(ProofType.TEXT)
}

@Preview(name = "Photo - Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Photo - Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun CompletionPhotoPreview() {
  CompletionPreview(ProofType.PHOTO)
}

@Composable
private fun CompletionPreview(proofType: ProofType) {
  AroundAppTheme {
    CompletionContent(
        CompletionUiState(
            status = CompletionFormStatus.READY,
            questTitle = "Find the hidden fox",
            proofType = proofType,
            requirements = "Order any drink. One proof per party.",
        ),
        "Café Lumière",
        {},
        {},
        {},
        {},
        {},
        photoPicker = { ProofPhotoBox("Café Lumière", false, null, false, {}, {}, {}, {}) },
    )
  }
}
