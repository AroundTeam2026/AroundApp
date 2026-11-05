// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import kotlinx.coroutines.launch

/** A preset reason to reject a proof, from Figma. [photoOnly] ones make no sense for an answer. */
enum class RejectReason(@param:StringRes val label: Int, val photoOnly: Boolean = false) {
  BLURRY(R.string.reject_proof_reason_blurry, photoOnly = true),
  NOT_AT_VENUE(R.string.reject_proof_reason_not_at_venue, photoOnly = true),
  NO_MATCH(R.string.reject_proof_reason_no_match),
  OTHER(R.string.reject_proof_reason_other),
}

/**
 * The sheet a venue rejects a proof from, as the Figma frame `S4 · Reject proof · reason`: preset
 * reasons to pick one of, and an optional note. "Reject proof" stays disabled until a reason is
 * picked or a note written, then sends both as one reason through [onReject]. The sheet stays open
 * afterwards, so a failure can be retried; the screen closes it once the proof is reviewed.
 *
 * While [saving], its buttons are disabled and it can't be dismissed, since the rejection would
 * still go through. Otherwise Back, a drag down or system back close it without rejecting anything,
 * then call [onDismiss].
 *
 * @param explorerName Name of the explorer who sent the proof, or null if unknown.
 * @param proofType The kind of proof: a written answer leaves out the reasons about photos.
 * @param saving Whether the rejection is being saved.
 * @param onReject Called with the reason the explorer will see.
 * @param onDismiss Called once the sheet has closed without rejecting.
 * @param snackbarHostState Shows a failed rejection on the sheet, which covers the screen's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RejectProofSheet(
    explorerName: String?,
    proofType: ProofType,
    saving: Boolean,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val currentSaving by rememberUpdatedState(saving)
  val sheetState =
      rememberModalBottomSheetState(
          skipPartiallyExpanded = true,
          confirmValueChange = { it != SheetValue.Hidden || !currentSaving },
      )
  val scope = rememberCoroutineScope()
  var picked by rememberSaveable { mutableStateOf<RejectReason?>(null) }
  var note by rememberSaveable { mutableStateOf("") }
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(C.Tag.REJECT_PROOF_SHEET),
      sheetState = sheetState,
      shape = AroundCorners.BottomSheet,
      containerColor = MaterialTheme.colorScheme.surface,
      dragHandle = { Handle() },
      properties = ModalBottomSheetProperties(shouldDismissOnClickOutside = !saving),
  ) {
    // Taken before the sheet's own Back, which would close it
    BackHandler(enabled = saving) {}
    RejectProofForm(
        explorerName = explorerName,
        reasons = RejectReason.entries.filter { proofType == ProofType.PHOTO || !it.photoOnly },
        picked = picked,
        onPick = { reason -> picked = reason.takeUnless { it == picked } },
        note = note,
        onNoteChange = { note = it.take(NOTE_MAX_LENGTH) },
        saving = saving,
        onReject = onReject,
        onBack = {
          scope
              .launch { sheetState.hide() }
              .invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
        },
    )
    SnackbarHost(snackbarHostState)
  }
}

/**
 * What the sheet shows: [reasons] with [picked] selected, which [onPick] picks or unpicks, the
 * [note], and the buttons. [onReject] gets the reason the explorer will see.
 */
