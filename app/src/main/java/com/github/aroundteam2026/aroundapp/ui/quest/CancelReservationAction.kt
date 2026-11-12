// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** How cancelling a reservation stands. */
enum class CancelStatus {
  /** Not cancelled, and not being cancelled. */
  NONE,
  /** The cancel is being written. */
  CANCELLING,
  /** The last try failed; cancelling again retries. */
  FAILED,
}

/**
 * The button that cancels a reservation at [venueName]: "Cancel reservation", which asks first
 * since a cancelled reservation can't be undone; "Cancelling…", disabled, while the cancel is
 * written; or after a failure an error and "Try again", which retries at once. With [leave], it
 * says the same of leaving the party of a reservation someone else made.
 *
 * @param status How cancelling stands.
 * @param venueName The venue the dialog names.
 * @param onCancel Cancels the reservation, once the explorer confirmed.
 * @param horizontalAlignment Where the button and the error sit across the width.
 * @param leave Whether [onCancel] leaves the party rather than cancelling the reservation.
 */
@Composable
internal fun CancelReservationAction(
    status: CancelStatus,
    venueName: String,
    onCancel: () -> Unit,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    leave: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val texts = if (leave) LEAVE_TEXTS else CANCEL_TEXTS
  var asking by rememberSaveable { mutableStateOf(false) }
  Column(
      Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
      horizontalAlignment = horizontalAlignment,
  ) {
    if (status == CancelStatus.FAILED) {
      Text(
          stringResource(texts.failed),
          Modifier.testTag(C.Tag.CANCEL_RESERVATION_FAILED),
          color = colors.error,
          style = AroundTextStyles.Caption,
      )
    }
    TextButton(
        // The explorer already confirmed the cancel that failed
        onClick = { if (status == CancelStatus.FAILED) onCancel() else asking = true },
        enabled = status != CancelStatus.CANCELLING,
        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
        modifier = Modifier.testTag(C.Tag.CANCEL_RESERVATION),
    ) {
      Text(
          stringResource(
              when (status) {
                CancelStatus.NONE -> texts.label
                CancelStatus.CANCELLING -> texts.running
                CancelStatus.FAILED -> R.string.cancel_reservation_retry
              }
          ),
          style = AroundTextStyles.Button,
      )
    }
  }
  if (asking) {
    AlertDialog(
        onDismissRequest = { asking = false },
        confirmButton = {
          TextButton(
              onClick = {
                asking = false
                onCancel()
              },
              colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
              modifier = Modifier.testTag(C.Tag.CANCEL_RESERVATION_CONFIRM),
          ) {
            Text(stringResource(texts.confirm))
          }
        },
        dismissButton = {
          TextButton(
              onClick = { asking = false },
              // The default primary colour is unreadable on the dark background
              colors =
                  ButtonDefaults.textButtonColors(contentColor = AroundTheme.colors.accentText),
              modifier = Modifier.testTag(C.Tag.CANCEL_RESERVATION_KEEP),
          ) {
            Text(stringResource(texts.keep))
          }
        },
        title = { Text(stringResource(texts.title)) },
        text = { Text(stringResource(texts.text, venueName)) },
        modifier = Modifier.testTag(C.Tag.CANCEL_RESERVATION_DIALOG),
    )
  }
}

/** The strings of a [CancelReservationAction], but its "Try again": its button and its dialog. */
private class ActionTexts(
    @StringRes val label: Int,
    @StringRes val running: Int,
    @StringRes val failed: Int,
    @StringRes val title: Int,
    @StringRes val text: Int,
    @StringRes val confirm: Int,
    @StringRes val keep: Int,
)

private val CANCEL_TEXTS =
    ActionTexts(
        R.string.cancel_reservation,
        R.string.cancel_reservation_cancelling,
        R.string.cancel_reservation_failed,
        R.string.cancel_reservation_title,
        R.string.cancel_reservation_text,
        R.string.cancel_reservation_confirm,
        R.string.cancel_reservation_keep,
    )

private val LEAVE_TEXTS =
    ActionTexts(
        R.string.leave_party,
        R.string.leave_party_leaving,
        R.string.leave_party_failed,
        R.string.leave_party_title,
        R.string.leave_party_text,
        R.string.leave_party_confirm,
        R.string.leave_party_stay,
    )
