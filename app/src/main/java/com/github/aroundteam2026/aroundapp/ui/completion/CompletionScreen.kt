// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.theme.*

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
      modifier = modifier,
      photoPicker = {
        ProofPhotoPicker(
            venueName,
            state.photo,
            viewModel::pickPhoto,
            { viewModel.pickPhoto(null) },
            enabled = state.isEditable,
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
    photoPicker: @Composable () -> Unit,
) {
  val saving = state.saveState == SaveState.Saving
  val saved = state.saveState is SaveState.Saved
  Scaffold(
      modifier = modifier.testTag("completion_screen").imePadding(),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        if (state.status == CompletionFormStatus.READY) {
          Column(
              Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
                stringResource(R.string.completion_review_hint, venueName),
                Modifier.fillMaxWidth(),
                style = AroundTextStyles.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor =
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        disabledContentColor =
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                    ),
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("completion_submit"),
            ) {
              if (saving) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
              }
              Text(
                  stringResource(
                      if (saving) R.string.completion_sending
                      else if (saved) R.string.completion_sent else R.string.completion_send
                  ),
                  style = AroundTextStyles.Button,
              )
            }
          }
        }
      },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedIconButton(
            onClick = onBack,
            enabled = !saving && !saved,
            modifier = Modifier.align(Alignment.CenterStart).size(44.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Icon(
              painterResource(R.drawable.venue_setup_back),
              stringResource(R.string.completion_back),
          )
        }
        Text(stringResource(R.string.completion_heading), style = AroundTextStyles.CardTitle)
      }
      when (state.status) {
        CompletionFormStatus.LOADING ->
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        CompletionFormStatus.FAILED -> {
          CompletionMessage(stringResource(R.string.completion_load_failed))
          TextButton(onClick = onRetry) { Text(stringResource(R.string.completion_retry)) }
        }
        CompletionFormStatus.UNAVAILABLE ->
            CompletionMessage(stringResource(R.string.completion_unavailable))
        CompletionFormStatus.READY -> {
          Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = AroundPillShape,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                    venueName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.take(1) }
                        .uppercase(),
                    style = AroundTextStyles.AvatarSmall,
                )
              }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                  venueName,
                  style = AroundTextStyles.Caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                  state.questTitle,
                  Modifier.semantics { heading() },
                  style = AroundTextStyles.OverlayTitle,
                  color = MaterialTheme.colorScheme.onBackground,
              )
            }
          }
          if (state.requirements.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = AroundTheme.colors.panel,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
              Column(
                  Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                    stringResource(R.string.completion_requirements),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = AroundTextStyles.Label,
                )
                Text(state.requirements, style = AroundTextStyles.Body)
              }
            }
          }
          when (state.proofType) {
            ProofType.TEXT -> {
              Text(stringResource(R.string.completion_answer), style = AroundTextStyles.FieldLabel)
              OutlinedTextField(
                  value = state.answer,
                  onValueChange = onAnswerChange,
                  enabled = state.isEditable,
                  placeholder = { Text(stringResource(R.string.completion_answer_hint)) },
                  modifier =
                      Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("completion_answer"),
                  shape = MaterialTheme.shapes.small,
                  textStyle = AroundTextStyles.TextArea,
                  colors =
                      OutlinedTextFieldDefaults.colors(
                          unfocusedContainerColor = MaterialTheme.colorScheme.surface
                      ),
              )
            }
            ProofType.PHOTO -> photoPicker()
            null -> Unit
          }
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
          if (
              preciseLocationRefused || state.locationError == CompletionLocationError.UNAVAILABLE
          ) {
            OutlinedButton(
                onClick = onRequestLocation,
                enabled = state.isEditable,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = AroundTheme.colors.accentText
                    ),
            ) {
              Text(stringResource(R.string.completion_enable_location))
            }
          }
          if (
              state.saveState == SaveState.Failed &&
                  state.locationError == null &&
                  !preciseLocationRefused
          ) {
            CompletionMessage(stringResource(R.string.completion_save_failed))
          }
        }
      }
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

@Preview(name = "Text - Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Text - Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CompletionTextPreview() {
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
private fun CompletionPhotoPreview() {
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
