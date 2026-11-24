// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.OfflineHint
import com.github.aroundteam2026.aroundapp.ui.ProfilePhoto
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
    connectivity: ConnectivityRepository,
    onSignOut: () -> Unit,
    home: @Composable (ProfileSetupViewModel) -> Unit,
) {
  val model: ProfileSetupViewModel =
      viewModel(
          key = "profile-setup-$uid",
          factory = ProfileSetupViewModel.factory(uid, users, pictures, connectivity),
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
  val offline by model.offline.collectAsStateWithLifecycle()
  val picker = rememberPicturePicker(form.photo, model::setPhoto)

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
    val savedPicture = profile?.pictureUrl?.takeIf { editing && !form.removePhoto }
    PictureRow(
        avatar = {
          ProfileAvatar(
              savedPicture,
              form.photo,
              pictures,
              Modifier.testTag(C.Tag.PICTURE_AVATAR),
          )
        },
        labels = PHOTO_LABELS,
        editing = editing,
        hasPicture = form.photo != null || savedPicture != null,
        saving = form.saving,
        picker = picker,
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
    errorTextOf(form.error, editing, picker.failed)?.let { errorText ->
      Spacer(Modifier.height(AroundSpacing.Small))
      Text(
          stringResource(errorText),
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(C.Tag.PROFILE_SETUP_ERROR),
      )
    }
    Spacer(Modifier.weight(1f))
    // Uploading the picture and saving the profile both need the server
    if (offline) {
      OfflineHint(R.string.offline_profile_needs_connection)
      Spacer(Modifier.height(AroundSpacing.Medium))
    }
    Button(
        onClick = model::save,
        enabled = !form.saving && !picker.preparing && !offline,
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
 * The words of a [PictureRow]: an explorer adds a "photo", a venue a "picture".
 *
 * @property add The pick button's label while there is nothing to replace, at setup.
 * @property change The pick button's label when editing.
 * @property remove The label of the button that removes the picture, when editing.
 * @property optional The hint under the pick button at setup.
 */
internal data class PictureLabels(
    @StringRes val add: Int,
    @StringRes val change: Int,
    @StringRes val remove: Int,
    @StringRes val optional: Int,
)

/**
 * Picks a picture from the gallery and prepares a small copy of it to upload.
 *
 * @property preparing Whether a picked picture is being shrunk, so picking or saving must wait.
 * @property failed Whether the last picked picture couldn't be read, to say so under the form.
 * @property launch Opens the gallery.
 */
internal class PicturePicker(val preparing: Boolean, val failed: Boolean, val launch: () -> Unit)

/**
 * A [PicturePicker] that passes each prepared copy to [onPicked], then discards [current], the copy
 * it replaces. The copies stay in the cache, which the system clears, since they're uploaded at
 * once.
 */
@Composable
internal fun rememberPicturePicker(current: Uri?, onPicked: (Uri) -> Unit): PicturePicker {
  val context = LocalContext.current.applicationContext
  val photos = remember(context) { ProofPhotos(context, root = context.cacheDir) }
  val scope = rememberCoroutineScope()
  var preparing by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val launcher =
      rememberLauncherForActivityResult(PickVisualMedia()) { source: Uri? ->
        if (source != null) {
          preparing = true
          failed = false
          scope.launch {
            try {
              val prepared = photos.shrink(source)
              current?.let { photos.discard(it) }
              onPicked(prepared)
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              failed = true
            } finally {
              preparing = false
            }
          }
        }
      }
  return PicturePicker(preparing, failed) {
    launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
  }
}

/**
 * The [avatar] beside the picture's actions, shared by the explorer's and the venue's forms: at
 * setup, a button to add one and a hint that it is optional; when [editing], a button to change it
 * and, while [hasPicture], one to remove it. [saving] locks both buttons.
 */
@Composable
internal fun PictureRow(
    avatar: @Composable () -> Unit,
    labels: PictureLabels,
    editing: Boolean,
    hasPicture: Boolean,
    saving: Boolean,
    picker: PicturePicker,
    onRemove: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    avatar()
    Column(verticalArrangement = Arrangement.spacedBy(PHOTO_ACTIONS_GAP)) {
      OutlinedButton(
          onClick = picker.launch,
          enabled = !saving && !picker.preparing,
          shape = AroundShapes.medium,
          border = BorderStroke(PHOTO_BUTTON_BORDER, MaterialTheme.colorScheme.primary),
          colors =
              ButtonDefaults.outlinedButtonColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = AroundTheme.colors.accentText,
              ),
          modifier = Modifier.height(PHOTO_BUTTON_HEIGHT).testTag(C.Tag.PICTURE_PICK),
      ) {
        Text(stringResource(if (editing) labels.change else labels.add))
      }
      when {
        editing && hasPicture ->
            TextButton(
                onClick = onRemove,
                enabled = !saving,
                modifier = Modifier.testTag(C.Tag.PICTURE_REMOVE),
            ) {
              Text(stringResource(labels.remove), color = MaterialTheme.colorScheme.error)
            }
        !editing ->
            Text(
                stringResource(labels.optional),
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
 * forms: [fallbackIcon] on the primary container.
 *
 * @param io Where the saved picture is downloaded and decoded, off the main thread.
 */
@Composable
internal fun ProfileAvatar(
    pictureUrl: String?,
    photo: Uri?,
    pictures: UserPictureRepository,
    modifier: Modifier = Modifier,
    @DrawableRes fallbackIcon: Int = R.drawable.ic_profile,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
  val context = LocalContext.current.applicationContext
  // Uploaded at once, so its copies stay in the cache, which the system clears
  val photos = remember(context) { ProofPhotos(context, root = context.cacheDir) }
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
    val picture = preview
    if (picture != null) {
      ProfilePhoto(picture, Modifier.fillMaxSize())
    } else {
      Image(
          painterResource(fallbackIcon),
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

/** The explorer's words for their picture, a "photo". */
private val PHOTO_LABELS =
    PictureLabels(
        add = R.string.profile_setup_add_photo,
        change = R.string.profile_edit_change_photo,
        remove = R.string.profile_edit_remove_photo,
        optional = R.string.profile_setup_photo_optional,
    )
