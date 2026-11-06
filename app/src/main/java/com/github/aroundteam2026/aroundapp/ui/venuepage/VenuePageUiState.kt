// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.ui.quest.CancelStatus
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar

/**
 * What a venue's page shows: its header and the quests explorers can take there.
 *
 * The header and [quests] are only meaningful once [status] is [VenuePageStatus.LOADED]; before
 * that they keep their defaults.
 *
 * @property status Whether the page is loading, shown, or why it can't be.
 * @property venueName The venue's name, shown in the header.
 * @property avatar What the header shows for the venue, or null until the venue is loaded.
 * @property address The venue's street address, or null if it gave none.
 * @property venueLocation Where the venue is, or null until it confirms its marker on the map.
 * @property quests The venue's valid quests, in the order the page lists them.
 * @property selectedQuestId Id of the quest the explorer picked to accept, or null when none is
 *   picked.
 * @property detailQuestId Id of the quest whose details are open, or null when none is.
 * @property pickBeforeDetails What closing the details picks: the pick from before they opened, or
 *   their quest once the explorer accepted it. Null while they are closed.
 * @property acceptedParties For each quest of this venue the explorer has accepted, how many people
 *   its party has. A pending or approved reservation counts; a rejected or cancelled one does not.
 * @property reservationIds For each quest in [acceptedParties], the id of its reservation.
 * @property acceptingQuestIds Ids of the quests being accepted right now.
 * @property failedQuestIds Ids of the quests whose last accept failed: the write could not be
 *   issued, or the server refused it afterwards.
 * @property cancellingIds Ids of the reservations being cancelled right now.
 * @property failedCancelIds Ids of the reservations whose last cancel failed.
 * @property acceptAvailable False once the explorer's reservations can't be read, since then the
 *   page can't tell what they accepted and so doesn't book anything.
 * @property approvedIds Ids of the explorer's reservations the venue approved, whose quest they can
 *   now complete.
 * @property completions The proofs the explorer sent, by reservation id.
 * @property proofsAvailable False once the explorer's proofs can't be read, since then the page
 *   can't tell if one was sent and so doesn't offer to send one.
 */
