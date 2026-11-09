// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** Why the last photo couldn't be used, which the box says under its buttons. */
internal enum class ProofPhotoError {
  /** The phone has no app that takes photos. */
  NO_CAMERA,
  /** The taken or picked photo couldn't be read as an image. */
  UNREADABLE,
  /** Even shrunk, the photo is too big to upload. */
  TOO_BIG,
  /** The phone has no screen for the app's settings, where the camera could be allowed. */
  NO_SETTINGS,
}

/**
 * The proof photo box of a photo quest, as the Figma "Photo" box of `S4 · Submit proof`. Before a
 * photo: the camera icon, what to do at [venueName], and "Take photo" above "Choose from gallery";
 * after a refused camera, why it can't be used and "Open settings" in place of "Take photo". Once
 * [picked], the photo fills the box, with "Retake" in its corner.
 *
 * It only draws: the caller asks for the camera permission and opens the camera, the gallery and
 * the settings.
 *
 * @param preview The picked photo, scaled for the screen, or null while it loads; the box then
 *   shows its background colour.
 * @param error Why the last photo failed, said under the buttons, or null.
 * @param enabled False while a photo is being prepared, which locks the buttons.
 */
@Composable
internal fun ProofPhotoBox(
    venueName: String,
    picked: Boolean,
    preview: ImageBitmap?,
    cameraRefused: Boolean,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
    error: ProofPhotoError? = null,
    enabled: Boolean = true,
) {
  val box = modifier.fillMaxWidth().clip(BOX_SHAPE).testTag(C.Tag.PROOF_PHOTO)
  if (picked) {
    PickedPhoto(preview, enabled, onRetake, box)
  } else {
    Choices(
        venueName,
        cameraRefused,
        error,
        enabled,
        onTakePhoto,
        onChooseFromGallery,
        onOpenSettings,
        box,
    )
  }
}

/** The picked photo filling the box, with "Retake" in its corner. */
@Composable
private fun PickedPhoto(
    preview: ImageBitmap?,
    enabled: Boolean,
    onRetake: () -> Unit,
    modifier: Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Box(modifier.height(BOX_HEIGHT).background(AroundTheme.colors.sage)) {
    if (preview != null) {
      Image(
          preview,
          contentDescription = stringResource(R.string.proof_photo_description),
          modifier = Modifier.matchParentSize().testTag(C.Tag.PROOF_PHOTO_IMAGE),
          contentScale = ContentScale.Crop,
      )
    }
    Text(
        stringResource(R.string.proof_photo_retake),
        Modifier.align(Alignment.BottomEnd)
            .padding(AroundSpacing.InsetPadding)
            .testTag(C.Tag.PROOF_PHOTO_RETAKE)
            .clip(AroundPillShape)
            .background(colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onRetake)
            .padding(horizontal = AroundSpacing.InsetPadding, vertical = AroundSpacing.Small),
        color = colors.onSurface,
        style = AroundTextStyles.Helper,
    )
  }
}

/**
 * Before a photo: the camera icon, what to do, and "Take photo" (or "Open settings" after a refused
 * camera) above "Choose from gallery", then the [error] if the last photo failed.
 */
@Composable
private fun Choices(
    venueName: String,
    cameraRefused: Boolean,
    error: ProofPhotoError?,
    enabled: Boolean,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier
          .defaultMinSize(minHeight = BOX_HEIGHT)
          .background(AroundTheme.colors.panel)
          .padding(AroundSpacing.CardPadding),
      verticalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
        painterResource(R.drawable.ic_proof_photo),
        contentDescription = null,
        Modifier.size(ICON_SIZE),
        tint = colors.onSurfaceVariant,
    )
    Text(
        stringResource(
            if (cameraRefused) R.string.proof_photo_camera_refused_title
            else R.string.proof_photo_title
        ),
        color = colors.onSurface,
        style = AroundTextStyles.RowTitle,
        textAlign = TextAlign.Center,
    )
    Text(
        if (cameraRefused) stringResource(R.string.proof_photo_camera_refused_body)
        else stringResource(R.string.proof_photo_body, venueName),
        color = colors.onSurfaceVariant,
        style = AroundTextStyles.Caption,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(BUTTONS_EXTRA_GAP))
    Button(
        onClick = if (cameraRefused) onOpenSettings else onTakePhoto,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth()
                .height(BUTTON_HEIGHT)
                .testTag(
                    if (cameraRefused) C.Tag.PROOF_PHOTO_OPEN_SETTINGS else C.Tag.PROOF_PHOTO_TAKE
                ),
    ) {
      Text(
          stringResource(
              if (cameraRefused) R.string.proof_photo_open_settings else R.string.proof_photo_take
          ),
          style = AroundTextStyles.Button,
      )
    }
    OutlinedButton(
        onClick = onChooseFromGallery,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(OUTLINE_WIDTH, colors.primary),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = AroundTheme.colors.accentText,
            ),
        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT).testTag(C.Tag.PROOF_PHOTO_GALLERY),
    ) {
      Text(stringResource(R.string.proof_photo_gallery), style = AroundTextStyles.Button)
    }
    if (error != null) {
      Text(
          stringResource(
              when (error) {
                ProofPhotoError.NO_CAMERA -> R.string.proof_photo_no_camera
                ProofPhotoError.UNREADABLE -> R.string.proof_photo_unreadable
                ProofPhotoError.TOO_BIG -> R.string.proof_photo_too_big
                ProofPhotoError.NO_SETTINGS -> R.string.proof_photo_no_settings
              }
          ),
          Modifier.testTag(C.Tag.PROOF_PHOTO_ERROR),
          color = colors.error,
          style = AroundTextStyles.Caption,
          textAlign = TextAlign.Center,
      )
    }
  }
}

private val BOX_SHAPE = RoundedCornerShape(22.dp)
private val BOX_HEIGHT = 290.dp
private val ICON_SIZE = 28.dp
private val CONTENT_GAP = 6.dp
// Figma's "Gap" frame: with the two content gaps around it, 20dp between the text and the buttons
private val BUTTONS_EXTRA_GAP = 8.dp
private val BUTTON_HEIGHT = 54.dp
private val OUTLINE_WIDTH = 1.5.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProofPhotoBoxPreview() {
  AroundAppTheme {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(AroundSpacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
    ) {
      // Before a photo, after a refused camera, and with a photo picked
      listOf(false to false, false to true, true to false).forEach { (picked, refused) ->
        ProofPhotoBox("Café Lumière", picked, null, refused, {}, {}, {}, {})
      }
      // A photo that failed
      ProofPhotoBox(
          "Café Lumière",
          picked = false,
          preview = null,
          cameraRefused = false,
          onTakePhoto = {},
          onChooseFromGallery = {},
          onOpenSettings = {},
          onRetake = {},
          error = ProofPhotoError.TOO_BIG,
      )
    }
  }
}
