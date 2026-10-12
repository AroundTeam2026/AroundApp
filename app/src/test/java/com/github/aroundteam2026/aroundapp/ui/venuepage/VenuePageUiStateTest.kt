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
}