data class VenuePageUiState(
    val status: VenuePageStatus = VenuePageStatus.LOADING,
    val venueName: String = "",
    val avatar: VenueAvatar? = null,
    val address: String? = null,
    val venueLocation: Location? = null,
    val quests: List<VenuePageQuest> = emptyList(),
    val selectedQuestId: String? = null,
    val detailQuestId: String? = null,
    val pickBeforeDetails: String? = null,
    val acceptedParties: Map<String, Int> = emptyMap(),
    val reservationIds: Map<String, String> = emptyMap(),
    val acceptingQuestIds: Set<String> = emptySet(),
    val failedQuestIds: Set<String> = emptySet(),
    val cancellingIds: Set<String> = emptySet(),
    val failedCancelIds: Set<String> = emptySet(),
    val acceptAvailable: Boolean = true,
    val approvedIds: Set<String> = emptySet(),
    val completions: Map<String, Completion> = emptyMap(),
    val proofsAvailable: Boolean = true,
) {
  /** The picked quest, or null when none is picked or it is no longer in [quests]. */
  val selectedQuest: VenuePageQuest?
    get() = selectedQuestId?.let { id -> quests.find { it.questId == id } }

  /** The quest whose details are open, or null when none are or it is no longer in [quests]. */
  val detailQuest: VenuePageQuest?
    get() = detailQuestId?.let { id -> quests.find { it.questId == id } }

  /** How accepting stands for the picked quest; [AcceptStatus.NONE] when none is picked. */
  val acceptStatus: AcceptStatus
    get() {
      val id = selectedQuestId ?: return AcceptStatus.NONE
      return when {
        id in acceptedParties -> AcceptStatus.ACCEPTED
        id in acceptingQuestIds -> AcceptStatus.ACCEPTING
        id in failedQuestIds -> AcceptStatus.FAILED
        else -> AcceptStatus.NONE
      }
    }

  /**
   * The reservation the explorer can cancel for the picked quest, or null when there is none. While
   * the reservations can't be read it is null too, since the page can't tell if it still stands.
   */
  val cancellableReservationId: String?
    get() = selectedQuestId?.takeIf { acceptAvailable }?.let(reservationIds::get)

  /**
   * The reservation whose quest the explorer can complete, for the picked quest: the one they can
   * cancel, once the venue approved it and until they send a proof. Null otherwise, and while the
   * proofs can't be read.
   */
  val completableReservationId: String?
    get() = cancellableReservationId?.takeIf {
      it in approvedIds && proofsAvailable && it !in completions
    }

  /** The proof the explorer sent for the picked quest, or null when there is none. */
  val proof: Completion?
    get() = selectedQuestId?.let(reservationIds::get)?.let(completions::get)

  /** How cancelling the picked quest's reservation stands; [CancelStatus.NONE] when none is. */
  val cancelStatus: CancelStatus
    get() =
        when (selectedQuestId?.let(reservationIds::get)) {
          in cancellingIds -> CancelStatus.CANCELLING
          in failedCancelIds -> CancelStatus.FAILED
          else -> CancelStatus.NONE
        }

  /**
   * How many more people the picked quest needs: its minimum party size less the party's size,
   * never below 0. Until the quest is accepted the party is the explorer alone. 0 when none is
   * picked.
   */
  val morePeopleNeeded: Int
    get() {
      val quest = selectedQuest ?: return 0
      val party = acceptedParties[quest.questId] ?: SOLO_PARTY
      return (quest.minPartySize - party).coerceAtLeast(0)
    }

  /**
   * This page listing [quests] instead. A picked quest that isn't among them is unpicked, so it
   * isn't picked again if it comes back, as when its venue extends an expired reward; likewise, its
   * details close, which gives back the pick from before they opened.
   */
  fun withQuests(quests: List<VenuePageQuest>): VenuePageUiState {
    fun String?.ifListed() = this?.takeIf { id -> quests.any { it.questId == id } }
    val detailsClosed = detailQuestId != null && detailQuestId.ifListed() == null
    val pick = if (detailsClosed) pickBeforeDetails else selectedQuestId
    return copy(
        quests = quests,
        selectedQuestId = pick.ifListed(),
        detailQuestId = detailQuestId.ifListed(),
        pickBeforeDetails = pickBeforeDetails.ifListed().takeIf { !detailsClosed },
    )
  }

  /**
   * This page with the details of the quest [questId] open, and that quest picked, since the
   * details offer to accept it, until [withoutQuestDetails] gives back the earlier pick. A quest it
   * doesn't list changes nothing.
   */
  fun withQuestDetails(questId: String): VenuePageUiState =
      if (quests.any { it.questId == questId }) {
        copy(
            detailQuestId = questId,
            selectedQuestId = questId,
            pickBeforeDetails = selectedQuestId,
        )
      } else {
        this
      }

  /** This page with the details closed and the pick from before they opened back. */
  fun withoutQuestDetails(): VenuePageUiState =
      if (detailQuestId == null) {
        this
      } else {
        copy(detailQuestId = null, selectedQuestId = pickBeforeDetails, pickBeforeDetails = null)
      }

  /**
   * This page once the explorer tapped the quest [questId]: picked, or unpicked if it was picked. A
   * quest the page doesn't list changes nothing.
   */
  fun withQuestTapped(questId: String): VenuePageUiState =
      when {
        selectedQuestId == questId -> copy(selectedQuestId = null)
        quests.none { it.questId == questId } -> this
        else -> copy(selectedQuestId = questId)
      }
}

/** How accepting the picked quest stands. */
enum class AcceptStatus {
  /** Not accepted, and not being accepted. */
  NONE,
  /** The reservation is being created. */
  ACCEPTING,
  /** The explorer has a pending or approved reservation for the quest. */
  ACCEPTED,
  /** The last try failed; accepting again retries. */
  FAILED,
}

/** The party of an explorer who has not formed one yet: themself. */
internal const val SOLO_PARTY = 1

/** Where the venue page stands. */
enum class VenuePageStatus {
  /** The venue and its quests are being fetched. */
  LOADING,
  /** The venue was found; the page shows it, even when it has no quests. */
  LOADED,
  /** No venue has this id. */
  NOT_FOUND,
  /** The venue or its quests could not be fetched; trying again may work. */
  FAILED,
}

/**
 * One quest as the venue page lists it.
 *
 * @property questId Id of the quest.
 * @property title The quest's title.
 * @property description What the quest is about.
 * @property requirements What explorers must do to complete it.
 * @property proofType The kind of proof explorers submit.
 * @property minPartySize How many people the quest needs at least.
 * @property reward The quest's reward, or null when it has none.
 * @property exactPartySize Whether the party must have exactly [minPartySize] people.
 * @property radiusMeters How close to the venue explorers must be to send their proof.
 */
data class VenuePageQuest(
    val questId: String,
    val title: String,
    val description: String,
    val requirements: String,
    val proofType: ProofType,
    val minPartySize: Int,
    val reward: Reward?,
    val exactPartySize: Boolean = false,
    val radiusMeters: Int = QuestLimits.DEFAULT_RADIUS_METERS,
)
