// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the page of the venue [venueId]: its header and the quests explorers can take there.
 *
 * It listens to the venue, its active quests, the only ones the repository sends, and the
 * reservations of the explorer [explorerUid], and keeps up as they change and as rewards expire, by
 * [clock]. If listening to the venue or its quests fails, the page shows [VenuePageStatus.FAILED]
 * until [onRetry]. If listening to the reservations fails, the page stays and accepting is
 * unavailable ([VenuePageUiState.acceptAvailable]) until the page is opened again. Accepting names
 * the explorer by the display name in their profile, read from [userRepository].
 */
class VenuePageViewModel(
    private val venueId: String,
    private val venueRepository: VenueRepository,
    private val questRepository: QuestRepository,
    private val reservationRepository: ReservationRepository,
    private val userRepository: UserRepository,
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
  private var latest: Triple<Venue?, List<VenuePageQuest>, List<Reservation>?>? = null

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
    mutableUiState.update { it.copy(detailQuestId = null) }
  }

  /**
   * Called when the explorer accepts the picked quest: books it as a pending reservation of a party
   * of just themself, named as in their profile, for a slot starting now. Does nothing when no
   * quest is picked, when accepting is unavailable, or when that quest is already accepted or being
   * accepted, so a double tap books once; other quests can still be accepted meanwhile. The quest
   * shows as accepted only once the backend has answered, never while the write waits for it. A
   * failure keeps the quest picked and shows [AcceptStatus.FAILED], and so does the server refusing
   * the write later, after the quest showed as accepted, or the explorer's profile not being
   * readable; calling this again retries.
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
                        newPendingReservation(id, venueId, explorerUid, name, clock())
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
   * Listens to the venue, its quests and the explorer's reservations until the ViewModel is cleared
   * or listening fails. The quests are checked again each time a reward expires, even when nothing
   * else changes. A failure of the reservations only is not the page's: it goes on without them,
   * with accepting unavailable.
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
                  .map<List<Reservation>, List<Reservation>?> { it }
                  .catch {
                    Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
                    emit(null)
                  },
          ) { venue, quests, reservations ->
            Triple(venue, quests, reservations)
          }
          .transformLatest { (venue, quests, reservations) ->
            while (true) {
              val now = clock()
              emit(
                  Triple(venue, venuePageQuests(quests, venue?.featuredQuestId, now), reservations)
              )
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
            page.third?.let { listed ->
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
    val (venue, quests, reservations) = latest ?: return
    val accepted = reservations?.let { acceptedPartiesOf(it, venueId) - unconfirmed - refusing }
    val refused = reservations?.let(::refusedWrites).orEmpty()
    mutableUiState.update { it.showing(venue, quests, accepted, refused) }
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
 * by quest id. Only [ReservationStatus.isActive] reservations count; of several for one quest, the
 * biggest party counts.
 */
internal fun acceptedPartiesOf(reservations: List<Reservation>, venueId: String): Map<String, Int> =
    reservations
        .filter { it.venueId == venueId && it.status.isActive }
        .groupBy { it.questId }
        .mapValues { (_, same) -> same.maxOf { it.explorerUids.size } }

/**
 * This page showing [venue], its valid [quests] and the parties of the [accepted] ones, or "not
 * found" when [venue] is null. A quest being accepted that is now accepted is done accepting, and a
 * quest in [refused] failed. A null [accepted] means the reservations can't be read: the page keeps
 * what it knew and stops offering to accept.
 */
private fun VenuePageUiState.showing(
    venue: Venue?,
    quests: List<VenuePageQuest>,
    accepted: Map<String, Int>?,
    refused: Set<String>,
): VenuePageUiState =
    if (venue == null) {
      VenuePageUiState(status = VenuePageStatus.NOT_FOUND)
    } else {
      copy(
              status = VenuePageStatus.LOADED,
              venueName = venue.name,
              avatar = INITIALS_AVATAR(venue, venue.name),
              address = venue.address,
              acceptedParties = accepted ?: acceptedParties,
              acceptingQuestIds =
                  if (accepted == null) emptySet() else acceptingQuestIds - accepted.keys,
              failedQuestIds = failedQuestIds - (accepted?.keys ?: emptySet()) + refused,
              acceptAvailable = accepted != null,
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
    )
