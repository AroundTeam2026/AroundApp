// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.observeJoinedCounts
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.isActive
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.map.INITIALS_AVATAR
import com.github.aroundteam2026.aroundapp.ui.map.NEWEST_FIRST
import com.github.aroundteam2026.aroundapp.ui.quest.CancelStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the page of the venue [venueId]: its header and the quests explorers can take there.
 *
 * It keeps up with the venue, its active quests, the reservations of [explorerUid] with their
 * parties and the proofs they sent, and with rewards expiring by [clock]. If the venue or its
 * quests fail to load, the page is [VenuePageStatus.FAILED] until [onRetry]; if the reservations
 * fail, accepting is unavailable ([VenuePageUiState.acceptAvailable]) until the page reopens, and
 * if the proofs fail, so is sending one ([VenuePageUiState.proofsAvailable]). Accepting names the
 * explorer as in their profile.
 */
class VenuePageViewModel(
    private val venueId: String,
    private val venueRepository: VenueRepository,
    private val questRepository: QuestRepository,
    private val reservationRepository: ReservationRepository,
    private val userRepository: UserRepository,
    private val partyRepository: PartyRepository,
    private val completionRepository: CompletionRepository,
    private val explorerUid: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(VenuePageUiState())
  /** What the page shows now; the page observes it. */
  val uiState: StateFlow<VenuePageUiState> = mutableUiState.asStateFlow()

  private var listening: Job? = null

  /**
   * The reservations this page created, by reservation id. One that was shown as pending and then
   * vanishes was refused by the server, see [refusedWrites].
   */
  private val written = mutableMapOf<String, Write>()

  private class Write(val questId: String, var shown: Boolean = false)

  /**
   * The explorer's [reservations] and the number of joined members of the pending and approved ones
   * at this venue, by reservation id.
   */
  private data class Booked(val reservations: List<Reservation>, val partySizes: Map<String, Int>)

  /**
   * The quests whose reservation this page is creating and the backend hasn't answered yet.
   * Firestore lists such a write at once, before the server accepts or refuses it, so it doesn't
   * count as accepted until [ReservationRepository.createReservation] returns: the page shows
   * "accepting" instead of "accepted" followed by a failure.
   */
  private val unconfirmed = mutableSetOf<String>()

  /**
   * The quests whose reservation the backend just refused. Firestore may list the refused write
   * before or after the refusal arrives, so the quest doesn't count as accepted until the
   * reservations no longer list anything for it.
   */
  private val refusing = mutableSetOf<String>()

  /** What the page last heard from its listeners, so it can be shown again; null when failed. */
  private var latest: Heard<VenuePageQuest>? = null

  /** What the listeners sent; [booked] or [completions] is null once its listener failed. */
  private data class Heard<Q>(
      val venue: Venue?,
      val quests: List<Q>,
      val booked: Booked?,
      val completions: List<Completion>?,
  )

  init {
    listen()
  }

  /** Called when the explorer taps the quest [questId]; see [VenuePageUiState.withQuestTapped]. */
  fun onQuestSelected(questId: String) {
    mutableUiState.update { it.withQuestTapped(questId) }
  }

  /** Called when the explorer opens the details of the quest [questId]. */
  fun onQuestDetailsOpened(questId: String) {
    mutableUiState.update { it.withQuestDetails(questId) }
  }

  /** Called when the explorer closes the quest details, however they close them. */
  fun onQuestDetailsClosed() {
    mutableUiState.update { it.withoutQuestDetails() }
  }

  /**
   * Books the picked quest as a pending reservation, with the explorer as its party's first member,
   * for a slot starting now.
   *
   * Does nothing when no quest is picked, accepting is unavailable, or that quest is already
   * accepted or being accepted, so a double tap books once; other quests can still be accepted
   * meanwhile. The quest shows as accepted only once the backend answered. A failure, a later
   * server refusal or an unreadable profile keeps it picked and shows [AcceptStatus.FAILED];
   * calling this again retries. A quest accepted from its details stays picked once they close.
   */
  fun accept() {
    val current = mutableUiState.value
    val quest = current.selectedQuest ?: return
    val id = quest.questId
    if (
        !current.acceptAvailable || id in current.acceptingQuestIds || id in current.acceptedParties
    ) {
      return
    }
    mutableUiState.update {
      it.copy(
          acceptingQuestIds = it.acceptingQuestIds + id,
          failedQuestIds = it.failedQuestIds - id,
          pickBeforeDetails = if (it.detailQuestId == id) id else it.pickBeforeDetails,
      )
    }
    unconfirmed += id
    refusing -= id
    viewModelScope.launch {
      val result =
          explorerName()
              .fold(
                  onSuccess = { name ->
                    reservationRepository.createReservation(
                        newPendingReservation(id, venueId, name, clock()),
                        explorerUid,
                    )
                  },
                  onFailure = { Result.failure(it) },
              )
      unconfirmed -= id
      result
          .onSuccess { reservationId ->
            // The page stays "accepting" until the reservation shows up in the listener
            written[reservationId] = Write(id)
            show()
          }
          .onFailure {
            Log.w(TAG, "Accepting quest $id failed", it)
            refusing += id
            mutableUiState.update { state ->
              state.copy(
                  acceptingQuestIds = state.acceptingQuestIds - id,
                  failedQuestIds = state.failedQuestIds + id,
              )
            }
          }
    }
  }

  /**
   * Called when the explorer cancels their reservation for the picked quest, once they confirmed.
   * Does nothing without such a reservation, or while it is being cancelled, so a double tap
   * cancels once. It shows as cancelling until the reservations no longer list it. A failure, such
   * as being offline, shows [CancelStatus.FAILED]; calling this again retries.
   */
  fun cancel() {
    val current = mutableUiState.value
    val reservationId = current.cancellableReservationId ?: return
    if (reservationId in current.cancellingIds) return
    mutableUiState.update {
      it.copy(
          cancellingIds = it.cancellingIds + reservationId,
          failedCancelIds = it.failedCancelIds - reservationId,
      )
    }
    viewModelScope.launch {
      reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELLED).onFailure {
        Log.w(TAG, "Cancelling reservation $reservationId failed", it)
        mutableUiState.update { state ->
          // A reservation that left meanwhile, e.g. rejected by the venue, has nothing to mark
          val shown = reservationId in state.reservationIds.values
          state.copy(
              cancellingIds = state.cancellingIds - reservationId,
              failedCancelIds =
                  if (shown) state.failedCancelIds + reservationId else state.failedCancelIds,
          )
        }
      }
    }
  }

  /**
   * The display name in the explorer's profile, which the venue sees on their reservation, or a
   * failure if the profile is missing or can't be read.
   */
  private suspend fun explorerName(): Result<String> =
      try {
        val profile =
            userRepository.getUser(explorerUid)
                ?: throw NoSuchElementException("No profile for $explorerUid")
        Result.success(profile.displayName)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /** Called when the explorer asks to try again after a failure; does nothing otherwise. */
  fun onRetry() {
    if (mutableUiState.value.status != VenuePageStatus.FAILED) return
    mutableUiState.value = VenuePageUiState()
    written.clear()
    refusing.clear()
    latest = null
    listen()
  }

  /**
   * Listens to the venue, its quests, the explorer's reservations and their parties, and the proofs
   * they sent until the ViewModel is cleared or listening fails. The quests are checked again each
   * time a reward expires, even when nothing else changes. A failure of the reservations or parties
   * only is not the page's: it goes on without them, with accepting unavailable. Nor is a failure
   * of the proofs, with sending one unavailable.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen() {
    listening?.cancel()
    listening = viewModelScope.launch {
      combine(
              venueRepository.observeVenue(venueId),
              questRepository.observeActiveQuestsByVenue(venueId),
              reservationRepository
                  .observeForExplorer(explorerUid)
                  .flatMapLatest { reservations ->
                    // Only the parties acceptedPartiesOf counts, so an old reservation elsewhere
                    // can't make accepting unavailable here
                    val ids = reservations.filter { it.acceptsAQuestOf(venueId) }.map { it.id }
                    partyRepository.observeJoinedCounts(ids).map { Booked(reservations, it) }
                  }
                  .map<Booked, Booked?> { it }
                  .catch {
                    Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
                    emit(null)
                  },
              completionRepository
                  .observeForExplorer(explorerUid)
                  .map<List<Completion>, List<Completion>?> { it }
                  .catch {
                    Log.w(TAG, "Listening to the proofs of $explorerUid failed", it)
                    emit(null)
                  },
              ::Heard,
          )
          .transformLatest { (venue, quests, booked, completions) ->
            while (true) {
              val now = clock()
              val valid = venuePageQuests(quests, venue?.featuredQuestId, now)
              emit(Heard(venue, valid, booked, completions))
              val nextExpiry = quests.nextExpiryAfter(now) ?: break
              delay(nextExpiry - now)
            }
          }
          .catch {
            Log.w(TAG, "Listening to venue $venueId failed", it)
            latest = null
            mutableUiState.value = VenuePageUiState(status = VenuePageStatus.FAILED)
          }
          .collect { page ->
            latest = page
            // A refused write is over once Firestore no longer lists anything for its quest
            page.booked?.reservations?.let { listed ->
              refusing.retainAll { quest ->
                listed.any { it.questId == quest && it.venueId == venueId }
              }
            }
            show()
          }
    }
  }

  /**
   * Shows what the listeners last sent. A quest whose reservation is [unconfirmed] or [refusing]
   * doesn't count as accepted: it stays "accepting", or "failed", whatever Firestore lists.
   */
  private fun show() {
    val (venue, quests, booked, completions) = latest ?: return
    val accepted = booked?.let {
      acceptedPartiesOf(it.reservations, it.partySizes, venueId) - unconfirmed - refusing
    }
    val reservationIds = booked?.let {
      acceptedReservationsOf(it.reservations, it.partySizes, venueId) - unconfirmed - refusing
    }
    val approved = booked?.reservations?.approvedIds()
    val refused = booked?.reservations?.let(::refusedWrites).orEmpty()
    mutableUiState.update {
      it.showing(venue, quests, accepted, reservationIds, approved, refused, completions)
    }
  }

  /**
   * The quests whose reservation this page created and the server then refused, e.g. because the
   * quest was deactivated meanwhile: Firestore first lists the write as pending, then drops it. A
   * reservation that was never listed yet is not refused, and one that the venue answered, or the
   * explorer cancelled, is not either. Each refusal is reported once.
   */
  private fun refusedWrites(reservations: List<Reservation>): Set<String> {
    val byId = reservations.associateBy { it.id }
    val refused = mutableSetOf<String>()
    val writes = written.iterator()
    while (writes.hasNext()) {
      val (id, write) = writes.next()
      val current = byId[id]
      when {
        current == null && write.shown -> {
          refused += write.questId
          writes.remove()
        }
        current == null -> Unit
        current.status == ReservationStatus.PENDING -> write.shown = true
        else -> writes.remove()
      }
    }
    return refused
  }

  companion object {
    private const val TAG = "VenuePageViewModel"

    /**
     * Builds the [VenuePageViewModel] of the venue [venueId] for the explorer [explorerUid], with
     * the app's repositories.
     */
    fun factory(venueId: String, explorerUid: String): ViewModelProvider.Factory =
        viewModelFactory {
          initializer {
            VenuePageViewModel(
                venueId,
                VenueRepositoryProvider.repository,
                QuestRepositoryProvider.repository,
                ReservationRepositoryProvider.repository,
                UserRepositoryProvider.repository,
                PartyRepositoryProvider.repository,
                CompletionRepositoryProvider.repository,
                explorerUid,
            )
          }
        }
  }
}

