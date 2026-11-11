// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.meetsPartySize
import com.github.aroundteam2026.aroundapp.model.party.observeJoinedCounts
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
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.map.INITIALS_AVATAR
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Holds the dashboard of the signed-in venue: its name, the proofs to review, its pending and
 * approved reservations with their quest titles, explorer names and party sizes, and how many of
 * its quests are active.
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
) : ViewModel() {
  /** Bumped by [retry]: each new value restarts the listeners, even for the same venue. */
  private val retries = MutableStateFlow(0)

  /** Saves the answers to reservations. */
  private val answers = AnswerSaver(viewModelScope, TAG)

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
   * The dashboard of the venue [venueId], from the venue, its reservations, their parties, its
   * proofs and its quests. A failing listener gives a failed dashboard.
   *
   * The parties are read again only when the reservations to read change, not on every new or
   * reviewed proof or every answer that keeps them.
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
    val partySizes =
        lists
            .map { (reservations, completions) -> partyIdsOf(reservations, completions) }
            .distinctUntilChanged()
            .flatMapLatest { partyRepository.observeJoinedCounts(it) }
    val listsWithParties =
        combine(lists, partySizes) { (reservations, completions), sizes ->
              // Right after the ids change the sizes are still the old ones: wait for
              // the new ones rather than show an empty party
              if (sizes.keys.containsAll(partyIdsOf(reservations, completions))) {
                Triple(reservations, completions, sizes)
              } else {
                null
              }
            }
            .filterNotNull()
    combine(
            listsWithParties,
            questRepository.observeQuestsByVenue(venueId),
            venueRepository.observeVenue(venueId),
        ) { (reservations, completions, sizes), quests, venue ->
          dashboardOf(reservations, sizes, quests, venue, completions)
        }
        .collect { send(it) }
  }
      // The repositories' flows throw if the backend stops a listener
      .catch {
        Log.w(TAG, "Listening to the dashboard of $venueId failed", it)
        emit(VenueDashboardUiState(status = VenueDashboardStatus.FAILED))
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
        )
      }
    }
  }
}

/**
 * The reservations whose parties the dashboard shows: the pending and approved ones, and those of
 * the pending proofs, whose reservation may have been cancelled since.
 */
private fun partyIdsOf(reservations: List<Reservation>, completions: List<Completion>) =
    reservations.filter { it.status.isActive }.mapTo(HashSet()) { it.id } +
        completions.filter { it.status == CompletionStatus.PENDING }.map { it.reservationId }

/**
 * The loaded dashboard of [venue], built from [reservations], their joined [partySizes] keyed by
 * reservation id, [quests] and the pending [completions].
 *
 * Pending reservations need an answer and approved ones are upcoming, each oldest request first,
 * ties going to the smallest id; rejected and cancelled ones are left out. The proofs to review
 * follow the same order. A missing party counts as empty, and an unreadable [venue] leaves the
 * header empty. Each pending reservation gets its [ApproveBlock] from its quest and party.
 * Reservations have no visit time yet, so nothing is sorted or filtered by slot.
 */
internal fun dashboardOf(
    reservations: List<Reservation>,
    partySizes: Map<String, Int>,
    quests: List<Quest>,
    venue: Venue?,
    completions: List<Completion>,
): VenueDashboardUiState {
  val questsById = quests.associateBy { it.id }
  val reservationsById = reservations.associateBy { it.id }
  fun withStatus(status: ReservationStatus) =
      reservations
          .filter { it.status == status }
          .sortedWith(compareBy<Reservation> { it.createdAt }.thenBy { it.id })
          .map { it.toDashboardReservation(questsById, partySizes.getOrDefault(it.id, 0)) }
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
                    questsById,
                    partySizes[it.reservationId] ?: 0,
                )
              },
  )
}

/**
 * Why a pending party of [joined] explorers can't be approved for [quest], or null if it has the
 * size [quest] asks for, as [meetsPartySize] tells. A null [quest] was deleted.
 */
internal fun approveBlockOf(quest: Quest?, joined: Int): ApproveBlock? =
    when {
      quest == null -> ApproveBlock.QuestMissing
      quest.meetsPartySize(joined) -> null
      joined < quest.minPartySize -> ApproveBlock.TooFew(quest.minPartySize - joined)
      else -> ApproveBlock.TooMany(joined, quest.minPartySize)
    }

/** Only a pending reservation gets an [ApproveBlock]: an approved one has nothing to approve. */
private fun Reservation.toDashboardReservation(questsById: Map<String, Quest>, partySize: Int) =
    DashboardReservation(
        reservationId = id,
        questTitle = questsById[questId]?.title,
        explorerName = explorerName.ifBlank { null },
        partySize = partySize,
        requestedAt = createdAt,
        approved = status == ReservationStatus.APPROVED,
        approveBlock =
            approveBlockOf(questsById[questId], partySize).takeIf {
              status == ReservationStatus.PENDING
            },
    )

private fun Completion.toDashboardProof(
    reservation: Reservation?,
    questsById: Map<String, Quest>,
    partySize: Int,
) =
    DashboardProof(
        completionId = id,
        explorerName = reservation?.explorerName?.ifBlank { null },
        questTitle = reservation?.let { questsById[it.questId]?.title },
        partySize = partySize,
        proofType = proof.type,
        sentAt = submittedAt,
    )
