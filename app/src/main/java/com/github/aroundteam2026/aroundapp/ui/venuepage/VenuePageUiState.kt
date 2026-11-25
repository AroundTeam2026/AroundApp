// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_SIZE
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.ui.proof.ProofSender
import com.github.aroundteam2026.aroundapp.ui.quest.CancelStatus
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.review.ProofPhoto

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
 * @property arrivalQuestId Id of the quest whose details the page opened as it arrived, as from a
 *   card on the Quests tab, while they are open: closing them leaves the page instead of showing
 *   its list, since the explorer never saw that list. Null otherwise.
 * @property arrivalMissed The page was to arrive at a quest it doesn't list, such as one whose
 *   reward expired, and shows its list instead; true until the page has said so once.
 * @property focusQuestId Id of the quest the page scrolls to as it arrives, as from a card on the
 *   map, until it has; null otherwise.
 * @property pickBeforeDetails What closing the details picks: the pick from before they opened, or
 *   their quest once the explorer accepted it. Null while they are closed.
 * @property acceptedParties For each quest of this venue the explorer has accepted, how many people
 *   its party has. A pending or approved reservation counts; a rejected or cancelled one does not.
 * @property reservationIds For each quest in [acceptedParties], the id of its reservation.
 * @property unsentIds Ids of the explorer's reservations the server hasn't confirmed yet, as when
 *   they were accepted offline: their quests are accepted, but [AcceptStatus.SENDING].
 * @property acceptingQuestIds Ids of the quests being accepted right now.
 * @property failedQuestIds Ids of the quests whose last accept failed: the write could not be
 *   issued, or the server refused it afterwards.
 * @property cancellingIds Ids of the reservations being cancelled right now.
 * @property failedCancelIds Ids of the reservations whose last cancel failed.
 * @property acceptAvailable False once the explorer's reservations can't be read, since then the
 *   page can't tell what they accepted and so doesn't book anything.
 * @property approvedIds Ids of the explorer's reservations the venue approved, whose quest they can
 *   now complete.
 * @property completions The proofs of the explorer's parties, whichever member sent them, by
 *   reservation id.
 * @property proofSenders The other members who sent some of [completions], by reservation id; a
 *   proof the explorer sent themselves isn't in it.
 * @property proofsAvailable False once those proofs can't be read, since then the page can't tell
 *   if one was sent and so doesn't offer to send one.
 * @property proofPhotos The downloaded photos of the photo proofs in [completions], by location.
 * @property failedProofPhotos The locations of those photos whose download failed.
 * @property partyCodes The party codes of the reservations the explorer created and friends can
 *   still join, by reservation id.
 * @property partyMembers The joined members of the explorer's parties here, by reservation id.
 * @property leavesParties Whether the explorer joined the party of each reservation rather than
 *   created it, by reservation id, known as soon as the parties are read. A reservation whose party
 *   doesn't list the explorer as joined isn't in it.
 * @property offline Whether the device is offline, so cancelling and accepting, which need a
 *   connection, are unavailable.
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
    val arrivalQuestId: String? = null,
    val arrivalMissed: Boolean = false,
    val focusQuestId: String? = null,
    val pickBeforeDetails: String? = null,
    val acceptedParties: Map<String, Int> = emptyMap(),
    val reservationIds: Map<String, String> = emptyMap(),
    val unsentIds: Set<String> = emptySet(),
    val acceptingQuestIds: Set<String> = emptySet(),
    val failedQuestIds: Set<String> = emptySet(),
    val cancellingIds: Set<String> = emptySet(),
    val failedCancelIds: Set<String> = emptySet(),
    val acceptAvailable: Boolean = true,
    val approvedIds: Set<String> = emptySet(),
    val completions: Map<String, Completion> = emptyMap(),
    val proofSenders: Map<String, ProofSender> = emptyMap(),
    val proofsAvailable: Boolean = true,
    val proofPhotos: Map<String, ImageBitmap> = emptyMap(),
    val failedProofPhotos: Set<String> = emptySet(),
    val partyCodes: Map<String, String> = emptyMap(),
    val partyMembers: Map<String, List<PartyMemberUi>> = emptyMap(),
    val leavesParties: Map<String, Boolean> = emptyMap(),
    val offline: Boolean = false,
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
        id in acceptedParties ->
            if (reservationIds[id] in unsentIds) AcceptStatus.SENDING else AcceptStatus.ACCEPTED
        id in acceptingQuestIds -> AcceptStatus.ACCEPTING
        id in failedQuestIds -> AcceptStatus.FAILED
        else -> AcceptStatus.NONE
      }
    }

  /**
   * The reservation the explorer can cancel for the picked quest, or null when there is none. Once
   * any member of its party sent its proof, whatever the proof's status, it can't be cancelled, as
   * on the Quests tab and as the rules enforce. Nor can it while the server doesn't have it yet
   * ([unsentIds]): there is nothing to cancel. While the reservations can't be read it is null too,
   * since the page can't tell if it still stands.
   */
  val cancellableReservationId: String?
    get() =
        selectedQuestId
            ?.takeIf { acceptAvailable }
            ?.let(reservationIds::get)
            ?.takeIf { it !in completions && it !in unsentIds }

  /**
   * Whether the explorer leaves the party of [cancellableReservationId] rather than cancelling it,
   * since someone else made it. The rules would let any joined member cancel it, but the app only
   * lets its creator, so a member can't cancel everyone's reservation. Null without such a
   * reservation, or when the explorer's membership isn't found, so neither is known to be safe.
   */
  val leavesParty: Boolean?
    get() = cancellableReservationId?.let(leavesParties::get)

  /**
   * The reservation whose quest the explorer can complete, for the picked quest: the one they can
   * cancel, once the venue approved it and until they send a proof. Null otherwise, and while the
   * proofs can't be read.
   */
  val completableReservationId: String?
    get() = cancellableReservationId?.takeIf {
      it in approvedIds && proofsAvailable && it !in completions
    }

  /** The party's proof for the picked quest, or null when there is none. */
  val proof: Completion?
    get() = selectedQuestId?.let(reservationIds::get)?.let(completions::get)

  /** Where the photo of [proof] stands, as the review screen shows it; null for a text proof. */
  val proofPhoto: ProofPhoto?
    get() =
        (proof?.proof as? Proof.Photo)?.location?.let { location ->
          proofPhotos[location]?.let(ProofPhoto::Loaded)
              ?: if (location in failedProofPhotos) ProofPhoto.Failed else ProofPhoto.Loading
        }

  /** Who sent [proof], when another member of the party did; null when the explorer did. */
  val proofSender: ProofSender?
    get() = selectedQuestId?.let(reservationIds::get)?.let(proofSenders::get)

  /**
   * The code the explorer shares so friends can join their party for the picked quest: only for a
   * reservation they created, while friends can join it, so while it is pending and its party isn't
   * full. Null otherwise, while the reservations can't be read, and while the server doesn't have
   * the reservation yet ([unsentIds]), since friends can't find its code before then.
   */
  val partyCode: String?
    get() {
      val quest = selectedQuest?.takeIf { acceptAvailable } ?: return null
      val party = acceptedParties[quest.questId] ?: SOLO_PARTY
      return reservationIds[quest.questId]
          ?.takeIf { it !in unsentIds }
          ?.let(partyCodes::get)
          ?.takeIf { party < quest.partyCapacity }
    }

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
   * never below 0. Until the quest is accepted it counts the explorer's own seat, though the party
   * card shows 0 joined then, so the bar says the explorer is included. 0 when none is picked.
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
   * details close, which gives back the pick from before they opened, and they no longer count as
   * the details the page arrived at.
   */
  fun withQuests(quests: List<VenuePageQuest>): VenuePageUiState {
    fun String?.ifListed() = this?.takeIf { id -> quests.any { it.questId == id } }
    val detailsClosed = detailQuestId != null && detailQuestId.ifListed() == null
    val pick = if (detailsClosed) pickBeforeDetails else selectedQuestId
    return copy(
        quests = quests,
        selectedQuestId = pick.ifListed(),
        detailQuestId = detailQuestId.ifListed(),
        arrivalQuestId = arrivalQuestId.takeIf { !detailsClosed },
        focusQuestId = focusQuestId.ifListed(),
        pickBeforeDetails = pickBeforeDetails.ifListed().takeIf { !detailsClosed },
    )
  }

  /**
   * This page arriving at the details of the quest [questId], as from a card on the Quests tab:
   * they open as with [withQuestDetails], and count as the [arrivalQuestId]. A quest it doesn't
   * list, such as one whose reward has expired, leaves its list shown, with [arrivalMissed] set so
   * that the page says why. A page that isn't loaded, e.g. because the venue is gone, says so
   * itself and changes nothing.
   */
  fun arrivingAt(questId: String): VenuePageUiState =
      when {
        status != VenuePageStatus.LOADED -> this
        quests.none { it.questId == questId } -> copy(arrivalMissed = true)
        else -> withQuestDetails(questId).copy(arrivalQuestId = questId)
      }

  /**
   * This page arriving at the quest [questId] in its list, as from a card on the map: picked, and
   * the [focusQuestId] the list scrolls to. A quest it doesn't list, or a page that isn't loaded,
   * changes nothing, leaving the list at its top.
   */
  fun focusingOn(questId: String): VenuePageUiState =
      if (status == VenuePageStatus.LOADED && quests.any { it.questId == questId }) {
        copy(selectedQuestId = questId, focusQuestId = questId)
      } else {
        this
      }

  /** Whether closing the open details leaves the page, since it arrived at them. */
  val detailsLeaveThePage: Boolean
    get() = detailQuestId != null && detailQuestId == arrivalQuestId

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
        copy(
            detailQuestId = null,
            arrivalQuestId = null,
            selectedQuestId = pickBeforeDetails,
            pickBeforeDetails = null,
        )
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
  /** Accepted, but the server hasn't confirmed it yet: it is sent once the device is online. */
  SENDING,
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
 * @property radiusMeters How close to [location] explorers must be to send their proof.
 * @property location Where the quest takes place, or null when at the venue.
 * @property partyCapacity The most explorers its party can have, as `Quest.partyCapacity`.
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
    val location: Location? = null,
    val partyCapacity: Int = MAX_PARTY_SIZE,
)
