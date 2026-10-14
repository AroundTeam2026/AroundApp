// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class VenuePageUiStateTest {
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
  fun openingAListedQuestsDetailsShowsThem() {
    val open = loaded.withQuestDetails("b")

    assertEquals("b", open.detailQuestId)
    assertEquals(quest("b"), open.detailQuest)
  }

  @Test
  fun openingTheDetailsDoesNotPickTheQuest() {
    assertNull(loaded.withQuestDetails("a").selectedQuestId)
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
}
