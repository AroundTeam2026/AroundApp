// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether an Explorer can enter home or must finish their profile first. */
enum class ProfileSetupDestination {
  LOADING,
  SETUP,
  HOME,
  ERROR,
}

/** The editable first-time profile form. The selected picture survives activity recreation. */
data class ProfileSetupFormState(
    val name: String = "",
    val photo: Uri? = null,
    val saving: Boolean = false,
    val error: ProfileSetupError? = null,
)

/** Why the form can't be saved yet, shown under the name field. */
enum class ProfileSetupError {
  NAME_REQUIRED,
  NAME_TOO_LONG,
  SAVE_FAILED,
}

/**
 * Saves the name and optional picture through repository interfaces, then observes the saved name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupViewModel(
    private val uid: String,
    private val users: UserRepository,
    private val pictures: UserPictureRepository,
) : ViewModel() {
  private val retries = MutableStateFlow(0)

  /**
   * Where the gate goes: [ProfileSetupDestination.ERROR] if the profile can't be read or is
   * missing.
   */
  val destination: StateFlow<ProfileSetupDestination> =
      retries
          .flatMapLatest {
            users
                .observeUser(uid)
                .map { profile ->
                  when {
                    profile == null -> ProfileSetupDestination.ERROR
                    profile.displayName.isBlank() -> ProfileSetupDestination.SETUP
                    else -> ProfileSetupDestination.HOME
                  }
                }
                .catch { emit(ProfileSetupDestination.ERROR) }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileSetupDestination.LOADING)

  private val mutableForm = MutableStateFlow(ProfileSetupFormState())
  /** The form's name, picture, saving flag and error. */
  val form: StateFlow<ProfileSetupFormState> = mutableForm
  private var uploadedPhoto: Pair<Uri, String>? = null

  /** Edits the name and clears the error; ignored while saving. */
  fun setName(name: String) {
    if (!form.value.saving) mutableForm.update { it.copy(name = name, error = null) }
  }

  /** Picks a new picture, or none, to upload on the next save; ignored while saving. */
  fun setPhoto(photo: Uri?) {
    if (!form.value.saving) {
      uploadedPhoto = null
      mutableForm.update { it.copy(photo = photo, error = null) }
    }
  }

  /** Reads the profile again after a load error. */
  fun retry() {
    retries.update { it + 1 }
  }

  /** A second tap cannot start another upload or profile write while the first save is pending. */
  fun save() {
    val current = form.value
    if (current.saving || destination.value != ProfileSetupDestination.SETUP) return
    val name = current.name.trim()
    val validation =
        when {
          name.isEmpty() -> ProfileSetupError.NAME_REQUIRED
          name.length > MAX_NAME_LENGTH -> ProfileSetupError.NAME_TOO_LONG
          else -> null
        }
    if (validation != null) {
      mutableForm.update { it.copy(error = validation) }
      return
    }
    mutableForm.update { it.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
        val location =
            current.photo?.let { photo ->
              uploadedPhoto?.takeIf { it.first == photo }?.second
                  ?: pictures.upload(uid, photo).getOrThrow().also { uploadedPhoto = photo to it }
            }
        users.updateProfile(uid, name, location).getOrThrow()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableForm.update { it.copy(error = ProfileSetupError.SAVE_FAILED) }
      } finally {
        mutableForm.update { it.copy(saving = false) }
      }
    }
  }

  companion object {
    const val MAX_NAME_LENGTH = 100

    fun factory(
        uid: String,
        users: UserRepository,
        pictures: UserPictureRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { ProfileSetupViewModel(uid, users, pictures) }
    }
  }
}
