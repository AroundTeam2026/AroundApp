// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import java.time.ZoneId
import kotlin.math.ceil

/** An average walking pace, about 5 km/h. */
private const val WALKING_METERS_PER_MINUTE = 80.0

/** The radii offered as one-tap choices, each with the place it reaches. */
enum class RadiusPreset(val meters: Int) {
  INSIDE(25),
  AT_THE_DOOR(50),
  ON_THE_STREET(100);

  companion object {
    /** Whether [meters] is one of the presets. */
    fun isPreset(meters: Int) = entries.any { it.meters == meters }
  }
}

/** Why a text field of the quest form is invalid. */
enum class QuestTextError {
  /** Empty or whitespace only. */
  BLANK,
  /** Longer than the field's limit once trimmed. */
  TOO_LONG,
}

/**
 * The error of a quest text field holding [value], or null if it is valid: not blank, and at most
 * [maxLength] characters once trimmed, since the quest is saved trimmed.
 */
internal fun validateQuestText(value: String, maxLength: Int): QuestTextError? {
  val trimmed = value.trim()
  return when {
    trimmed.isEmpty() -> QuestTextError.BLANK
    trimmed.length > maxLength -> QuestTextError.TOO_LONG
    else -> null
  }
}

/**
 * The error of [value] to show: as [validateQuestText], but no [QuestTextError.BLANK] until
 * [touched].
 */
internal fun shownQuestTextError(value: String, maxLength: Int, touched: Boolean): QuestTextError? {
  val error = validateQuestText(value, maxLength)
  return if (error == QuestTextError.BLANK && !touched) null else error
}

/** Where saving the quest stands. */
sealed interface SaveState {
  /** Not saved yet. */
  data object Idle : SaveState

  /** Being saved; the form can't be saved again meanwhile. */
  data object Saving : SaveState

  /** Saved as the quest [questId]. */
  data class Saved(val questId: String) : SaveState

  /** The last save failed; the form keeps its values and can be saved again. */
  data object Failed : SaveState
}

/**
 * The quest form being filled in by a venue.
 *
 * Errors are derived from the values, so they can never disagree with them. A
 * [QuestTextError.BLANK] error is only shown once its field has been edited, so the form doesn't
 * open full of errors; [QuestTextError.TOO_LONG] shows at once.
 *
 * The completion rules always hold a valid value, so they never block [canSubmit]; an invalid
 * [reward] does.
 *
 * @property titleTouched Whether [title] has been edited; likewise for the other `Touched` flags.
 * @property proofType The kind of proof explorers submit.
 * @property radiusMeters Distance within which a visit counts, always within [QuestLimits].
 * @property customRadius Whether the venue chose a custom radius rather than one of [RadiusPreset],
 *   which shows the radius slider.
 * @property venueLocation Where the venue is, to centre the radius map; null until it is loaded, or
 *   if the venue has no location.
 * @property minPartySize Smallest party allowed, never below [QuestLimits.MIN_PARTY_SIZE].
 * @property exactPartySize Whether the party must have exactly [minPartySize] people rather than at
 *   least that many.
 * @property reward The reward explorers get, if any.
 * @property saveState Where saving the quest stands.
 */
data class CreateQuestUiState(
    val title: String = "",
    val description: String = "",
    val requirements: String = "",
    val titleTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
    val requirementsTouched: Boolean = false,
    val proofType: ProofType = ProofType.PHOTO,
    val radiusMeters: Int = QuestLimits.DEFAULT_RADIUS_METERS,
    val customRadius: Boolean = false,
    val venueLocation: Location? = null,
    val minPartySize: Int = QuestLimits.MIN_PARTY_SIZE,
    val exactPartySize: Boolean = false,
    val reward: RewardFormState = RewardFormState(),
    val saveState: SaveState = SaveState.Idle,
) {
  /** The preset shown as selected: [radiusMeters] if it is one and not [customRadius], or null. */
  val selectedRadiusPreset: Int?
    get() = radiusMeters.takeIf { !customRadius && RadiusPreset.isPreset(it) }

  /** About how many minutes it takes to walk across [radiusMeters], never less than one. */
  val walkMinutes: Int
    get() = ceil(radiusMeters / WALKING_METERS_PER_MINUTE).toInt().coerceAtLeast(1)

  /** The error shown under the title, or null. */
  val titleError: QuestTextError?
    get() = shownQuestTextError(title, QuestLimits.TITLE_MAX_LENGTH, titleTouched)

  /** The error shown under the description, or null. */
  val descriptionError: QuestTextError?
    get() = shownQuestTextError(description, QuestLimits.DESCRIPTION_MAX_LENGTH, descriptionTouched)

  /** The error shown under the requirements, or null. */
  val requirementsError: QuestTextError?
    get() =
        shownQuestTextError(requirements, QuestLimits.REQUIREMENTS_MAX_LENGTH, requirementsTouched)

  /** Whether every field is valid, touched or not. */
  val canSubmit: Boolean
    get() =
        validateQuestText(title, QuestLimits.TITLE_MAX_LENGTH) == null &&
            validateQuestText(description, QuestLimits.DESCRIPTION_MAX_LENGTH) == null &&
            validateQuestText(requirements, QuestLimits.REQUIREMENTS_MAX_LENGTH) == null &&
            reward.isValid

  /**
   * Whether the quest can be saved now: [canSubmit], the venue's location is known, and it is
   * neither being saved nor already saved.
   */
  val canSave: Boolean
    get() =
        canSubmit &&
            venueLocation != null &&
            (saveState == SaveState.Idle || saveState == SaveState.Failed)

  /**
   * The quest to save for [venue], trimmed, as active, where the venue is. Only call it when
   * [canSubmit] and the venue has a location. Its id and timestamps are left for the repository to
   * set, and its reward expires at the end of its last day in [zone].
   */
  fun toQuest(venue: Venue, zone: ZoneId): Quest =
      Quest(
          id = "",
          venueId = venue.id,
          venueName = venue.name,
          location = requireNotNull(venue.location),
          radiusMeters = radiusMeters,
          title = title.trim(),
          description = description.trim(),
          requirements = requirements.trim(),
          proofType = proofType,
          minPartySize = minPartySize,
          exactPartySize = exactPartySize,
          reward = reward.toReward(zone),
          status = QuestStatus.ACTIVE,
          createdAt = 0,
          updatedAt = 0,
      )
}
