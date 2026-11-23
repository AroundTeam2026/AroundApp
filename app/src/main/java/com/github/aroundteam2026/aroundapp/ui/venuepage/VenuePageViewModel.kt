// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.createdParty
import com.github.aroundteam2026.aroundapp.model.party.isJoined
import com.github.aroundteam2026.aroundapp.model.party.leavesPartiesOf
import com.github.aroundteam2026.aroundapp.model.party.observeParties
import com.github.aroundteam2026.aroundapp.model.party.partyCapacity
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWrites
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWritesProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.bookQuest
import com.github.aroundteam2026.aroundapp.model.reservation.isActive
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.user.observeProfiles
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.map.INITIALS_AVATAR
import com.github.aroundteam2026.aroundapp.ui.map.NEWEST_FIRST
import com.github.aroundteam2026.aroundapp.ui.observeOffline
import com.github.aroundteam2026.aroundapp.ui.observeOnlineNow
import com.github.aroundteam2026.aroundapp.ui.proof.ProofSender
import com.github.aroundteam2026.aroundapp.ui.proof.ProofSenders
import com.github.aroundteam2026.aroundapp.ui.quest.CancelStatus
import com.github.aroundteam2026.aroundapp.ui.quest.ProfilePictures
import com.github.aroundteam2026.aroundapp.ui.quest.decodeAvatar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the page of the venue [venueId]: its header and the quests explorers can take there.
 *
 * It keeps up with the venue, its active quests, the reservations of [explorerUid] with their
 * parties and the parties' proofs, and with rewards expiring by [clock]. If the venue or its quests
 * fail to load, the page is [VenuePageStatus.FAILED] until [onRetry]; if the reservations fail,
 * accepting is unavailable ([VenuePageUiState.acceptAvailable]) until the page reopens, and if the
 * proofs fail, so is sending one ([VenuePageUiState.proofsAvailable]). Accepting names the explorer
 * as in their profile. Given [arrivalQuestId], the page opens that quest's details the first time
 * it shows the venue; see [VenuePageUiState.arrivingAt]. Given [focusQuestId], it scrolls to and
 * picks that quest instead; see [VenuePageUiState.focusingOn].
 *
 * It also shows who is in each party, downloading their pictures through [pictureRepository];
 * without one, members show their initials. While [VenuePageUiState.offline] says the device is
 * offline, which it follows from [connectivityRepository], the explorer can't [cancel].
 */
