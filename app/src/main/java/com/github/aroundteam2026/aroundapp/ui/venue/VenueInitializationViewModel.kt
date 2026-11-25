// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.ui.PictureUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why the business name could not be saved. */
enum class VenueInitializationError {
  EMPTY_NAME,
  NAME_TOO_LONG,
  SIGN_IN_REQUIRED,
  SAVE_FAILED,
}

/** [venueId] signals that initialization is complete and location setup can begin. */
data class VenueInitializationUiState(
    val businessName: String = "",
    val isSaving: Boolean = false,
    val error: VenueInitializationError? = null,
    val venueId: String? = null,
    val isLoading: Boolean = false,
    val hasVenue: Boolean = false,
    /** Whether an existing venue is editing its details, rather than setting it up. */
    val editing: Boolean = false,
    /** The picture picked to upload on the next save, or null for none. */
    val photo: Uri? = null,
    /** Whether the saved picture is to be removed on the next save. */
    val removePhoto: Boolean = false,
    /** Where the venue's saved picture is stored, or null without one. */
    val savedPicture: String? = null,
) {
  val canContinue: Boolean
    get() = !isLoading && !isSaving && venueId == null

  val canEditName: Boolean
    get() = canContinue && (editing || !hasVenue)
}

/**
 * Creates the signed-in venue's profile once, before its location is configured. With [editing],
 * the venue edits its details from its profile instead: the name stays editable and is saved with
 * [VenueRepository.setName] before moving on to the location.
 *
 * Either way, the venue can pick a picture, uploaded through [pictures] on save, or remove its own;
 * the picture is then saved with [VenueRepository.setPicture], once the venue exists.
 */
class VenueInitializationViewModel(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    pictures: UserPictureRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    editing: Boolean = false,
) : ViewModel() {
  private val mutableUiState =
      MutableStateFlow(VenueInitializationUiState(isLoading = true, editing = editing))

  /** The name as loaded, so editing skips the write when it's unchanged. */
  private var loadedName: String? = null
  private val upload = PictureUpload(pictures)
  val uiState = mutableUiState.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        val uid = authRepository.currentUserId.value
        val venue = if (uid.isNullOrBlank()) null else venueRepository.getVenue(uid)
        if (venue != null) {
          loadedName = venue.name
          mutableUiState.value =
              uiState.value.copy(
                  businessName = venue.name,
                  hasVenue = true,
                  savedPicture = venue.pictureUrl,
              )
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableUiState.value = uiState.value.copy(error = VenueInitializationError.SAVE_FAILED)
      } finally {
        mutableUiState.value = uiState.value.copy(isLoading = false)
      }
    }
  }

  /** Consumes the navigation signal so Back from location does not immediately reopen it. */
  fun onLocationNavigationHandled() {
    mutableUiState.value = uiState.value.copy(venueId = null)
  }

  /** Sets the typed business name and clears any error, unless the name is locked. */
  fun updateBusinessName(name: String) {
    if (!uiState.value.canEditName) return
    mutableUiState.value = uiState.value.copy(businessName = name, error = null)
  }

  /** Picks a new picture to upload on the next save; ignored while the form is locked. */
  fun setPhoto(photo: Uri) {
    if (!uiState.value.canContinue) return
    mutableUiState.value = uiState.value.copy(photo = photo, removePhoto = false, error = null)
  }

  /** Marks the picture for removal on the next save; ignored while the form is locked. */
  fun removePhoto() {
    if (!uiState.value.canContinue) return
    mutableUiState.value = uiState.value.copy(photo = null, removePhoto = true, error = null)
  }

  /**
   * Validates the name and creates the venue if needed, then signals location setup through
   * [uiState].
   */
  fun submit() {
    val state = uiState.value
    if (!state.canContinue) return
    val name = state.businessName.trim()
    // An existing venue's name is only checked when editing; setup keeps it as it is
    val invalid = if (state.editing || !state.hasVenue) nameError(name) else null
    if (invalid != null) {
      mutableUiState.value = state.copy(error = invalid)
      return
    }
    val uid = authRepository.currentUserId.value
    if (uid.isNullOrBlank()) {
      mutableUiState.value = state.copy(error = VenueInitializationError.SIGN_IN_REQUIRED)
      return
    }

    mutableUiState.value = state.copy(isSaving = true, error = null)
    viewModelScope.launch {
      try {
        val savedName = save(uid, name, state.editing)
        val savedPicture = savePicture(uid, state)
        mutableUiState.value =
            uiState.value.copy(
                isSaving = false,
                venueId = uid,
                businessName = savedName,
                hasVenue = true,
                photo = null,
                removePhoto = false,
                savedPicture = savedPicture,
            )
      } catch (cancelled: CancellationException) {
        mutableUiState.value = uiState.value.copy(isSaving = false)
        throw cancelled
      } catch (_: Exception) {
        mutableUiState.value =
            uiState.value.copy(isSaving = false, error = VenueInitializationError.SAVE_FAILED)
      }
    }
  }

  private fun nameError(name: String): VenueInitializationError? =
      when {
        name.isEmpty() -> VenueInitializationError.EMPTY_NAME
        name.length > VenueLimits.MAX_NAME_LENGTH -> VenueInitializationError.NAME_TOO_LONG
        else -> null
      }

  /**
   * Saves [name] and returns the venue's name. Editing always renames, even if the first read
   * failed, unless the name is unchanged; setup creates the venue unless it already exists.
   */
  private suspend fun save(uid: String, name: String, editing: Boolean): String {
    if (!editing) return getOrCreateVenue(uid, name).name
    if (name != loadedName) venueRepository.setName(uid, name).getOrThrow()
    return name
  }

  /**
   * Saves the picture [state] asks for and returns where the saved one is. Writes nothing when the
   * picture is unchanged, and uploads a picked one only once, even when the save is retried.
   */
  private suspend fun savePicture(uid: String, state: VenueInitializationUiState): String? {
    val saved = state.savedPicture
    val location = upload.locationOf(uid, state.photo, state.removePhoto, saved)
    if (location != saved) venueRepository.setPicture(uid, location).getOrThrow()
    return location
  }

  private suspend fun getOrCreateVenue(uid: String, name: String): Venue {
    venueRepository.getVenue(uid)?.let {
      return it
    }
    val venue =
        Venue(
            id = uid,
            name = name,
            location = null,
            radiusMeters = VenueLimits.DEFAULT_RADIUS_METERS,
            address = null,
            createdAt = currentTimeMillis(),
        )
    val result = venueRepository.createVenue(venue)
    if (result.isSuccess) return venue
    // Another initialization may have won the atomic create in the meantime.
    return venueRepository.getVenue(uid) ?: throw result.exceptionOrNull()!!
  }
}
