// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.profile.PictureLabels
import com.github.aroundteam2026.aroundapp.ui.profile.PicturePicker
import com.github.aroundteam2026.aroundapp.ui.profile.PictureRow
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileAvatar
import com.github.aroundteam2026.aroundapp.ui.profile.rememberPicturePicker
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundShapes
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import com.github.aroundteam2026.aroundapp.ui.theme.aroundFieldColors

/**
 * The form's picture row and the [picker] behind it, whose state also locks saving and explains a
 * picture that couldn't be read.
 */
class VenueFormPicture
internal constructor(
    internal val picker: PicturePicker,
    internal val row: @Composable () -> Unit,
)

/**
 * Entry point for the venue sign-up flow. The navigation owner supplies the existing ViewModel and
 * moves to location setup when [onContinueToLocation] receives the saved venue id. With no
 * [onBack], this is a root screen: hide the arrow and leave system Back to the activity.
 *
 * @param pictures shows the venue's saved picture.
 */
@Composable
fun VenueInitializationScreen(
    viewModel: VenueInitializationViewModel,
    pictures: UserPictureRepository,
    onBack: (() -> Unit)?,
    onContinueToLocation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsState()
  val currentOnContinue by rememberUpdatedState(onContinueToLocation)
  LaunchedEffect(state.venueId) {
    state.venueId?.let {
      currentOnContinue(it)
      viewModel.onLocationNavigationHandled()
    }
  }
  if (onBack != null) {
    BackHandler { if (!state.isSaving) onBack() }
  }
  val picker = rememberPicturePicker(state.photo, viewModel::setPhoto)
  val savedPicture = state.savedPicture.takeIf { !state.removePhoto }
  val picture =
      VenueFormPicture(picker) {
        PictureRow(
            avatar = {
              ProfileAvatar(
                  savedPicture,
                  state.photo,
                  pictures,
                  Modifier.testTag(C.Tag.PICTURE_AVATAR),
                  fallbackIcon = R.drawable.ic_storefront,
              )
            },
            labels = VENUE_PICTURE_LABELS,
            editing = state.editing,
            hasPicture = state.photo != null || savedPicture != null,
            saving = !state.canContinue,
            picker = picker,
            onRemove = viewModel::removePhoto,
        )
      }
  VenueInitializationContent(
      state = state,
      onNameChange = viewModel::updateBusinessName,
      onSubmit = viewModel::submit,
      onBack = onBack,
      modifier = modifier,
      picture = picture,
  )
}

/**
 * Stateless form, also used by previews without authentication or repository dependencies. Without
 * a [picture], the form has no picture row.
 */