class VenuePageViewModel(
    private val venueId: String,
    private val venueRepository: VenueRepository,
    private val questRepository: QuestRepository,
    private val reservationRepository: ReservationRepository,
    private val userRepository: UserRepository,
    private val partyRepository: PartyRepository,
    private val completionRepository: CompletionRepository,
    connectivityRepository: ConnectivityRepository,
    private val explorerUid: String,
    private val clock: () -> Long = System::currentTimeMillis,
    arrivalQuestId: String? = null,
    focusQuestId: String? = null,
    pictureRepository: UserPictureRepository? = null,
    private val refusedWrites: RefusedWrites,
    decodePicture: suspend (ByteArray) -> ImageBitmap? = { decodeAvatar(it) },
) : ViewModel() {
  private val hearing = refusedWrites.hearing()
  private val mutableUiState =
      MutableStateFlow(VenuePageUiState(failedQuestIds = refusedWrites.refusedQuests))
  /** What the page shows now; the page observes it. */
  val uiState: StateFlow<VenuePageUiState> = mutableUiState.asStateFlow()

  private var listening: Job? = null

  /** Who sent the proofs; the page doesn't wait for their profiles to show the proofs. */
  private val proofSenders = ProofSenders(userRepository, explorerUid)

  /**
   * The quest whose details the page opens the first time it shows the venue; null afterwards. Kept
   * out of the state, which only learns of it once it has been opened, as
   * [VenuePageUiState.arrivalQuestId], or missed, as [VenuePageUiState.arrivalMissed].
   */
  private var arriving: String? = arrivalQuestId

  /** The quest the page scrolls to and picks the first time it shows the venue; null afterwards. */
  private var focusing: String? = focusQuestId

  /**
   * The explorer's [reservations], the memberships of the parties of the pending and approved ones
   * at this venue by reservation id, and the [profiles] of their joined members by uid, null until
   * they are read.
   */
  private data class Booked(
      val reservations: List<Reservation>,
      val parties: Map<String, List<PartyMember>>,
      val profiles: Map<String, User?>?,
  ) {
    /** The number of joined members of each party of [parties], by reservation id. */
    val partySizes: Map<String, Int> = parties.mapValues { (_, party) ->
      party.count { it.isJoined }
    }
  }

  /**
   * The quests whose reservation this page is creating and the backend hasn't answered yet.
   * Firestore lists such a write at once, before the server accepts or refuses it, so it doesn't
   * count as accepted until [ReservationRepository.createReservation] returns: the page shows
   * "accepting" instead of "accepted" followed by a failure.
   */
  private val unconfirmed = mutableSetOf<String>()

  /**
   * The quests whose reservation the backend just refused. Firestore may list the refused write, as
   * pending, before or after the refusal arrives, so the quest doesn't count as accepted until no
   * pending reservation is listed for it.
   */
  private val refusing = mutableSetOf<String>()

  /** What the page last heard from its listeners, so it can be shown again; null when failed. */
  private var latest: Heard<VenuePageQuest>? = null

  /** The members' profile pictures, each downloaded once and tried again after a failure. */
  private val pictures = ProfilePictures(viewModelScope, pictureRepository, decodePicture)

  /** What the listeners sent; [booked] or [proofs] is null once its listener failed. */
  private data class Heard<Q>(
      val venue: Venue?,
      val quests: List<Q>,
      val booked: Booked?,
      val proofs: Proofs?,
  )

  /** Whether the device is online now: the page says it isn't a moment later. */
  private val online = connectivityRepository.observeOnlineNow(viewModelScope, TAG)

  init {
    viewModelScope.launch {
      connectivityRepository.observeOffline(TAG).collect { offline ->
        mutableUiState.update { it.copy(offline = offline) }
      }
    }
    listen()
    // The page shows itself again as each picture arrives
    viewModelScope.launch { pictures.pictures.collect { show() } }
  }

  /** Called when the explorer taps the quest [questId]; see [VenuePageUiState.withQuestTapped]. */
  fun onQuestSelected(questId: String) {
    mutableUiState.update { it.withQuestTapped(questId) }
  }

  /** Called when the explorer opens the details of the quest [questId]. */
  fun onQuestDetailsOpened(questId: String) {
    mutableUiState.update { it.withQuestDetails(questId) }
  }

  /** Called once the page has scrolled to [VenuePageUiState.focusQuestId]. */
  fun onFocusShown() {
    mutableUiState.update { it.copy(focusQuestId = null) }
  }

  /** Called once the page has said that the quest it was to arrive at is no longer listed. */
  fun onArrivalMissedShown() {
    mutableUiState.update { it.copy(arrivalMissed = false) }
  }

  /** Called when the explorer closes the quest details, however they close them. */
  fun onQuestDetailsClosed() {
    mutableUiState.update { it.withoutQuestDetails() }
  }

  /**
   * Books the picked quest as a pending reservation, with the explorer as its party's first member,
   * for a slot starting now.
   *
   * Does nothing when no quest is picked, accepting is unavailable, the device is offline, or that
   * quest is already accepted or being accepted, so a double tap books once; other quests can still
   * be accepted meanwhile. The quest shows as accepted only once the backend answered. A failure, a
   * later server refusal or an unreadable profile keeps it picked and shows [AcceptStatus.FAILED];
   * calling this again retries. A quest accepted from its details stays picked once they close.
   */
  fun accept() {
    val current = mutableUiState.value
    val quest = current.selectedQuest ?: return
    val id = quest.questId
    if (
        !current.acceptAvailable ||
            !online.value ||
            id in current.acceptingQuestIds ||
            id in current.acceptedParties
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
          reservationRepository.bookQuest(userRepository, explorerUid, id, venueId, clock())
      unconfirmed -= id
      result
          .onSuccess { reservationId ->
            // The page stays "accepting" until the reservation shows up in the listener
            refusedWrites.record(reservationId, id)
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
   * Called when the explorer cancels their reservation for the picked quest, once they confirmed,
   * or leaves its party when someone else made it ([VenuePageUiState.leavesParty]). Does nothing
   * without such a reservation, or when which of the two it is isn't known, or while it is being
   * cancelled, so a double tap cancels once, or while the device is offline, since cancelling needs
   * a connection. It shows as cancelling until the reservations no longer list it. A failure, such
   * as being offline, shows [CancelStatus.FAILED]; calling this again retries.
   */
  fun cancel() {
    val current = mutableUiState.value
    val reservationId = current.cancellableReservationId ?: return
    // Without the explorer's membership, cancelling for everyone might not be theirs to do
    val leaving = current.leavesParty ?: return
    if (!online.value || reservationId in current.cancellingIds) return
    mutableUiState.update {
      it.copy(
          cancellingIds = it.cancellingIds + reservationId,
          failedCancelIds = it.failedCancelIds - reservationId,
      )
    }
    viewModelScope.launch {
      // Only its creator cancels the reservation; anyone else who joined withdraws instead
      val result =
          if (leaving) partyRepository.withdraw(reservationId, explorerUid)
          else reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELLED)
      result.onFailure {
        val what = if (leaving) "Leaving the party of" else "Cancelling"
        Log.w(TAG, "$what reservation $reservationId failed", it)
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

  /** Called when the explorer asks to try again after a failure; does nothing otherwise. */
  fun onRetry() {
    if (mutableUiState.value.status != VenuePageStatus.FAILED) return
    // Whether the device is offline doesn't depend on the venue
    mutableUiState.update {
      VenuePageUiState(offline = it.offline, failedQuestIds = refusedWrites.refusedQuests)
    }
    refusing.clear()
    latest = null
    listen()
  }

  /**
   * Listens to the venue, its quests, the explorer's reservations and their parties, and the
   * parties' proofs, whichever member sent them, until the ViewModel is cleared or listening fails.
   * The quests are checked again each time a reward expires, even when nothing else changes. A
   * failure of the reservations or parties only is not the page's: it goes on without them, with
   * accepting unavailable. Nor is a failure of the proofs, with sending one unavailable.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen() {
    listening?.cancel()
    listening = viewModelScope.launch {
      // One listener of the reservations for both uses below. Its failure is shared as a value,
      // since a failing shareIn would fail this scope, and each use handles it on its own
      val reservations =
          reservationRepository
              .observeForExplorer(explorerUid)
              .map { Result.success(it) }
              .catch { emit(Result.failure(it)) }
              .shareIn(this, SharingStarted.Lazily, replay = 1)
      combine(
              venueRepository.observeVenue(venueId),
              questRepository.observeActiveQuestsByVenue(venueId),
              reservations
                  .map { it.getOrThrow() }
                  .flatMapLatest { reservations ->
                    // Only the parties acceptedPartiesOf counts, so an old reservation elsewhere
                    // can't make accepting unavailable here
                    val ids = reservations.filter { it.acceptsAQuestOf(venueId) }.map { it.id }
                    partyRepository.observeParties(ids).flatMapLatest { parties ->
                      val uids = parties.values.flatten().filter { it.isJoined }.map { it.uid }
                      userRepository
                          .observeProfiles(uids)
                          .onEach { profiles ->
                            pictures.load(profiles.values.mapNotNull { it?.pictureUrl })
                          }
                          .map<Map<String, User?>, Map<String, User?>?> { it }
                          // The reservations show at once; the seats fill in once the names arrive
                          .onStart { emit(null) }
                          .map { Booked(reservations, parties, it) }
                    }
                  }
                  .map<Booked, Booked?> { it }
                  .catch {
                    Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
                    emit(null)
                  },
              reservations
                  .map { it.getOrThrow() }
                  .flatMapLatest { reservations ->
                    // The party's proof, whichever member sent it, of each reservation here. Only
                    // an approved party has one, and the rules may refuse listening to a party
                    // just joined until the server has the membership, which an approved one has
                    val ids =
                        reservations
                            .filter {
                              it.venueId == venueId && it.status == ReservationStatus.APPROVED
                            }
                            .map { it.id }
                    completionRepository.observeForReservations(ids)
                  }
                  // A new list of the same proofs mustn't show the page again with older
                  // reservations, which would end a refused accept's "failed" too early
                  .distinctUntilChanged()
                  // A proof another member sent names them, as the Figma frame does
                  .flatMapLatest { completions ->
                    proofSenders.observe(completions).map { Proofs(completions, it) }
                  }
                  .distinctUntilChanged()
                  .map<Proofs, Proofs?> { it }
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
            mutableUiState.update {
              VenuePageUiState(status = VenuePageStatus.FAILED, offline = it.offline)
            }
          }
          .collect { page ->
            latest = page
            // A refused write is over once Firestore no longer lists it as pending; an earlier
            // reservation of the quest, cancelled and still listed, doesn't keep it
            page.booked?.reservations?.let { listed ->
              refusing.retainAll { quest ->
                listed.any {
                  it.questId == quest &&
                      it.venueId == venueId &&
                      it.status == ReservationStatus.PENDING
                }
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
    val (venue, quests, booked, proofs) = latest ?: return
    val view = booked?.let {
      BookedView(
          accepted =
              acceptedPartiesOf(it.reservations, it.partySizes, venueId) - unconfirmed - refusing,
          reservationIds =
              acceptedReservationsOf(it.reservations, it.partySizes, venueId) -
                  unconfirmed -
                  refusing,
          approved = it.reservations.approvedIds(),
          unsent =
              it.reservations
                  .filter { reservation -> reservation.pendingWrite }
                  .map { reservation -> reservation.id }
                  .toSet(),
          codes = partyCodesOf(it.reservations, it.parties, explorerUid, venueId),
          leaves = leavesPartiesOf(it.reservations, it.parties, explorerUid),
          members =
              it.profiles?.let { profiles ->
                partyMembersOf(
                    it.reservations,
                    it.parties,
                    profiles,
                    explorerUid,
                    pictures.pictures.value::get,
                )
              },
      )
    }
    // All the refused quests, not only the newly refused: the map may have heard the refusal first
    booked?.reservations?.let(hearing::refusedIn)
    val refused = refusedWrites.refusedQuests
    mutableUiState.update { it.showing(venue, quests, view, refused, proofs) }
    arriving?.let { questId ->
      arriving = null
      mutableUiState.update { it.arrivingAt(questId) }
    }
    focusing?.let { questId ->
      focusing = null
      mutableUiState.update { it.focusingOn(questId) }
    }
  }

  companion object {
    private const val TAG = "VenuePageViewModel"

    /**
     * Builds the [VenuePageViewModel] of the venue [venueId] for the explorer [explorerUid], with
     * the app's repositories, opening the details of [arrivalQuestId] if given, or scrolled to
     * [focusQuestId] if given.
     */
    fun factory(
        venueId: String,
        explorerUid: String,
        arrivalQuestId: String? = null,
        focusQuestId: String? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenuePageViewModel(
            venueId,
            VenueRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            ReservationRepositoryProvider.repository,
            UserRepositoryProvider.repository,
            PartyRepositoryProvider.repository,
            CompletionRepositoryProvider.repository,
            ConnectivityRepositoryProvider.repository(
                this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            ),
            explorerUid,
            arrivalQuestId = arrivalQuestId,
            focusQuestId = focusQuestId,
            pictureRepository =
                this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]?.let {
                  UserPictureRepositoryProvider.repository(it)
                },
            refusedWrites =
                RefusedWritesProvider.refusedWrites(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!,
                    explorerUid,
                ),
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

/**
 * The party code of each reservation at the venue [venueId] that [explorerUid] created and friends
 * can still join, keyed by reservation id. Friends join only while a reservation is
 * [ReservationStatus.PENDING], and only the creator, whose membership in [parties] says so, is
 * shown the code; a reservation made before party codes has none.
 */
internal fun partyCodesOf(
    reservations: List<Reservation>,
    parties: Map<String, List<PartyMember>>,
    explorerUid: String,
    venueId: String,
): Map<String, String> =
    reservations
        .filter { it.venueId == venueId && it.status == ReservationStatus.PENDING }
        .filter { reservation ->
          parties[reservation.id].orEmpty().any {
            it.uid == explorerUid && it.createdParty(reservation)
          }
        }
        .mapNotNull { reservation -> reservation.partyCode?.let { reservation.id to it } }
        .toMap()

/** The ids of the reservations among these that the venue approved. */
private fun List<Reservation>.approvedIds(): Set<String> = filter {
  it.status == ReservationStatus.APPROVED
}
    .mapTo(HashSet()) { it.id }

/** This reservation is an active one at the venue [venueId], so its quest is accepted. */
private fun Reservation.acceptsAQuestOf(venueId: String): Boolean =
    this.venueId == venueId && status.isActive

/**
 * What the page shows of the explorer's reservations at this venue.
 *
 * @property accepted The party size of each accepted quest, by quest id.
 * @property reservationIds The reservation of each accepted quest, by quest id.
 * @property approved Ids of the reservations the venue approved.
 * @property unsent The reservations the server hasn't confirmed yet.
 * @property codes The party codes the explorer can share, by reservation id.
 * @property leaves Whether the explorer joined the party of each reservation rather than created
 *   it, by reservation id ([leavesPartiesOf]).
 * @property members The joined members of each party, by reservation id; null until their profiles
 *   are read.
 */
private data class BookedView(
    val accepted: Map<String, Int>,
    val reservationIds: Map<String, String>,
    val approved: Set<String>,
    val unsent: Set<String>,
    val codes: Map<String, String>,
    val leaves: Map<String, Boolean>,
    val members: Map<String, List<PartyMemberUi>>?,
)

/**
 * The parties' [completions] at this venue, with the other members who sent some of them, by
 * reservation id.
 */
private data class Proofs(
    val completions: List<Completion>,
    val senders: Map<String, ProofSender>,
)

/**
 * This page showing [venue], its valid [quests] and what [booked] says about the explorer's
 * reservations, with the parties' [proofs], or "not found" when [venue] is null. A quest being
 * accepted that is now accepted is done accepting, a reservation no longer listed is done
 * cancelling, and a quest in [refused] failed. A null [booked] means the reservations can't be
 * read: the page keeps what it knew and stops offering to accept, cancel, complete or share a code.
 * Null [proofs] likewise mean the proofs can't be read, so it stops offering to complete.
 */
private fun VenuePageUiState.showing(
    venue: Venue?,
    quests: List<VenuePageQuest>,
    booked: BookedView?,
    refused: Set<String>,
    proofs: Proofs?,
): VenuePageUiState =
    if (venue == null) {
      VenuePageUiState(status = VenuePageStatus.NOT_FOUND, offline = offline)
    } else {
      copy(
              status = VenuePageStatus.LOADED,
              venueName = venue.name,
              avatar = INITIALS_AVATAR(venue, venue.name),
              address = venue.address,
              venueLocation = venue.location,
              acceptedParties = booked?.accepted ?: acceptedParties,
              reservationIds = booked?.reservationIds ?: this.reservationIds,
              acceptingQuestIds =
                  if (booked == null) emptySet() else acceptingQuestIds - booked.accepted.keys,
              failedQuestIds = failedQuestIds - booked?.accepted?.keys.orEmpty() + refused,
              cancellingIds = cancellingIds.intersect(booked?.reservationIds?.values.orEmpty()),
              failedCancelIds = failedCancelIds.intersect(booked?.reservationIds?.values.orEmpty()),
              acceptAvailable = booked != null,
              approvedIds = booked?.approved ?: approvedIds,
              unsentIds = booked?.unsent ?: unsentIds,
              completions =
                  proofs?.completions?.associateBy { it.reservationId } ?: this.completions,
              proofSenders = proofs?.senders ?: proofSenders,
              proofsAvailable = proofs != null,
              partyCodes = booked?.codes ?: partyCodes,
              partyMembers = booked?.members ?: partyMembers,
              leavesParties = booked?.leaves ?: leavesParties,
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
        location = location,
        partyCapacity = partyCapacity,
    )
