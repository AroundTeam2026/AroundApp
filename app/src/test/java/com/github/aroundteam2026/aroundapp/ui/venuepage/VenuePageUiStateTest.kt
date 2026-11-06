// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.ui.quest.CancelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VenuePageUiStateTest {
  private fun completion(reservationId: String) =
      Completion(
          id = "proof of $reservationId",
          reservationId = reservationId,
          venueId = "venue",
          explorerUid = "explorer",
          proof = Proof.Photo("gs://proofs/fox"),
          status = CompletionStatus.PENDING,
          rejectReason = null,
          submittedAt = 0L,
      )

  private fun quest(id: String) =
      VenuePageQuest(
          questId = id,
          title = "Quest $id",
          description = "About $id",
          requirements = "Order a drink",
          proofType = ProofType.PHOTO,
          minPartySize = 1,
          reward = null,
      )

  private val loaded =
      VenuePageUiState(
          status = VenuePageStatus.LOADED,
          venueName = "Café Fox",
          quests = listOf(quest("a"), quest("b")),
      )

  @Test
  fun startsLoadingWithNothingToShow() {
    val state = VenuePageUiState()
    assertEquals(VenuePageStatus.LOADING, state.status)
    assertEquals("", state.venueName)
    assertNull(state.avatar)
    assertNull(state.address)
    assertEquals(emptyList<VenuePageQuest>(), state.quests)
    assertNull(state.selectedQuestId)
    assertNull(state.selectedQuest)
    assertNull(state.detailQuestId)
    assertNull(state.detailQuest)
  }

  @Test
  fun selectedQuestIsTheListedQuestWithTheSelectedId() {
    assertEquals(quest("b"), loaded.copy(selectedQuestId = "b").selectedQuest)
  }

  @Test
  fun selectedQuestIsNullWhenItIsNoLongerListed() {
    assertNull(loaded.copy(selectedQuestId = "gone").selectedQuest)
  }

  @Test
  fun tappingAListedQuestPicksIt() {
    assertEquals("a", loaded.withQuestTapped("a").selectedQuestId)
  }

  @Test
  fun tappingAnotherQuestPicksItInstead() {
    assertEquals("b", loaded.withQuestTapped("a").withQuestTapped("b").selectedQuestId)
  }

  @Test
  fun tappingThePickedQuestUnpicksIt() {
    assertNull(loaded.withQuestTapped("a").withQuestTapped("a").selectedQuestId)
  }

  @Test
  fun tappingAnUnlistedQuestChangesNothing() {
    val picked = loaded.withQuestTapped("a")
    assertSame(picked, picked.withQuestTapped("gone"))
  }

  @Test
  fun newQuestsReplaceTheListAndKeepAQuestThatIsStillListed() {
    val updated = loaded.withQuestTapped("a").withQuests(listOf(quest("c"), quest("a")))

    assertEquals(listOf(quest("c"), quest("a")), updated.quests)
    assertEquals("a", updated.selectedQuestId)
  }

  @Test
  fun aPickedQuestThatLeavesTheListIsUnpickedForGood() {
    val left = loaded.withQuestTapped("a").withQuests(listOf(quest("b")))
    assertNull(left.selectedQuestId)

    // Coming back, e.g. when its venue extends an expired reward, doesn't pick it again
    assertNull(left.withQuests(listOf(quest("a"), quest("b"))).selectedQuestId)
  }

  @Test
  fun closingTheDetailsGivesBackTheEarlierPick() {
    val closed = loaded.withQuestTapped("a").withQuestDetails("b").withoutQuestDetails()

    assertEquals("a", closed.selectedQuestId)
    assertNull(closed.detailQuestId)
    assertNull(closed.pickBeforeDetails)
  }

  @Test
  fun closingTheDetailsPicksNothingWithoutAnEarlierPick() {
    assertNull(loaded.withQuestDetails("b").withoutQuestDetails().selectedQuestId)
  }

  @Test
  fun closingDetailsThatAreNotOpenChangesNothing() {
    val picked = loaded.withQuestTapped("a")
    assertSame(picked, picked.withoutQuestDetails())
  }

  @Test
  fun detailsThatLeaveTheListGiveBackTheEarlierPick() {
    val left = loaded.withQuestTapped("a").withQuestDetails("b").withQuests(listOf(quest("a")))

    assertEquals("a", left.selectedQuestId)
    assertNull(left.detailQuestId)
    assertNull(left.pickBeforeDetails)
  }

  @Test
  fun anEarlierPickThatLeftTheListIsNotGivenBack() {
    val closed =
        loaded
            .withQuestTapped("a")
            .withQuestDetails("b")
            .withQuests(listOf(quest("b")))
            .withQuests(listOf(quest("a"), quest("b")))
            .withoutQuestDetails()

    assertNull(closed.selectedQuestId)
  }

  @Test
  fun openingAListedQuestsDetailsShowsThem() {
    val open = loaded.withQuestDetails("b")

    assertEquals("b", open.detailQuestId)
    assertEquals(quest("b"), open.detailQuest)
  }

  @Test
  fun openingTheDetailsPicksTheQuestSoThatItCanBeAccepted() {
    assertEquals("a", loaded.withQuestDetails("a").selectedQuestId)
  }

  @Test
  fun onlyTheReservationOfThePickedQuestCanBeCancelled() {
    val booked = loaded.copy(reservationIds = mapOf("a" to "r"))

    assertNull(booked.cancellableReservationId)
    assertEquals("r", booked.withQuestTapped("a").cancellableReservationId)
    assertNull(booked.withQuestTapped("b").cancellableReservationId)
  }

  @Test
  fun nothingCanBeCancelledWhileTheReservationsCantBeRead() {
    val booked = loaded.copy(reservationIds = mapOf("a" to "r"), selectedQuestId = "a")

    assertNull(booked.copy(acceptAvailable = false).cancellableReservationId)
  }

  @Test
  fun onlyAnApprovedReservationOfThePickedQuestCanBeCompleted() {
    val booked = loaded.copy(reservationIds = mapOf("a" to "r"), selectedQuestId = "a")

    assertNull(booked.completableReservationId)
    assertEquals("r", booked.copy(approvedIds = setOf("r")).completableReservationId)
    assertNull(
        booked.copy(approvedIds = setOf("r"), selectedQuestId = "b").completableReservationId
    )
  }

  @Test
  fun aProofBelongsToThePickedQuestsReservationAndEndsCompleting() {
    val sent =
        loaded.copy(
            reservationIds = mapOf("a" to "r"),
            selectedQuestId = "a",
            approvedIds = setOf("r"),
            completions = mapOf("r" to completion("r")),
        )

    assertEquals("r", sent.proof?.reservationId)
    assertNull(sent.completableReservationId)
    assertNull(sent.copy(selectedQuestId = "b").proof)
    assertNull(sent.copy(selectedQuestId = null).proof)
  }

  @Test
  fun aProofOfAnotherReservationLeavesThePickedQuestCompletable() {
    val approved =
        loaded.copy(
            reservationIds = mapOf("a" to "r"),
            selectedQuestId = "a",
            approvedIds = setOf("r"),
            completions = mapOf("other" to completion("other")),
        )

    assertNull(approved.proof)
    assertEquals("r", approved.completableReservationId)
  }

  @Test
  fun nothingCanBeCompletedWhileTheProofsCantBeRead() {
    val approved =
        loaded.copy(
            reservationIds = mapOf("a" to "r"),
            selectedQuestId = "a",
            approvedIds = setOf("r"),
        )

    assertNull(approved.copy(proofsAvailable = false).completableReservationId)
  }

  @Test
  fun nothingCanBeCompletedWhileTheReservationsCantBeRead() {
    val approved =
        loaded.copy(
            reservationIds = mapOf("a" to "r"),
            selectedQuestId = "a",
            approvedIds = setOf("r"),
        )

    assertNull(approved.copy(acceptAvailable = false).completableReservationId)
  }

  @Test
  fun cancelStatusFollowsThePickedQuestsReservation() {
    val picked = loaded.copy(selectedQuestId = "a", reservationIds = mapOf("a" to "r"))

    assertEquals(CancelStatus.NONE, picked.cancelStatus)
    assertEquals(
        CancelStatus.CANCELLING,
        picked.copy(cancellingIds = setOf("r")).cancelStatus,
    )
    assertEquals(
        CancelStatus.FAILED,
        picked.copy(failedCancelIds = setOf("r")).cancelStatus,
    )
    assertEquals(
        CancelStatus.NONE,
        picked.copy(cancellingIds = setOf("other")).cancelStatus,
    )
  }

  @Test
  fun openingAnUnlistedQuestsDetailsChangesNothing() {
    assertSame(loaded, loaded.withQuestDetails("gone"))
  }

  @Test
  fun detailQuestIsNullWhenItIsNoLongerListed() {
    assertNull(loaded.copy(detailQuestId = "gone").detailQuest)
  }

  @Test
  fun theDetailsStayOpenWhileTheirQuestIsListed() {
    val updated = loaded.withQuestDetails("a").withQuests(listOf(quest("c"), quest("a")))
    assertEquals("a", updated.detailQuestId)
  }

  @Test
  fun theDetailsCloseForGoodWhenTheirQuestLeavesTheList() {
    val left = loaded.withQuestDetails("a").withQuests(listOf(quest("b")))
    assertNull(left.detailQuestId)

    assertNull(left.withQuests(listOf(quest("a"), quest("b"))).detailQuestId)
  }

  @Test
  fun acceptingIsAvailableUntilTheReservationsCantBeRead() {
    assertTrue(VenuePageUiState().acceptAvailable)
  }

  @Test
  fun showsNothingToAcceptUntilAQuestIsPicked() {
    assertEquals(AcceptStatus.NONE, loaded.acceptStatus)
    assertEquals(0, loaded.morePeopleNeeded)
  }

  @Test
  fun showsAcceptingFailedOrAcceptedForThePickedQuestOnly() {
    val picked = loaded.withQuestTapped("a")

    assertEquals(AcceptStatus.NONE, picked.acceptStatus)
    assertEquals(AcceptStatus.ACCEPTING, picked.copy(acceptingQuestIds = setOf("a")).acceptStatus)
    assertEquals(AcceptStatus.FAILED, picked.copy(failedQuestIds = setOf("a")).acceptStatus)
    assertEquals(AcceptStatus.ACCEPTED, picked.copy(acceptedParties = mapOf("a" to 1)).acceptStatus)
    // The attempt and the failure belong to another quest
    assertEquals(AcceptStatus.NONE, picked.copy(acceptingQuestIds = setOf("b")).acceptStatus)
    assertEquals(AcceptStatus.NONE, picked.copy(failedQuestIds = setOf("b")).acceptStatus)
    assertEquals(AcceptStatus.NONE, picked.copy(acceptedParties = mapOf("b" to 1)).acceptStatus)
  }

  @Test
  fun acceptedWinsOverAnAttemptThatIsStillSettling() {
    val state =
        loaded
            .withQuestTapped("a")
            .copy(acceptingQuestIds = setOf("a"), acceptedParties = mapOf("a" to 1))

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
  }

  @Test
  fun countsTheMissingPeopleAgainstTheMinimumPartySize() {
    val state =
        VenuePageUiState(
            quests = listOf(quest("a").copy(minPartySize = 4)),
            selectedQuestId = "a",
        )

    assertEquals(3, state.morePeopleNeeded) // the explorer alone
    assertEquals(2, state.copy(acceptedParties = mapOf("a" to 2)).morePeopleNeeded)
    assertEquals(0, state.copy(acceptedParties = mapOf("a" to 4)).morePeopleNeeded)
    assertEquals(0, state.copy(acceptedParties = mapOf("a" to 6)).morePeopleNeeded)
  }
}