@Composable
private fun RejectProofForm(
    explorerName: String?,
    reasons: List<RejectReason>,
    picked: RejectReason?,
    onPick: (RejectReason) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    saving: Boolean,
    onReject: (String) -> Unit,
    onBack: () -> Unit,
) {
  val reason = rejectReason(picked, note)
  Column(
      Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(
              start = AroundSpacing.ScreenHorizontal,
              end = AroundSpacing.ScreenHorizontal,
              bottom = AroundSpacing.ScreenBottom,
          ),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap)) {
      Text(
          stringResource(R.string.reject_proof_title),
          Modifier.semantics { heading() },
          color = MaterialTheme.colorScheme.onSurface,
          style = AroundTextStyles.SheetTitle,
      )
      Text(
          explorerName?.let { stringResource(R.string.reject_proof_body, it) }
              ?: stringResource(R.string.reject_proof_body_unknown),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.BodyMedium,
      )
    }
    // No gap between rows: each chip's 48 dp touch target already spaces them about as in Figma
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
      reasons.forEach {
        ReasonChip(
            stringResource(it.label),
            selected = it == picked,
            enabled = !saving,
            onClick = { onPick(it) },
            modifier = Modifier.testTag("${C.Tag.REJECT_PROOF_REASON}_${it.name}"),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
      Text(
          stringResource(R.string.reject_proof_note_label),
          color = MaterialTheme.colorScheme.onSurface,
          style = AroundTextStyles.FieldLabel,
      )
      val background = MaterialTheme.colorScheme.background
      OutlinedTextField(
          value = note,
          onValueChange = onNoteChange,
          enabled = !saving,
          placeholder = {
            Text(stringResource(R.string.reject_proof_note_hint), style = AroundTextStyles.Body)
          },
          modifier =
              Modifier.fillMaxWidth()
                  .heightIn(min = NOTE_MIN_HEIGHT)
                  .testTag(C.Tag.REJECT_PROOF_NOTE),
          shape = MaterialTheme.shapes.small,
          textStyle = AroundTextStyles.Body,
          colors =
              OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = background,
                  unfocusedContainerColor = background,
                  disabledContainerColor = background,
              ),
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap)) {
      SecondaryButton(
          stringResource(R.string.reject_proof_back),
          onBack,
          enabled = !saving,
          tag = C.Tag.REJECT_PROOF_BACK,
      )
      PrimaryButton(
          stringResource(
              if (saving) R.string.reject_proof_rejecting else R.string.reject_proof_confirm
          ),
          onClick = { onReject(reason) },
          enabled = reason.isNotEmpty() && !saving,
          tag = C.Tag.REJECT_PROOF_CONFIRM,
          busy = saving,
      )
    }
    if (reason.isEmpty()) {
      Text(
          stringResource(R.string.reject_proof_needs_reason),
          Modifier.testTag(C.Tag.REJECT_PROOF_NEEDS_REASON),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.Small,
      )
    }
  }
}

/**
 * The reason the explorer sees for [picked] and [note]: both, either one, or empty if neither.
 * [RejectReason.OTHER] says nothing a note doesn't, so a note replaces it.
 */
@Composable
private fun rejectReason(picked: RejectReason?, note: String): String {
  val written = note.trim()
  val label =
      picked?.takeUnless { it == RejectReason.OTHER && written.isNotEmpty() }?.label
          ?: return written
  return if (written.isEmpty()) stringResource(label)
  else stringResource(R.string.reject_proof_reason_with_note, stringResource(label), written)
}

/** A pill to pick [label] with, ringed and ticked in purple while [selected]. */
@Composable
private fun ReasonChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val accent = AroundTheme.colors.accentText
  Surface(
      selected = selected,
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      shape = AroundPillShape,
      color = if (selected) AroundTheme.colors.lavender else MaterialTheme.colorScheme.surface,
      contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
  ) {
    Row(
        Modifier.padding(horizontal = AroundSpacing.InsetPadding, vertical = CHIP_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selected) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null)
      }
      Text(label, style = AroundTextStyles.Chip)
    }
  }
}

/** The bar at the top of the sheet, as in Figma. */
@Composable
private fun Handle() {
  Box(
      Modifier.padding(top = AroundSpacing.Medium, bottom = AroundSpacing.Large)
          .size(HANDLE_WIDTH, HANDLE_HEIGHT)
          .background(MaterialTheme.colorScheme.outline, AroundCorners.ProgressSegment)
  )
}

/**
 * The longest note. The rules refuse a reason over 300 characters, and the longest preset with its
 * separator takes about 25 of them; if a preset gets much longer, compute this from the labels.
 */
internal const val NOTE_MAX_LENGTH = 250

private val NOTE_MIN_HEIGHT = 76.dp
private val CHIP_VERTICAL_PADDING = 9.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp

// Internal, not private, so a test can draw them: the previews are code too
@Preview(name = "Picked - Light", widthDp = 413)
@Preview(name = "Picked - Dark", widthDp = 413, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun RejectProofPickedPreview() {
  RejectProofPreview(RejectReason.NO_MATCH, "The photo shows a tea; the task asks for any drink.")
}

@Preview(name = "Empty - Light", widthDp = 413)
@Preview(name = "Empty - Dark", widthDp = 413, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun RejectProofEmptyPreview() {
  RejectProofPreview(picked = null, note = "")
}

/** The form as the sheet draws it: the sheet itself is a window, which previews don't show. */
@Composable
private fun RejectProofPreview(picked: RejectReason?, note: String) {
  AroundAppTheme {
    Surface(color = MaterialTheme.colorScheme.surface, shape = AroundCorners.BottomSheet) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Handle()
        RejectProofForm(
            explorerName = "yusuf.s",
            reasons = RejectReason.entries,
            picked = picked,
            onPick = {},
            note = note,
            onNoteChange = {},
            saving = false,
            onReject = {},
            onBack = {},
        )
      }
    }
  }
}