/**
 * The rows for the quests among [quests] that are valid at [now]: the quest [featuredQuestId] first
 * when it is one of them, then the others newest first, ties going to the smallest id.
 */
internal fun venuePageQuests(
    quests: List<Quest>,
    featuredQuestId: String?,
    now: Long,
): List<VenuePageQuest> =
    quests
        .filter { it.isValidAt(now) }
        .sortedWith(compareBy<Quest> { it.id != featuredQuestId }.then(NEWEST_FIRST))
        .map { it.toVenuePageQuest() }

/**
 * The size of the party the explorer has for each quest of the venue [venueId] they accepted, keyed
 * by quest id: the number of joined members in [partySizes], keyed by reservation id. Only
 * [ReservationStatus.isActive] reservations count; of several for one quest, the biggest party
 * counts. A party missing from [partySizes] counts as just the explorer, who is in every party they
 * see here.
 */
internal fun acceptedPartiesOf(
    reservations: List<Reservation>,
    partySizes: Map<String, Int>,
    venueId: String,
): Map<String, Int> =
    reservations
        .filter { it.acceptsAQuestOf(venueId) }
        .groupBy { it.questId }
        .mapValues { (_, same) -> same.maxOf { partySizes[it.id] ?: 1 } }

/**
 * The explorer's reservation for each quest of the venue [venueId] they accepted, keyed by quest
 * id: the one [acceptedPartiesOf] counts, so of several for one quest, the one with the biggest
 * party.
 */
