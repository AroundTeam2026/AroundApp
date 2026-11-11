// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.launch

/**
 * Holds Explorer home until the signed-in account has a saved display name.
 *
 * @param onSignOut Called from the load-error state, the way out when the profile can't be read.
 */
@Composable
fun ExplorerProfileSetupGate(
    uid: String,
    users: UserRepository,
    pictures: UserPictureRepository,
    onSignOut: () -> Unit,
    home: @Composable () -> Unit,
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
    ProfileSetupDestination.SETUP -> ProfileSetupScreen(model)
    ProfileSetupDestination.HOME -> home()
    // The same message, retry and sign-out as a failed role read: a missing profile can't be fixed
    // by retrying, so signing out must stay possible
    ProfileSetupDestination.ERROR -> ProfileError(model::retry, onSignOut)
  }
}

/** The first-time profile form from the light and dark Sprint 5 setup frames. */
@Composable
private fun ProfileSetupScreen(model: ProfileSetupViewModel) {
  val form by model.form.collectAsStateWithLifecycle()
  val context = LocalContext.current.applicationContext
  val photos = remember(context) { ProofPhotos(context) }
  val scope = rememberCoroutineScope()
  var preparingPhoto by remember { mutableStateOf(false) }
  var photoError by remember { mutableStateOf(false) }
  var preview by remember { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(form.photo) {
    preview =
        form.photo?.let {
          try {
            photos.preview(it)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            null
          }
        }
  }
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
    Spacer(Modifier.height(80.dp))
    Text(
        stringResource(R.string.profile_setup_title),
        style = AroundTextStyles.SetupTitle,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(AroundSpacing.Small))
    Text(
        stringResource(R.string.profile_setup_description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(AroundSpacing.SetupGap))
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          Modifier.size(64.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer)
              .testTag(C.Tag.PROFILE_SETUP_AVATAR),
          contentAlignment = Alignment.Center,
      ) {
        if (preview != null) {
          Image(preview!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
          Image(
              painterResource(R.drawable.ic_profile),
              null,
              Modifier.size(28.dp),
              colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            enabled = !form.saving && !preparingPhoto,
            shape = AroundShapes.medium,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = AroundTheme.colors.accentText,
                ),
            modifier = Modifier.height(44.dp).testTag(C.Tag.PROFILE_SETUP_ADD_PHOTO),
        ) {
          Text(stringResource(R.string.profile_setup_add_photo))
        }
        Text(
            stringResource(R.string.profile_setup_photo_optional),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
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
    val errorText =
        when (form.error) {
          ProfileSetupError.NAME_REQUIRED -> R.string.profile_setup_name_required
          ProfileSetupError.NAME_TOO_LONG -> R.string.profile_setup_name_too_long
          ProfileSetupError.SAVE_FAILED -> R.string.profile_setup_save_failed
          null -> if (photoError) R.string.profile_setup_photo_failed else null
        }
    if (errorText != null) {
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
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag(C.Tag.PROFILE_SETUP_CONTINUE),
    ) {
      Text(
          stringResource(
              if (form.saving) R.string.profile_setup_saving else R.string.profile_setup_continue
          )
      )
    }
  }
}
