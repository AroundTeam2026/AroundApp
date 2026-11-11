// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.venue.AddressSearchUiState
import java.time.LocalDate
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
 * @property venueName The venue's name, to say where the quest happens; empty until it is loaded.
 * @property venueLocation Where the venue is, to centre the radius map; null until it is loaded, or
 *   if the venue has no location.
 * @property questLocation Where the quest happens when the venue placed it away from its own
 *   location, or null to keep it at the venue's.
 * @property addressQuery What the venue typed to find the quest's address.
 * @property addressSearch What that search found.
 * @property venueLoadFailed Whether reading the venue failed, so the form explains why it can't be
 *   saved and offers to read it again.
 * @property retryingVenue Whether the venue is being read again after it failed; the explanation
 *   stays meanwhile, and another retry can't start.
 * @property minPartySize Smallest party allowed, never below [QuestLimits.MIN_PARTY_SIZE].
 * @property exactPartySize Whether the party must have exactly [minPartySize] people rather than at
 *   least that many.
 * @property reward The reward explorers get, if any.
 * @property saveState Where saving the quest stands.
 * @property editing Whether the form edits an existing quest rather than creating one; it then says
 *   "Edit quest" and "Save changes", and shows saving and failure above its button.
 * @property questUnavailable Whether the quest being edited no longer exists or isn't the venue's,
 *   so the form has nothing to edit and closes.
 * @property questLoadFailed Whether reading the quest being edited failed, so the form offers to
 *   read it again.
 * @property partySizeLocked Whether the party size can't change, as the quest being edited has
 *   pending reservations.
 * @property proofTypeLocked Whether the proof type can't change, as the quest being edited has
 *   reservations explorers made for it.
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
    val venueName: String = "",
    val venueLocation: Location? = null,
    val questLocation: Location? = null,
    val addressQuery: String = "",
    val addressSearch: AddressSearchUiState = AddressSearchUiState(),
    val venueLoadFailed: Boolean = false,
    val retryingVenue: Boolean = false,
    val minPartySize: Int = QuestLimits.MIN_PARTY_SIZE,
    val exactPartySize: Boolean = false,
    val reward: RewardFormState = RewardFormState(),
    val saveState: SaveState = SaveState.Idle,
    val editing: Boolean = false,
    val questUnavailable: Boolean = false,
    val questLoadFailed: Boolean = false,
    val partySizeLocked: Boolean = false,
    val proofTypeLocked: Boolean = false,
) {
  /**
   * This form filled in with [quest], its fields untouched, so it opens without errors. The quest
   * stays where it is: at the venue if it has no location of its own. The reward expiry is read in
   * [zone], and is flagged if it is before [today].
   */
  fun filledWith(quest: Quest, zone: ZoneId, today: LocalDate): CreateQuestUiState {
    // Clamped like every other radius, so a quest saved under other limits is saved within these
    val radius =
        quest.radiusMeters.coerceIn(QuestLimits.MIN_RADIUS_METERS, QuestLimits.MAX_RADIUS_METERS)
    return copy(
        title = quest.title,
        description = quest.description,
        requirements = quest.requirements,
        proofType = quest.proofType,
        radiusMeters = radius,
        customRadius = !RadiusPreset.isPreset(radius),
        questLocation = quest.location,
        minPartySize = quest.minPartySize,
        exactPartySize = quest.exactPartySize,
        reward = RewardFormState.fromReward(quest.reward, zone, today),
    )
  }

  /** The preset shown as selected: [radiusMeters] if it is one and not [customRadius], or null. */
  val selectedRadiusPreset: Int?
    get() = radiusMeters.takeIf { !customRadius && RadiusPreset.isPreset(it) }

  /** Whether the quest happens at the venue's own location rather than at [questLocation]. */
  val atVenue: Boolean
    get() = questLocation == null

  /** Where the quest happens: [questLocation], else the venue's; null until the venue is loaded. */
  val location: Location?
    get() = questLocation ?: venueLocation

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
   * The quest to save for [venue], trimmed, as active, at [questLocation], or with no location of
   * its own, so at the venue's, when there is none or it is still the venue's. Only call it when
   * [canSubmit] and the venue has a location. Its id and timestamps are left for the repository to
   * set, and its reward expires at the end of its last day in [zone].
   */
  fun toQuest(venue: Venue, zone: ZoneId): Quest =
      Quest(
          id = "",
          venueId = venue.id,
          radiusMeters = radiusMeters,
          title = title.trim(),
          description = description.trim(),
          requirements = requirements.trim(),
          proofType = proofType,
          minPartySize = minPartySize,
          exactPartySize = exactPartySize,
          // A pin never moved from the venue saves no location, so the quest follows the venue
          location = questLocation?.takeIf { it != venue.location },
          reward = reward.toReward(zone),
          status = QuestStatus.ACTIVE,
          createdAt = 0,
          updatedAt = 0,
      )
}