internal fun acceptedReservationsOf(
    reservations: List<Reservation>,
    partySizes: Map<String, Int>,
    venueId: String,
): Map<String, String> =
    reservations
        .filter { it.acceptsAQuestOf(venueId) }
        .groupBy { it.questId }
        .mapValues { (_, same) -> same.maxBy { partySizes[it.id] ?: 1 }.id }

/** The ids of the reservations among these that the venue approved. */
private fun List<Reservation>.approvedIds(): Set<String> = filter {
  it.status == ReservationStatus.APPROVED
}
    .mapTo(HashSet()) { it.id }

/** This reservation is an active one at the venue [venueId], so its quest is accepted. */
private fun Reservation.acceptsAQuestOf(venueId: String): Boolean =
    this.venueId == venueId && status.isActive

/**
 * This page showing [venue], its valid [quests] and the parties and [reservationIds] of the
 * [accepted] ones, with the explorer's [approved] reservations and the proofs in [completions], or
 * "not found" when [venue] is null. A quest being accepted that is now accepted is done accepting,
 * a reservation no longer listed is done cancelling, and a quest in [refused] failed. A null
 * [accepted] means the reservations can't be read: the page keeps what it knew and stops offering
 * to accept, cancel or complete. Null [completions] likewise mean the proofs can't be read, so it
 * stops offering to complete.
 */
