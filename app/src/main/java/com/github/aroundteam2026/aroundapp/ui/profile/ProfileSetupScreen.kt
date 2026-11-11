// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.completion.ProofPhotos
import com.github.aroundteam2026.aroundapp.ui.navigation.ProfileError
import com.github.aroundteam2026.aroundapp.ui.theme.AroundShapes
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import com.github.aroundteam2026.aroundapp.ui.theme.aroundFieldColors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds Explorer home until the signed-in account has a saved display name.
 *
 * @param onSignOut Called from the load-error state, the way out when the profile can't be read.
 * @param home Explorer home, given the profile's ViewModel so the Profile tab can edit it.
 */
@Composable
fun ExplorerProfileSetupGate(
    uid: String,
    users: UserRepository,
    pictures: UserPictureRepository,
    onSignOut: () -> Unit,
    home: @Composable (ProfileSetupViewModel) -> Unit,
) {
  val model: ProfileSetupViewModel =
      viewModel(
          key = "profile-setup-$uid",
          factory = ProfileSetupViewModel.factory(uid, users, pictures),
      )
  val destination by model.destination.collectAsStateWithLifecycle()
  when (destination) {
    ProfileSetupDestination.LOADING ->
        Box(
            Modifier.fillMaxSize().testTag(C.Tag.PROFILE_SETUP_LOADING),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
    ProfileSetupDestination.SETUP -> ProfileForm(model, pictures)
    ProfileSetupDestination.HOME -> home(model)
    // The same message, retry and sign-out as a failed role read: a missing profile can't be fixed
    // by retrying, so signing out must stay possible
    ProfileSetupDestination.ERROR -> ProfileError(model::retry, onSignOut)
  }
}

/** The shared setup and edit form from the light and dark Sprint 5 frames. */
@Composable
internal fun ProfileForm(
    model: ProfileSetupViewModel,
    pictures: UserPictureRepository,
    editing: Boolean = false,
    onBack: () -> Unit = {},
) {
  val form by model.form.collectAsStateWithLifecycle()
  val profile by model.profile.collectAsStateWithLifecycle()
  val context = LocalContext.current.applicationContext
  val photos = remember(context) { ProofPhotos(context) }
  val scope = rememberCoroutineScope()
  var preparingPhoto by remember { mutableStateOf(false) }
  var photoError by remember { mutableStateOf(false) }
  val picker =
      rememberLauncherForActivityResult(PickVisualMedia()) { source: Uri? ->
        if (source != null) {
          preparingPhoto = true
          photoError = false
          scope.launch {
            try {
              val prepared = photos.shrink(source)
              form.photo?.let { photos.discard(it) }
              model.setPhoto(prepared)
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              photoError = true
            } finally {
              preparingPhoto = false
            }
          }
        }
      }

  Column(
      Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(
              start = AroundSpacing.ScreenHorizontal,
              end = AroundSpacing.ScreenHorizontal,
              top = AroundSpacing.ScreenTop,
              bottom = AroundSpacing.ScreenBottom,
          )
          .testTag(C.Tag.PROFILE_SETUP_SCREEN),
  ) {
    FormHeader(editing, backEnabled = !form.saving, onBack)
    Spacer(Modifier.height(AroundSpacing.SetupGap))
    PhotoRow(
        form,
        savedPicture = profile?.pictureUrl?.takeIf { editing && !form.removePhoto },
        editing,
        pickEnabled = !form.saving && !preparingPhoto,
        pictures,
        onPick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        onRemove = model::removePhoto,
    )
    Spacer(Modifier.height(AroundSpacing.ExtraLarge))
    Text(
        stringResource(R.string.profile_setup_name),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(AroundSpacing.Small))
    OutlinedTextField(
        value = form.name,
        onValueChange = model::setName,
        placeholder = { Text(stringResource(R.string.profile_setup_name_hint)) },
        singleLine = true,
        enabled = !form.saving,
        isError =
            form.error == ProfileSetupError.NAME_REQUIRED ||
                form.error == ProfileSetupError.NAME_TOO_LONG,
        colors = aroundFieldColors(),
        shape = AroundShapes.extraSmall,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.PROFILE_SETUP_NAME),
    )
    errorTextOf(form.error, editing, photoError)?.let { errorText ->
      Spacer(Modifier.height(AroundSpacing.Small))
      Text(
          stringResource(errorText),
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(C.Tag.PROFILE_SETUP_ERROR),
      )
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = model::save,
        enabled = !form.saving && !preparingPhoto,
        shape = AroundShapes.medium,
        modifier =
            Modifier.fillMaxWidth().height(CONTINUE_HEIGHT).testTag(C.Tag.PROFILE_SETUP_CONTINUE),
    ) {
      Text(stringResource(continueLabelOf(form.saving, editing)))
    }
  }
}

