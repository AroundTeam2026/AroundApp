// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.isJoined
import com.github.aroundteam2026.aroundapp.model.party.meetsPartySize
import com.github.aroundteam2026.aroundapp.model.party.observeParties
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the dashboard of the signed-in venue: its name, the proofs to review, its pending and
 * approved reservations with their quest titles and parties, and how many of its quests are active.
 *
 * Each party lists its joined members with their profiles' names and pictures. A picture is
 * downloaded once, the first time a member has it, and decoded with [decode]; until then, or if
 * that fails, the member shows initials.
 *
 * It keeps up with changes while the dashboard is watched; if listening fails, it shows
 * [VenueDashboardStatus.FAILED] until [retry]. [approve] and [decline] answer a pending reservation
 * through an [AnswerSaver]; a failure sets [VenueDashboardUiState.updateFailed] until
 * [clearUpdateError]. A pending reservation whose party doesn't have its quest's size has an
 * [ApproveBlock], and [approve] ignores it. Proofs are reviewed on their own screen.
 */
class VenueDashboardViewModel(
    authRepository: AuthRepository,
    private val reservationRepository: ReservationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
    private val partyRepository: PartyRepository,
    private val completionRepository: CompletionRepository,
    private val userRepository: UserRepository,
    private val pictureRepository: UserPictureRepository,
    private val decode: suspend (ByteArray) -> ImageBitmap? = { decodeAvatar(it) },
) : ViewModel() {
  /** Bumped by [retry]: each new value restarts the listeners, even for the same venue. */
  private val retries = MutableStateFlow(0)

  /** Saves the answers to reservations. */
  private val answers = AnswerSaver(viewModelScope, TAG)

  /** The downloaded profile pictures, keyed by their location. */
  private val pictures = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

  /** The picture locations already downloaded or being downloaded, so each is fetched once. */
  private val requestedPictures = HashSet<String>()

  /** What the dashboard shows now; the screen observes it. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<VenueDashboardUiState> =
      combine(
              combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
                  .flatMapLatest { venueId ->
                    if (venueId == null) flowOf(VenueDashboardUiState()) else listen(venueId)
                  },
              answers.state,
          ) { listened, answers ->
            val pending = listened.needsAnswer.mapTo(HashSet()) { it.reservationId }
            listened.copy(
                savingIds = answers.savingAmong(pending),
                updateFailed = answers.failed,
            )
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              VenueDashboardUiState(),
          )

  /**
   * Approves the pending reservation [reservationId]; see [answer]. Does nothing while the
   * dashboard shows it with an [ApproveBlock], since the rules would refuse it.
   */
  fun approve(reservationId: String) {
    val shown = uiState.value.needsAnswer.find { it.reservationId == reservationId }
    if (shown?.approveBlock != null) return
    answer(reservationId, ReservationStatus.APPROVED)
  }

  /** Declines the pending reservation [reservationId]; see [answer]. */
  fun decline(reservationId: String) = answer(reservationId, ReservationStatus.REJECTED)

  /** Clears [VenueDashboardUiState.updateFailed], once the screen has shown it. */
  fun clearUpdateError() = answers.clearFailure()

  /** Listens again, after listening failed. */
  fun retry() {
    retries.update { it + 1 }
  }

  /**
   * Moves the reservation [reservationId] to [status], saved by [answers]: a double tap saves once,
   * it stays saving until the listener shows it is no longer pending, and a failure sets
   * [VenueDashboardUiState.updateFailed].
   */
  private fun answer(reservationId: String, status: ReservationStatus) =
      answers.answer(reservationId, "reservation $reservationId with $status") {
        reservationRepository.updateStatus(reservationId, status)
      }

  /**
   * The dashboard of the venue [venueId], from the venue, its reservations, their parties and the
   * members' profiles, its proofs and its quests. A failing listener gives a failed dashboard, but
   * a profile that can't be read only leaves its member unnamed.
   *
   * The parties are read again only when the reservations to read change, not on every new or
   * reviewed proof or every answer that keeps them, and the profiles only when the members change.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen(venueId: String): Flow<VenueDashboardUiState> = channelFlow {
    // One listener each, shared by the dashboard and the parties to read
    val lists =
        combine(
                reservationRepository.observeForVenue(venueId),
                completionRepository.observeForVenue(venueId),
                ::Pair,
            )
            .shareIn(this, SharingStarted.Eagerly, replay = 1)
    val parties =
        lists
            .map { (reservations, completions) -> partyIdsOf(reservations, completions) }
            .distinctUntilChanged()
            .flatMapLatest { partyRepository.observeParties(it) }
            .map { parties -> parties.mapValues { (_, members) -> members.filter { it.isJoined } } }
            .shareIn(this, SharingStarted.Eagerly, replay = 1)
    val listsWithParties =
        combine(lists, parties) { (reservations, completions), joined ->
              // Right after the ids change the parties are still the old ones: wait for
              // the new ones rather than show an empty party
              if (joined.keys.containsAll(partyIdsOf(reservations, completions))) {
                Triple(reservations, completions, joined)
              } else {
                null
              }
            }
            .filterNotNull()
    val profiles =
        parties
            .map { joined -> joined.values.flatten().mapTo(HashSet()) { it.uid } }
            .distinctUntilChanged()
            .flatMapLatest { userRepository.observeProfiles(it) }
            .onEach { profiles -> loadPictures(profiles.values.mapNotNull { it?.pictureUrl }) }
    combine(
            listsWithParties,
            questRepository.observeQuestsByVenue(venueId),
            venueRepository.observeVenue(venueId),
            profiles,
            pictures,
        ) { (reservations, completions, joined), quests, venue, profiles, pictures ->
          dashboardOf(
              reservations,
              joined,
              quests,
              venue,
              completions,
              profiles = profiles,
              pictures = pictures,
          )
        }
        .collect { send(it) }
  }
      // The repositories' flows throw if the backend stops a listener
      .catch {
        Log.w(TAG, "Listening to the dashboard of $venueId failed", it)
        emit(VenueDashboardUiState(status = VenueDashboardStatus.FAILED))
      }

  /**
   * Downloads each picture of [locations] not asked for yet, and adds it to [pictures] once
   * decoded. A failure is only logged: the member keeps their initials.
   */
  private fun loadPictures(locations: List<String>) {
    for (location in locations) {
      if (!requestedPictures.add(location)) continue
      viewModelScope.launch {
        pictureRepository
            .download(location)
            .mapCatching { checkNotNull(decode(it)) { "Not an image" } }
            .onSuccess { picture -> pictures.update { it + (location to picture) } }
            .onFailure { Log.w(TAG, "Showing the profile picture $location failed", it) }
      }
    }
  }

  companion object {
    private const val TAG = "VenueDashboardViewModel"

    /** How long the listeners outlive the tab, so a quick rotation doesn't restart them. */
    private const val STOP_TIMEOUT_MS = 5_000L

    /** Builds the [VenueDashboardViewModel] of the signed-in venue with the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenueDashboardViewModel(
            AuthRepositoryProvider.repository,
            ReservationRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
            PartyRepositoryProvider.repository,
            CompletionRepositoryProvider.repository,
            UserRepositoryProvider.repository,
            UserPictureRepositoryProvider.repository(this[APPLICATION_KEY]!!),
        )
      }
    }
  }
}

/**
 * [bytes] as a picture at most [maxEdge] pixels on each side, decoded on [dispatcher], or null if
 * they aren't an image. An avatar is small, so it never keeps a full-size photo in memory.
 */
internal suspend fun decodeAvatar(
    bytes: ByteArray,
    maxEdge: Int = AVATAR_EDGE_PX,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): ImageBitmap? =
    withContext(dispatcher) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

/** The longest side an avatar is decoded at: its 32 dp even on the densest screens. */
private const val AVATAR_EDGE_PX = 128

/**
 * The reservations whose parties the dashboard shows: the pending and approved ones, and those of
 * the pending proofs, whose reservation may have been cancelled since.
 */
private fun partyIdsOf(reservations: List<Reservation>, completions: List<Completion>) =
    reservations.filter { it.status.isActive }.mapTo(HashSet()) { it.id } +
        completions.filter { it.status == CompletionStatus.PENDING }.map { it.reservationId }

/**
 * The loaded dashboard of [venue], built from [reservations], the joined members of their [parties]
 * keyed by reservation id, [quests], the pending [completions], and the members' [profiles] and
 * downloaded [pictures], keyed by uid and by location.
 *
 * Pending reservations need an answer and approved ones are upcoming, each oldest request first,
 * ties going to the smallest id; rejected and cancelled ones are left out. The proofs to review
 * follow the same order. A missing party counts as empty, and an unreadable [venue] leaves the
 * header empty. Each pending reservation gets its [ApproveBlock] from its quest and party.
 * Reservations have no visit time yet, so nothing is sorted or filtered by slot.
 */
internal fun dashboardOf(
    reservations: List<Reservation>,
    parties: Map<String, List<PartyMember>>,
    quests: List<Quest>,
    venue: Venue?,
    completions: List<Completion>,
    profiles: Map<String, User?> = emptyMap(),
    pictures: Map<String, ImageBitmap> = emptyMap(),
): VenueDashboardUiState {
  val titles = quests.associate { it.id to it.title }
  val questsById = quests.associateBy { it.id }
  val reservationsById = reservations.associateBy { it.id }
  fun withStatus(status: ReservationStatus) =
      reservations
          .filter { it.status == status }
          .sortedWith(compareBy<Reservation> { it.createdAt }.thenBy { it.id })
          .map {
            val quest = questsById[it.questId]
            val members =
                leaderFirst(it, parties[it.id].orEmpty()).map { member ->
                  val profile = profiles[member.uid]
                  DashboardMember(profile?.displayName, profile?.pictureUrl?.let(pictures::get))
                }
            val block =
                if (status == ReservationStatus.PENDING) {
                  approveBlockOf(quest, members.size)
                } else {
                  null
                }
            it.toDashboardReservation(titles, members, block, quest)
          }
  return VenueDashboardUiState(
      status = VenueDashboardStatus.LOADED,
      venueName = venue?.name.orEmpty(),
      avatar = venue?.let { INITIALS_AVATAR(it, it.name) },
      needsAnswer = withStatus(ReservationStatus.PENDING),
      upcoming = withStatus(ReservationStatus.APPROVED),
      activeQuestsCount = quests.count { it.status == QuestStatus.ACTIVE },
      proofsToReview =
          completions
              .filter { it.status == CompletionStatus.PENDING }
              .sortedWith(compareBy<Completion> { it.submittedAt }.thenBy { it.id })
              .map {
                it.toDashboardProof(
                    reservationsById[it.reservationId],
                    titles,
                    parties[it.reservationId]?.size ?: 0,
                )
              },
  )
}

/**
 * The joined [members] of [reservation]'s party, the explorer who made it first, then in the order
 * they joined. The rules mark the creator by a membership joined at the reservation's creation
 * time, which no later join may have.
 */
private fun leaderFirst(reservation: Reservation, members: List<PartyMember>) =
    members.sortedWith(
        compareBy<PartyMember> { it.joinedAt != reservation.createdAt }
            .thenBy { it.joinedAt }
            .thenBy { it.uid }
    )

/**
 * Why a pending party of [joined] explorers can't be approved for [quest], or null if it has the
 * size [quest] asks for, as [meetsPartySize] tells. A null [quest] was deleted.
 */
internal fun approveBlockOf(quest: Quest?, joined: Int): ApproveBlock? =
    when {
      quest == null -> ApproveBlock.QuestMissing
      quest.meetsPartySize(joined) -> null
      joined < quest.minPartySize -> ApproveBlock.TooFew(quest.minPartySize - joined)
      else -> ApproveBlock.TooMany(quest.minPartySize)
    }

/**
 * The row of this reservation, whose party has [members], leader first: the leader's name is their
 * profile's, or else the one stored with the reservation.
 */
private fun Reservation.toDashboardReservation(
    titles: Map<String, String>,
    members: List<DashboardMember>,
    approveBlock: ApproveBlock?,
    quest: Quest?,
) =
    DashboardReservation(
        reservationId = id,
        questTitle = titles[questId],
        explorerName = members.firstOrNull()?.name ?: explorerName.ifBlank { null },
        partySize = members.size,
        requestedAt = createdAt,
        approved = status == ReservationStatus.APPROVED,
        approveBlock = approveBlock,
        members = members,
        groupSize = quest?.minPartySize?.takeIf { it > 1 },
    )

private fun Completion.toDashboardProof(
    reservation: Reservation?,
    titles: Map<String, String>,
    partySize: Int,
) =
    DashboardProof(
        completionId = id,
        explorerName = reservation?.explorerName?.ifBlank { null },
        questTitle = reservation?.let { titles[it.questId] },
        partySize = partySize,
        proofType = proof.type,
        sentAt = submittedAt,
    )
