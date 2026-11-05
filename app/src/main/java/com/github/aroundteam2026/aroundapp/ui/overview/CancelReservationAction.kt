// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

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

/**
 * The button that cancels a reservation at [venueName]: "Cancel reservation", which asks first
 * since a cancelled reservation can't be undone; "Cancelling…", disabled, while the cancel is
 * written; or after a failure an error and "Try again", which retries at once.
 *
 * @param status How cancelling stands.
 * @param venueName The venue the dialog names.
 * @param onCancel Cancels the reservation, once the explorer confirmed.
 * @param horizontalAlignment Where the button and the error sit across the width.
 */
@Composable
internal fun CancelReservationAction(
    status: CancelStatus,
    venueName: String,
    onCancel: () -> Unit,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
  val colors = MaterialTheme.colorScheme
  var asking by rememberSaveable { mutableStateOf(false) }
  Column(
      Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
      horizontalAlignment = horizontalAlignment,
  ) {
    if (status == CancelStatus.FAILED) {
      Text(
          stringResource(R.string.overview_cancel_failed),
          Modifier.testTag(C.Tag.OVERVIEW_ROW_CANCEL_FAILED),
          color = colors.error,
          style = AroundTextStyles.Caption,
      )
    }
    TextButton(
        // The explorer already confirmed the cancel that failed
        onClick = { if (status == CancelStatus.FAILED) onCancel() else asking = true },
        enabled = status != CancelStatus.CANCELLING,
        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
        modifier = Modifier.testTag(C.Tag.OVERVIEW_ROW_CANCEL),
    ) {
      Text(
          stringResource(
              when (status) {
                CancelStatus.NONE -> R.string.overview_cancel
                CancelStatus.CANCELLING -> R.string.overview_cancelling
                CancelStatus.FAILED -> R.string.overview_cancel_retry
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
              modifier = Modifier.testTag(C.Tag.OVERVIEW_CANCEL_CONFIRM),
          ) {
            Text(stringResource(R.string.overview_cancel_confirm))
          }
        },
        dismissButton = {
          TextButton(
              onClick = { asking = false },
              modifier = Modifier.testTag(C.Tag.OVERVIEW_CANCEL_KEEP),
          ) {
            Text(stringResource(R.string.overview_cancel_keep))
          }
        },
        title = { Text(stringResource(R.string.overview_cancel_title)) },
        text = { Text(stringResource(R.string.overview_cancel_text, venueName)) },
        modifier = Modifier.testTag(C.Tag.OVERVIEW_CANCEL_DIALOG),
    )
  }
}
