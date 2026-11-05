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
 * Holds the dashboard of the signed-in venue: its name, the proofs it has to review, read from
 * [completionRepository], its reservations, split into those it has to answer and the approved
 * ones, with each reservation's quest title, explorer's name and party size, read from
 * [partyRepository], and how many of its quests are active.
 *
 * It follows the signed-in venue, and keeps up as it, its proofs, its reservations, their parties
 * and its quests change. It only listens while the dashboard is watched. If listening fails, the
 * dashboard shows [VenueDashboardStatus.FAILED] until [retry].
 *
 * [approve] and [decline] answer a pending reservation through an [AnswerSaver]; a failure sets
 * [VenueDashboardUiState.updateFailed] until [clearUpdateError]. Proofs are reviewed on their own
 * screen.
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

  /** Approves the pending reservation [reservationId]; see [answer]. */
  fun approve(reservationId: String) = answer(reservationId, ReservationStatus.APPROVED)

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
 * The loaded dashboard of [venue] for [reservations], with the number of joined members of each
 * party in [partySizes], keyed by reservation id, titled from [quests], which also give the active
 * quests count. A party missing from [partySizes] counts as empty. A [venue] that can't be read
 * leaves the header empty. Pending reservations need an answer and approved ones are upcoming, each
 * oldest request first, ties going to the smallest id. Rejected and cancelled ones are left out.
 * The pending [completions] are the proofs to review, oldest first, ties going to the smallest id,
 * each named, titled and sized from its reservation.
 *
 * Reservations have no visit time yet, so nothing is sorted or filtered by their slot: a pending
 * reservation's slot only dates the request, and an approved one keeps it.
 */
internal fun dashboardOf(
    reservations: List<Reservation>,
    partySizes: Map<String, Int>,
    quests: List<Quest>,
    venue: Venue?,
    completions: List<Completion>,
): VenueDashboardUiState {
  val titles = quests.associate { it.id to it.title }
  val reservationsById = reservations.associateBy { it.id }
  fun withStatus(status: ReservationStatus) =
      reservations
          .filter { it.status == status }
          .sortedWith(compareBy<Reservation> { it.createdAt }.thenBy { it.id })
          .map { it.toDashboardReservation(titles, partySizes[it.id] ?: 0) }
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
                    partySizes[it.reservationId] ?: 0,
                )
              },
  )
}

private fun Reservation.toDashboardReservation(
    titles: Map<String, String>,
    partySize: Int,
) =
    DashboardReservation(
        reservationId = id,
        questTitle = titles[questId],
        explorerName = explorerName.ifBlank { null },
        partySize = partySize,
        requestedAt = createdAt,
        approved = status == ReservationStatus.APPROVED,
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