/** The title and copy of the form; in edit mode, with a back arrow above them. */
@Composable
private fun FormHeader(editing: Boolean, backEnabled: Boolean, onBack: () -> Unit) {
  if (editing) {
    IconButton(
        onClick = onBack,
        enabled = backEnabled,
        modifier = Modifier.testTag(C.Tag.PROFILE_EDIT_BACK),
    ) {
      Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.profile_edit_back))
    }
    Spacer(Modifier.height(EDIT_TITLE_GAP))
  } else {
    Spacer(Modifier.height(SETUP_TITLE_GAP))
  }
  Text(
      stringResource(if (editing) R.string.profile_edit_title else R.string.profile_setup_title),
      style = AroundTextStyles.SetupTitle,
      color = MaterialTheme.colorScheme.onBackground,
  )
  Spacer(Modifier.height(AroundSpacing.Small))
  Text(
      stringResource(
          if (editing) R.string.profile_edit_description else R.string.profile_setup_description
      ),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The avatar beside the photo actions. It shows the picked photo, else [savedPicture]; "Remove
 * photo" appears in edit mode while there is either to remove, and the setup form says the photo is
 * optional instead.
 */
@Composable
private fun PhotoRow(
    form: ProfileSetupFormState,
    savedPicture: String?,
    editing: Boolean,
    pickEnabled: Boolean,
    pictures: UserPictureRepository,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ProfileAvatar(
        savedPicture,
        form.photo,
        pictures,
        Modifier.testTag(C.Tag.PROFILE_SETUP_AVATAR),
    )
    Column(verticalArrangement = Arrangement.spacedBy(PHOTO_ACTIONS_GAP)) {
      OutlinedButton(
          onClick = onPick,
          enabled = pickEnabled,
          shape = AroundShapes.medium,
          border = BorderStroke(PHOTO_BUTTON_BORDER, MaterialTheme.colorScheme.primary),
          colors =
              ButtonDefaults.outlinedButtonColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = AroundTheme.colors.accentText,
              ),
          modifier = Modifier.height(PHOTO_BUTTON_HEIGHT).testTag(C.Tag.PROFILE_SETUP_ADD_PHOTO),
      ) {
        Text(
            stringResource(
                if (editing) R.string.profile_edit_change_photo
                else R.string.profile_setup_add_photo
            )
        )
      }
      when {
        editing && (form.photo != null || savedPicture != null) ->
            TextButton(
                onClick = onRemove,
                enabled = !form.saving,
                modifier = Modifier.testTag(C.Tag.PROFILE_EDIT_REMOVE_PHOTO),
            ) {
              Text(
                  stringResource(R.string.profile_edit_remove_photo),
                  color = MaterialTheme.colorScheme.error,
              )
            }
        !editing ->
            Text(
                stringResource(R.string.profile_setup_photo_optional),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
      }
    }
  }
}

/** The message under the name field for [error], or for a photo that couldn't be prepared. */
@StringRes
internal fun errorTextOf(error: ProfileSetupError?, editing: Boolean, photoError: Boolean): Int? =
    when (error) {
      ProfileSetupError.NAME_REQUIRED -> R.string.profile_setup_name_required
      ProfileSetupError.NAME_TOO_LONG -> R.string.profile_setup_name_too_long
      ProfileSetupError.SAVE_FAILED ->
          if (editing) R.string.profile_edit_save_failed else R.string.profile_setup_save_failed
      null -> if (photoError) R.string.profile_setup_photo_failed else null
    }

/** The label of the form's main button. */
@StringRes
internal fun continueLabelOf(saving: Boolean, editing: Boolean): Int =
    when {
      saving -> R.string.profile_setup_saving
      editing -> R.string.profile_edit_save
      else -> R.string.profile_setup_continue
    }

/**
 * Shows a selected local picture or the saved Storage picture, with the same fallback in both
 * forms.
 *
 * @param io Where the saved picture is downloaded and decoded, off the main thread.
 */
@Composable
internal fun ProfileAvatar(
    pictureUrl: String?,
    photo: Uri?,
    pictures: UserPictureRepository,
    modifier: Modifier = Modifier,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
  val context = LocalContext.current.applicationContext
  val photos = remember(context) { ProofPhotos(context) }
  val preview by
      produceState<ImageBitmap?>(null, pictureUrl, photo) {
        value =
            try {
              when {
                photo != null -> photos.preview(photo)
                pictureUrl != null ->
                    withContext(io) {
                      val bytes = pictures.download(pictureUrl).getOrThrow()
                      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                else -> null
              }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              null
            }
      }
  Box(
      modifier
          .size(AVATAR_SIZE)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
  ) {
    if (preview != null) {
      Image(preview!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Image(
          painterResource(R.drawable.ic_profile),
          null,
          Modifier.size(AVATAR_ICON_SIZE),
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
      )
    }
  }
}

// The form's own geometry, from the Figma setup and edit frames
private val SETUP_TITLE_GAP = 80.dp
private val EDIT_TITLE_GAP = 36.dp
private val PHOTO_ACTIONS_GAP = 6.dp
private val PHOTO_BUTTON_HEIGHT = 44.dp
private val PHOTO_BUTTON_BORDER = 1.5.dp
private val CONTINUE_HEIGHT = 54.dp
private val AVATAR_SIZE = 64.dp
private val AVATAR_ICON_SIZE = 28.dp
