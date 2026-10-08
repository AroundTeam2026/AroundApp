// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests the address search field and its suggestions against the Figma search components. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddressSearchBarTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val bourg =
      AddressSuggestion("Rue de Bourg 12", "1003 Lausanne", Location(46.5199, 6.6346))
  private val gare =
      AddressSuggestion("Place de la Gare 9", "1003 Lausanne", Location(46.5167, 6.6291))

  private var state by mutableStateOf(AddressSearchUiState())
  private val queries = mutableListOf<String>()
  private var searches = 0
  private var clears = 0
  private val picks = mutableListOf<AddressSuggestion>()

  private fun show(colors: AddressSearchColors? = null) {
    composeTestRule.setContent {
      AddressSearchBar(
          state = state,
          onQueryChange = {
            queries += it
            state = state.copy(query = it)
          },
          onSearch = { searches++ },
          onClear = { clears++ },
          onPick = { picks += it },
          colors = colors ?: AddressSearchDefaults.colors(),
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun results() = composeTestRule.onAllNodesWithTag(C.Tag.VENUE_ADDRESS_RESULT)

  private fun titleOf(index: Int) =
      composeTestRule
          .onAllNodes(
              hasTestTag(C.Tag.VENUE_ADDRESS_RESULT_TITLE),
              useUnmergedTree = true,
          )[index]

  @Test
  fun anEmptyFieldShowsThePlaceholderAndNoClearButton() {
    show()

    composeTestRule.onNodeWithText("Search for your address").assertIsDisplayed()
    node(C.Tag.VENUE_ADDRESS_CLEAR).assertDoesNotExist()
  }

  @Test
  fun typingReportsTheQuery() {
    show()

    node(C.Tag.VENUE_ADDRESS_FIELD).performTextInput("Rue")

    assertEquals("Rue", queries.last())
  }

  @Test
  fun theClearButtonAppearsWithAQueryAndClears() {
    state = AddressSearchUiState(query = "Rue")
    show()

    composeTestRule.onNodeWithContentDescription("Clear search").performClick()

    assertEquals(1, clears)
  }

  @Test
  fun theKeyboardsSearchKeySearches() {
    state = AddressSearchUiState(query = "Ru")
    show()

    node(C.Tag.VENUE_ADDRESS_FIELD).performImeAction()

    assertEquals(1, searches)
  }

  @Test
  fun listsTheSuggestionsInOrderWithTheirTwoLines() {
    state = AddressSearchUiState(query = "a", suggestions = listOf(bourg, gare))
    show()

    results().assertCountEquals(2)
    assertEquals("Rue de Bourg 12", titleOf(0).text())
    assertEquals("Place de la Gare 9", titleOf(1).text())
    composeTestRule.onAllNodesWithText("1003 Lausanne").assertCountEquals(2)
  }

  @Test
  fun tappingASuggestionPicksIt() {
    state = AddressSearchUiState(query = "a", suggestions = listOf(bourg, gare))
    show()

    results()[1].performClick()

    assertEquals(listOf(gare), picks)
  }

  @Test
  fun eachSuggestionIsOneButtonReadingBothLines() {
    state = AddressSearchUiState(query = "a", suggestions = listOf(bourg))
    show()

    results()[0]
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assert(
            SemanticsMatcher("reads both lines") {
              it.config.getOrNull(SemanticsProperties.Text)?.map(AnnotatedString::text) ==
                  listOf("Rue de Bourg 12", "1003 Lausanne")
            }
        )
  }

  @Test
  fun thePartOfATitleMatchingTheQueryIsHighlightedInAnyCase() {
    state = AddressSearchUiState(query = " bourg ", suggestions = listOf(bourg))
    show()

    val title = titleOf(0).annotated()
    val highlight = title.spanStyles.single()
    assertEquals("Bourg", title.text.substring(highlight.start, highlight.end))
    assertEquals(AddressSearchDefaults.colors(darkTheme = false).highlight, highlight.item.color)
  }

  @Test
  fun aTitleWithoutTheQueryIsNotHighlighted() {
    state = AddressSearchUiState(query = "gare", suggestions = listOf(bourg))
    show()

    assertTrue(titleOf(0).annotated().spanStyles.isEmpty())
  }

  @Test
  fun nothingIsShownBelowTheFieldWhileIdleWithoutSuggestions() {
    state = AddressSearchUiState(query = "Ru")
    show()

    node(C.Tag.VENUE_ADDRESS_RESULTS).assertDoesNotExist()
  }

  @Test
  fun aFirstSearchSaysItIsSearching() {
    state = AddressSearchUiState(query = "Rue", status = AddressSearchStatus.SEARCHING)
    show()

    composeTestRule.onNodeWithText("Searching…").assertIsDisplayed()
  }

  @Test
  fun aLaterSearchKeepsShowingTheEarlierSuggestions() {
    state =
        AddressSearchUiState(
            query = "Rue de",
            suggestions = listOf(bourg),
            status = AddressSearchStatus.SEARCHING,
        )
    show()

    results().assertCountEquals(1)
    composeTestRule.onNodeWithText("Searching…").assertDoesNotExist()
  }

  @Test
  fun eachProblemIsExplained() {
    val messages =
        mapOf(
            AddressSearchStatus.NO_RESULTS to "No addresses found",
            AddressSearchStatus.FAILED to
                "Couldn't search for addresses. Check your connection and try again.",
            AddressSearchStatus.UNAVAILABLE to "Address search isn't available on this device.",
        )
    show()

    messages.forEach { (status, message) ->
      state = AddressSearchUiState(query = "Rue", status = status)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }
  }

  @Test
  fun theFieldAndRowsHaveTheFigmaSizes() {
    state = AddressSearchUiState(query = "a", suggestions = listOf(bourg))
    show()

    node(C.Tag.VENUE_ADDRESS_FIELD_BOX).assertHeightIsEqualTo(44.dp)
    node(C.Tag.VENUE_ADDRESS_CLEAR).assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
    val avatar =
        composeTestRule.onNode(
            hasTestTag(C.Tag.VENUE_ADDRESS_RESULT_AVATAR) and
                hasAnyAncestor(hasTestTag(C.Tag.VENUE_ADDRESS_RESULT)),
            useUnmergedTree = true,
        )
    avatar.assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
    // 10dp above and below the avatar, the row's tallest part
    results()[0].assertHeightIsEqualTo(56.dp)
    val field = node(C.Tag.VENUE_ADDRESS_FIELD_BOX).getBoundsInRoot()
    val card = node(C.Tag.VENUE_ADDRESS_RESULTS).getBoundsInRoot()
    assertEquals(field.left, card.left)
    assertEquals(field.right, card.right)
  }

  @Test
  fun theFieldIsOutlinedInPrimaryOnTheSurface() {
    show()

    val image = node(C.Tag.VENUE_ADDRESS_FIELD_BOX).captureToImage()
    val middle = image.height / 2
    val light = AddressSearchDefaults.colors(darkTheme = false)
    // The left edge is the straight part of the outline
    assertPixel(image, 1.dp.px(), middle, light.outline)
    assertPixel(image, image.width - 1.dp.px() - 1, middle, light.outline)
    // Between the outline and the search icon
    assertPixel(image, 6.dp.px(), middle, light.surface)
  }

  @Test
  fun theDarkColoursComeFromTheFigmaDarkMode() {
    val dark = AddressSearchDefaults.colors(darkTheme = true)
    show(dark)

    val image = node(C.Tag.VENUE_ADDRESS_FIELD_BOX).captureToImage()
    assertPixel(image, 6.dp.px(), image.height / 2, Color(0xFF383838))
    assertEquals(Color(0xFF383838), dark.surface)
  }

  @Test
  @Config(qualifiers = "+night")
  fun theDefaultColoursFollowDarkMode() {
    var colors: AddressSearchColors? = null
    composeTestRule.setContent { colors = AddressSearchDefaults.colors() }

    assertEquals(AddressSearchDefaults.colors(darkTheme = true), colors)
  }

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.annotated(): AnnotatedString =
      fetchSemanticsNode().config[SemanticsProperties.Text].single()

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.text(): String = annotated().text

  private fun androidx.compose.ui.unit.Dp.px(): Int =
      with(composeTestRule.density) { toPx().roundToInt() }

  private fun assertPixel(image: ImageBitmap, x: Int, y: Int, expected: Color) {
    val actual = image.toPixelMap()[x, y]
    val close =
        abs(actual.red - expected.red) < 3f / 255 &&
            abs(actual.green - expected.green) < 3f / 255 &&
            abs(actual.blue - expected.blue) < 3f / 255
    assertTrue("Pixel ($x, $y) is $actual, not $expected", close)
  }
}