private fun VenuePageUiState.showing(
    venue: Venue?,
    quests: List<VenuePageQuest>,
    accepted: Map<String, Int>?,
    reservationIds: Map<String, String>?,
    approved: Set<String>?,
    refused: Set<String>,
    completions: List<Completion>?,
): VenuePageUiState =
    if (venue == null) {
      VenuePageUiState(status = VenuePageStatus.NOT_FOUND)
    } else {
      copy(
              status = VenuePageStatus.LOADED,
              venueName = venue.name,
              avatar = INITIALS_AVATAR(venue, venue.name),
              address = venue.address,
              venueLocation = venue.location,
              acceptedParties = accepted ?: acceptedParties,
              reservationIds = reservationIds ?: this.reservationIds,
              acceptingQuestIds =
                  if (accepted == null) emptySet() else acceptingQuestIds - accepted.keys,
              failedQuestIds = failedQuestIds - (accepted?.keys ?: emptySet()) + refused,
              cancellingIds = cancellingIds.intersect(reservationIds?.values.orEmpty()),
              failedCancelIds = failedCancelIds.intersect(reservationIds?.values.orEmpty()),
              acceptAvailable = accepted != null,
              approvedIds = approved ?: approvedIds,
              completions = completions?.associateBy { it.reservationId } ?: this.completions,
              proofsAvailable = completions != null,
          )
          .withQuests(quests)
    }

private fun Quest.toVenuePageQuest() =
    VenuePageQuest(
        questId = id,
        title = title,
        description = description,
        requirements = requirements,
        proofType = proofType,
        minPartySize = minPartySize,
        reward = reward,
        exactPartySize = exactPartySize,
        radiusMeters = radiusMeters,
    )
