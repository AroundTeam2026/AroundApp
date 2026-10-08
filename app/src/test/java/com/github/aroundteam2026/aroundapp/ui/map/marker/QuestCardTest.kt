// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.VenuePin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the card a pin turns into: what it says, and where things sit. */
@RunWith(AndroidJUnit4::class)
class QuestCardTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun pin(
      venueName: String = "Café Lumen",
      title: String = "Order the secret menu",
      reward: Reward? = Reward.Other("Free coffee"),
      minPartySize: Int = 1,
      otherQuestCount: Int = 0,
      distanceMeters: Double? = null,
      avatar: VenueAvatar = TestAvatar("cafe"),
  ): VenuePin {
    val quest =
        testQuest(
            venueName = venueName,
            title = title,
            reward = reward,
            minPartySize = minPartySize,
        )
    return VenuePin(
        venueId = quest.venueId,
        venueName = venueName,
        location = quest.location,
        icon = VenueAvatar.QuestFlag,
        avatar = avatar,
        featuredQuest = quest,
        otherQuestCount = otherQuestCount,
        distanceMeters = distanceMeters,
        areaRadiusMeters = quest.radiusMeters,
    )
  }

  /** Shows [pin]'s card, in the default style or with [dimensions]. */
  private fun show(pin: VenuePin, dimensions: MarkerDimensions? = null) {
    composeTestRule.setContent {
      if (dimensions == null) QuestCard(pin)
      else QuestCard(pin, style = MarkerDefaults.style(dimensions = dimensions))
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun bounds(tag: String) = node(tag).getBoundsInRoot()

  @Test
  fun namesTheQuestAndItsVenue() {
    show(pin(venueName = "Café Lumen", title = "Order the secret menu"))

    node(C.Tag.QUEST_CARD_TITLE).assertTextEquals("Order the secret menu")
    node(C.Tag.QUEST_CARD_VENUE).assertTextEquals("Café Lumen")
  }

  @Test
  fun theQuestComesFirstAndItsVenueUnderIt() {
    show(pin())

    val title = bounds(C.Tag.QUEST_CARD_TITLE)
    val venue = bounds(C.Tag.QUEST_CARD_VENUE)
    assertTrue("The venue isn't under the title", venue.top >= title.bottom)
    assertDpEquals("aligned", title.left, venue.left)
  }

  @Test
  fun theAvatarSitsOnTheLeftOfTheText() {
    show(pin())

    val card = bounds(C.Tag.QUEST_CARD_BODY)
    val avatar = bounds(C.Tag.QUEST_CARD_AVATAR)
    val title = bounds(C.Tag.QUEST_CARD_TITLE)
    val padding = MarkerDefaults.dimensions.cardPadding

    assertDpEquals("left", card.left + padding, avatar.left)
    assertDpEquals("top", card.top + padding, avatar.top)
    assertTrue("The text overlaps the avatar", title.left >= avatar.right)
  }

  @Test
  fun showsThePinsOwnAvatar() {
    show(pin(avatar = TestAvatar("lumen")))

    node(TestAvatar.tagOf("lumen")).assertIsDisplayed()
  }

  @Test
  fun anInitialsAvatarShowsTheInitials() {
    show(pin(avatar = VenueAvatar.Initials("CL")))

    composeTestRule.onNodeWithText("CL", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun showsTheRewardAsAChip() {
    show(pin(reward = Reward.Other("Free coffee")))

    node(C.Tag.QUEST_CARD_REWARD).assertTextEquals("Free coffee")
  }

  @Test
  fun aPercentDiscountSaysHowMuchIsOff() {
    show(pin(reward = Reward.Discount(10.0, DiscountUnit.PERCENT, specifics = null)))

    node(C.Tag.QUEST_CARD_REWARD).assertTextEquals("10% off")
  }

  @Test
  fun aFrancDiscountSaysHowManyFrancsAreOff() {
    show(pin(reward = Reward.Discount(2.5, DiscountUnit.CHF, specifics = null)))

    node(C.Tag.QUEST_CARD_REWARD).assertTextEquals("CHF 2.50 off")
  }

  @Test
  fun aFreeItemSaysWhatIsFree() {
    show(pin(reward = Reward.FreeItem("coffee", specifics = null)))

    node(C.Tag.QUEST_CARD_REWARD).assertTextEquals("Free coffee")
  }

  @Test
  fun aQuestWithoutARewardHasNoRewardChip() {
    show(pin(reward = null))

    node(C.Tag.QUEST_CARD_REWARD).assertDoesNotExist()
  }

  @Test
  fun aGroupQuestSaysHowManyMustCome() {
    show(pin(minPartySize = 6))

    val text = context.getString(R.string.map_card_party_size, 6)
    assertTrue("$text doesn't give the size", text.contains("6"))
    node(C.Tag.QUEST_CARD_PARTY).assertTextEquals(text)
  }

  @Test
  fun aPartyOfTwoIsAlreadyAGroup() {
    show(pin(minPartySize = 2))

    node(C.Tag.QUEST_CARD_PARTY)
        .assertTextEquals(context.getString(R.string.map_card_party_size, 2))
  }

  @Test
  fun aSoloQuestHasNoPartyChip() {
    show(pin(minPartySize = 1))

    node(C.Tag.QUEST_CARD_PARTY).assertDoesNotExist()
  }

  @Test
  fun saysHowFarTheVenueIs() {
    show(pin(distanceMeters = 1_234.0))

    // Robolectric runs in English
    node(C.Tag.QUEST_CARD_DISTANCE)
        .assertTextEquals(context.getString(R.string.map_card_distance_kilometers, "1.2"))
  }

  @Test
  fun saysShortDistancesInMetres() {
    show(pin(distanceMeters = 347.0))

    node(C.Tag.QUEST_CARD_DISTANCE)
        .assertTextEquals(context.getString(R.string.map_card_distance_meters, "350"))
  }

  @Test
  fun anUnknownDistanceHasNoChip() {
    // The explorer's position is unknown without the permission
    show(pin(distanceMeters = null))

    node(C.Tag.QUEST_CARD_DISTANCE).assertDoesNotExist()
  }

  @Test
  fun theChipsShareARowUnderTheVenue() {
    show(pin(minPartySize = 2, distanceMeters = 300.0))

    val venue = bounds(C.Tag.QUEST_CARD_VENUE)
    val reward = bounds(C.Tag.QUEST_CARD_REWARD)
    val party = bounds(C.Tag.QUEST_CARD_PARTY)
    val distance = bounds(C.Tag.QUEST_CARD_DISTANCE)
    assertTrue("The chips aren't under the venue", reward.top >= venue.bottom)
    assertDpEquals("party beside reward", reward.top, party.top)
    assertTrue("Chips overlap", party.left >= reward.right && distance.left >= party.right)
  }

  @Test
  fun chipsThatDoNotFitWrapInsteadOfWideningTheCard() {
    show(
        pin(
            reward = Reward.Other("Free dessert for the whole table"),
            minPartySize = 6,
            distanceMeters = 12_000.0,
        )
    )

    node(C.Tag.QUEST_CARD).assertWidthIsEqualTo(MarkerDefaults.dimensions.cardWidth)
    val card = bounds(C.Tag.QUEST_CARD_BODY)
    listOf(C.Tag.QUEST_CARD_REWARD, C.Tag.QUEST_CARD_PARTY, C.Tag.QUEST_CARD_DISTANCE).forEach {
      node(it).assertIsDisplayed()
      assertTrue("$it sticks out of the card", bounds(it).right <= card.right)
    }
  }

  @Test
  fun saysHowManyMoreQuestsTheVenueHas() {
    show(pin(otherQuestCount = 3))

    val text = context.resources.getQuantityString(R.plurals.map_card_more_quests, 3, 3)
    assertTrue("$text doesn't give the count", text.contains("3"))
    node(C.Tag.QUEST_CARD_MORE).assertTextEquals(text)
  }

  @Test
  fun aVenueWithASingleQuestSaysNothingMore() {
    show(pin(otherQuestCount = 0))

    node(C.Tag.QUEST_CARD_MORE).assertDoesNotExist()
  }

  @Test
  fun thePointersTipIsAtTheBottomCentre() {
    // The map anchors the card there, so the tip touches the venue's location
    show(pin())

    val card = bounds(C.Tag.QUEST_CARD)
    val pointer = bounds(C.Tag.QUEST_CARD_POINTER)

    assertDpEquals("bottom", card.bottom, pointer.bottom)
    assertDpEquals("centre", (card.left + card.right) / 2, (pointer.left + pointer.right) / 2)
  }

  @Test
  fun theCardKeepsItsWidthWhateverItSays() {
    // The map draws the card as an image; a width that follows the text would make cards jump
    show(pin(venueName = "A", title = "B", reward = null))

    node(C.Tag.QUEST_CARD).assertWidthIsEqualTo(MarkerDefaults.dimensions.cardWidth)
  }

  @Test
  fun aLongVenueNameStaysOnOneLine() {
    show(pin(venueName = "The Extraordinarily Long Name Of A Very Small Neighbourhood Café"))

    val layout = node(C.Tag.QUEST_CARD_VENUE).textLayout()
    assertEquals(1, layout.lineCount)
    assertTrue("The name should be cut off", layout.hasVisualOverflow)
    node(C.Tag.QUEST_CARD).assertWidthIsEqualTo(MarkerDefaults.dimensions.cardWidth)
  }

  @Test
  fun aLongQuestTitleTakesAtMostTwoLines() {
    show(pin(title = "Bring five friends, order the secret menu and find the hidden fox"))

    assertTrue(node(C.Tag.QUEST_CARD_TITLE).textLayout().lineCount <= 2)
  }

  @Test
  fun followsTheStyleItIsGiven() {
    // Figma changes go through the style: the card must not hard-code its sizes
    show(pin(), MarkerDimensions(cardWidth = 300.dp, cardPadding = 20.dp))

    node(C.Tag.QUEST_CARD).assertWidthIsEqualTo(300.dp)
    val card = bounds(C.Tag.QUEST_CARD_BODY)
    assertDpEquals("padding", card.left + 20.dp, bounds(C.Tag.QUEST_CARD_AVATAR).left)
  }
}

/** The layout of this text node. */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
  val results = mutableListOf<TextLayoutResult>()
  fetchSemanticsNode()
      .config
      .getOrNull(SemanticsActions.GetTextLayoutResult)
      ?.action
      ?.invoke(results)
  return results.single()
}