@Composable
fun VenueInitializationContent(
    state: VenueInitializationUiState,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    picture: VenueFormPicture? = null,
) {
  val preparingPicture = picture?.picker?.preparing == true
  val keyboard = LocalSoftwareKeyboardController.current
  val nameLabel = stringResource(R.string.venue_initialization_name)
  BoxWithConstraints(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .safeDrawingPadding()
          .imePadding()
          .testTag(C.Tag.VENUE_INITIALIZATION_SCREEN)
  ) {
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp)
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (onBack != null) {
          Surface(
              onClick = onBack,
              enabled = !state.isSaving,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.size(48.dp).testTag(C.Tag.VENUE_INITIALIZATION_BACK),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Image(
                  painterResource(R.drawable.venue_setup_back),
                  stringResource(R.string.back),
                  Modifier.size(22.dp),
                  colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
              )
            }
          }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              stringResource(R.string.venue_initialization_step),
              style = AroundTextStyles.Helper,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.weight(1f)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, AroundCorners.ProgressSegment)
            )
            Box(
                Modifier.weight(1f)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outline, AroundCorners.ProgressSegment)
            )
          }
        }
      }
      Spacer(Modifier.height(36.dp))
      Text(
          stringResource(
              if (state.editing) R.string.venue_initialization_edit_title
              else R.string.venue_initialization_title
          ),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.semantics { heading() },
      )
      Spacer(Modifier.height(10.dp))
      Text(
          stringResource(R.string.venue_initialization_description),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(PICTURE_GAP))
      picture?.let {
        it.row()
        Spacer(Modifier.height(PICTURE_GAP))
      }
      Text(
          stringResource(R.string.venue_initialization_name),
          style = AroundTextStyles.FieldLabel,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
          value = state.businessName,
          onValueChange = onNameChange,
          enabled = state.canEditName,
          singleLine = true,
          isError =
              state.error == VenueInitializationError.EMPTY_NAME ||
                  state.error == VenueInitializationError.NAME_TOO_LONG,
          placeholder = { Text(stringResource(R.string.venue_initialization_name_hint)) },
          shape = AroundShapes.small,
          textStyle = MaterialTheme.typography.bodyLarge,
          colors =
              aroundFieldColors(
                  disabledContainerColor = MaterialTheme.colorScheme.surface,
                  // Purple on cream, cream in dark mode, where purple barely shows
                  focusedBorderColor = AroundTheme.colors.accentText,
                  cursorColor = AroundTheme.colors.accentText,
              ),
          keyboardOptions =
              KeyboardOptions(
                  capitalization = KeyboardCapitalization.Words,
                  imeAction = ImeAction.Done,
              ),
          keyboardActions =
              KeyboardActions(
                  onDone = {
                    keyboard?.hide()
                    onSubmit()
                  }
              ),
          modifier =
              Modifier.fillMaxWidth().testTag(C.Tag.VENUE_INITIALIZATION_NAME).semantics {
                contentDescription = nameLabel
              },
      )
      val message =
          state.error?.let { error ->
            when (error) {
              VenueInitializationError.EMPTY_NAME ->
                  stringResource(R.string.venue_initialization_empty_name)
              VenueInitializationError.NAME_TOO_LONG ->
                  pluralStringResource(
                      R.plurals.venue_initialization_name_too_long,
                      VenueLimits.MAX_NAME_LENGTH,
                      VenueLimits.MAX_NAME_LENGTH,
                  )
              VenueInitializationError.SIGN_IN_REQUIRED ->
                  stringResource(R.string.venue_initialization_sign_in_required)
              VenueInitializationError.SAVE_FAILED ->
                  stringResource(R.string.venue_initialization_save_failed)
            }
          }
              ?: stringResource(R.string.venue_picture_failed).takeIf {
                picture?.picker?.failed == true
              }
      message?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.padding(top = 8.dp).testTag(C.Tag.VENUE_INITIALIZATION_ERROR).semantics {
                  liveRegion = LiveRegionMode.Polite
                },
        )
      }
      Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
      Button(
          onClick = {
            keyboard?.hide()
            onSubmit()
          },
          enabled = state.canContinue && !preparingPicture,
          shape = AroundShapes.medium,
          contentPadding =
              androidx.compose.foundation.layout.PaddingValues(
                  horizontal = 16.dp,
                  vertical = 12.dp,
              ),
          colors = ButtonDefaults.buttonColors(),
          modifier =
              Modifier.fillMaxWidth()
                  .heightIn(min = 54.dp)
                  .testTag(C.Tag.VENUE_INITIALIZATION_CONTINUE),
      ) {
        Text(
            stringResource(
                when {
                  state.isSaving -> R.string.venue_initialization_saving
                  state.editing -> R.string.venue_initialization_edit_continue
                  else -> R.string.venue_initialization_continue
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 413, heightDp = 917)
@Composable
private fun VenueInitializationPreview() {
  VenueInitializationContent(
      VenueInitializationUiState(),
      onNameChange = {},
      onSubmit = {},
      onBack = {},
  )
}

/** The venue's words for its picture, from the Figma setup and details frames. */
private val VENUE_PICTURE_LABELS =
    PictureLabels(
        add = R.string.venue_picture_add,
        change = R.string.venue_picture_change,
        remove = R.string.venue_picture_remove,
        optional = R.string.venue_picture_optional,
    )

/** The gap around the picture row, from the Figma setup frame. */
private val PICTURE_GAP = 28.dp
