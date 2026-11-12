// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests the party's members on the quest details. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class QuestDetailsPartyMembersTest {
  @get:Rule val compose = createComposeRule()

  private val quest =
      VenuePageQuest(
          questId = QUEST,
          title = "Find the hidden fox",
          description = "Spot the ceramic fox hidden in the café.",
          requirements = "",
          proofType = ProofType.PHOTO,
          minPartySize = 3,
          reward = null,
      )

  private val creator = PartyMemberUi("bea", "Bea Smith", isCreator = true)
  private val me = PartyMemberUi("me", "Ada Lovelace", isMe = true)

  /** The details of [quest], accepted, with these [members] in the party of [RESERVATION]. */
  private fun state(vararg members: PartyMemberUi) =
      VenuePageUiState(
          status = VenuePageStatus.LOADED,
          venueName = "Café Lumen",
          quests = listOf(quest),
          selectedQuestId = QUEST,
          detailQuestId = QUEST,
          acceptedParties = mapOf(QUEST to maxOf(members.size, 1)),
          reservationIds = mapOf(QUEST to RESERVATION),
          partyMembers =
              if (members.isEmpty()) emptyMap() else mapOf(RESERVATION to members.toList()),
      )

  private fun show(state: VenuePageUiState, onCancel: () -> Unit = {}) {
    compose.setContent {
      AroundAppTheme {
        QuestDetailsScreen(
            state,
            quest,
            onBack = {},
            onAccept = {},
            onCancel = onCancel,
        )
      }
    }
    compose.waitForIdle()
  }

  private fun count(tag: String) =
      compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

  private fun texts(tag: String) =
      compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
      }

  @Test
  fun showsEachMemberWithTheirInitialsAndNameThenTheOpenSeats() {
    show(state(creator, me))

    assertEquals(listOf("Bea Smith", "You"), texts(C.Tag.QUEST_DETAIL_MEMBER_NAME))
    assertEquals(listOf("BS", "AL"), texts(C.Tag.QUEST_DETAIL_MEMBER_INITIALS))
    assertEquals(1, count(C.Tag.QUEST_DETAIL_SEAT_EMPTY))
  }

  @Test
  fun showsAPictureInsteadOfTheInitials() {
    show(state(creator.copy(picture = ImageBitmap(1, 1)), me))

    assertEquals(1, count(C.Tag.QUEST_DETAIL_MEMBER_PICTURE))
    assertEquals(listOf("AL"), texts(C.Tag.QUEST_DETAIL_MEMBER_INITIALS))
  }

  @Test
  fun showsTheSeatsUntilTheMembersAreRead() {
    show(state())

    assertEquals(0, count(C.Tag.QUEST_DETAIL_MEMBER))
    assertEquals(2, count(C.Tag.QUEST_DETAIL_SEAT_EMPTY))
  }

  @Test
  fun aMemberWithoutANameIsCalledAnExplorer() {
    show(state(creator.copy(name = ""), me))

    assertEquals(listOf("An explorer", "You"), texts(C.Tag.QUEST_DETAIL_MEMBER_NAME))
  }

  @Test
  fun withoutItsReservationNobodyIsTheCreator() {
    fun member(uid: String, joinedAt: Long, status: PartyMemberStatus = PartyMemberStatus.JOINED) =
        PartyMember(RESERVATION, uid, status, joinedAt, joinedAt)

    val members =
        partyMembersOf(
                reservations = emptyList(),
                parties =
                    mapOf(
                        RESERVATION to
                            listOf(
                                member("me", 2),
                                member("bea", 1),
                                member("cleo", 0, PartyMemberStatus.WITHDRAWN),
                            )
                    ),
                profiles = emptyMap(),
                me = "me",
                pictureOf = { null },
            )
            .getValue(RESERVATION)

    assertEquals(listOf("bea", "me"), members.map { it.uid })
    assertEquals(listOf(false, false), members.map { it.isCreator })
    assertEquals(listOf(false, true), members.map { it.isMe })
  }

  @Test
  fun thePreviewShowsThreeMembers() {
    compose.setContent { PartyMembersPreview() }

    assertEquals(
        listOf("Bea Smith", "You", "Cleo Martin"),
        texts(C.Tag.QUEST_DETAIL_MEMBER_NAME),
    )
  }

  @Test
  fun aMemberLeavesThePartyAfterAsking() {
    var left = false
    show(state(creator, me).copy(joinedPartyIds = setOf(RESERVATION))) { left = true }

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).assertTextEquals("Leave party").performClick()
    compose.onNodeWithText("Leave this party?").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_CONFIRM).performClick()

    assertTrue(left)
  }

  @Test
  fun theCreatorCancelsTheReservation() {
    show(state(me.copy(isCreator = true), creator.copy(isCreator = false)))

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).assertTextEquals("Cancel reservation")
  }

  private companion object {
    const val QUEST = "quest"
    const val RESERVATION = "reservation"
  }
}
