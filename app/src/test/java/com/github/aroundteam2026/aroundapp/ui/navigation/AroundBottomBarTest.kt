// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.DarkAroundColors
import com.github.aroundteam2026.aroundapp.ui.theme.LightAroundColors
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Tests the bottom bar against the Figma "Bottom bar/Explorer" component. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AroundBottomBarTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun show(
      selected: Tab? = Tab.EXPLORE,
      darkTheme: Boolean = false,
      insets: WindowInsets = WindowInsets(0),
      onTabClick: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        AroundBottomBar(selected, onTabClick, windowInsets = insets)
      }
    }
  }

  private fun bar() = composeTestRule.onNodeWithTag(C.Tag.NAV_BAR)

  private fun tab(tab: Tab) = composeTestRule.onNodeWithTag(tab.tabTag)

  /** The part of [tab] tagged [tag], which the tab's merged semantics would hide otherwise. */
  private fun partOf(tab: Tab, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(tab.tabTag)),
          useUnmergedTree = true,
      )

  private fun pill(tab: Tab) = partOf(tab, C.Tag.NAV_TAB_PILL)

  private fun icon(tab: Tab) = partOf(tab, C.Tag.NAV_TAB_ICON)

  private fun label(tab: Tab) = partOf(tab, C.Tag.NAV_TAB_LABEL)

  private fun Dp.toPx(): Int = with(composeTestRule.density) { toPx().roundToInt() }

  @Test
  fun showsTheFigmaTabsFromLeftToRight() {
    show()

    assertEquals(listOf(Tab.EXPLORE, Tab.QUESTS, Tab.FRIENDS, Tab.PROFILE), Tab.entries)
    Tab.entries.zip(listOf("Explore", "Quests", "Friends", "Profile")).forEach { (tab, text) ->
      tab(tab).assertTextEquals(text)
    }
    val lefts = Tab.entries.map { tab(it).getBoundsInRoot().left.value }
    assertEquals(lefts.sorted().distinct(), lefts)
  }

  @Test
  fun spreadsTheTabsEvenlyBetweenItsSidePaddings() {
    show()

    val bar = bar().getBoundsInRoot()
    val tabs = Tab.entries.map { tab(it).getBoundsInRoot() }
    assertDpEquals("first tab's left", bar.left + 28.dp, tabs.first().left)
    assertDpEquals("last tab's right", bar.right - 28.dp, tabs.last().right)
    val gaps = tabs.zipWithNext { left, right -> right.left - left.right }
    gaps.forEach { assertDpEquals("gap between tabs", gaps.first(), it) }
  }

  @Test
  fun isAsTallAsInFigmaWithoutSystemBars() {
    show()

    bar().assertHeightIsEqualTo(84.dp)
  }

  @Test
  fun aShortNavigationBarFitsInTheDesignsBottomPadding() {
    show(insets = WindowInsets(bottom = 16.dp))

    bar().assertHeightIsEqualTo(84.dp)
  }

  @Test
  fun growsToKeepTheTabsAboveATallerNavigationBar() {
    show(insets = WindowInsets(bottom = 48.dp))

    // The design's 22dp of bottom padding is replaced by the system bar's 48dp
    bar().assertHeightIsEqualTo(84.dp - 22.dp + 48.dp)
    val barBottom = bar().getBoundsInRoot().bottom
    Tab.entries.forEach { assertTrue(tab(it).getBoundsInRoot().bottom <= barBottom - 48.dp) }
  }

  @Test
  fun growsWithLargeTextRatherThanCuttingTheLabels() {
    composeTestRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        AroundAppTheme(dynamicColor = false) {
          AroundBottomBar(Tab.EXPLORE, {}, windowInsets = WindowInsets(0))
        }
      }
    }

    val bar = bar().getBoundsInRoot()
    Tab.entries.forEach {
      val label = label(it).getBoundsInRoot()
      // 12sp at twice the size is at least 24dp tall when laid out whole
      assertTrue("$it label is cut to ${label.height}", label.height >= 24.dp)
      assertTrue("$it label runs into the bottom padding", label.bottom <= bar.bottom - 22.dp)
    }
  }

  @Test
  fun pillsAndIconsHaveTheFigmaSizesAndPlaces() {
    show()

    val barTop = bar().getBoundsInRoot().top
    Tab.entries.forEach {
      pill(it).assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(30.dp)
      icon(it).assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
      val pill = pill(it).getBoundsInRoot()
      val icon = icon(it).getBoundsInRoot()
      val label = label(it).getBoundsInRoot()
      assertDpEquals("pill below the bar's top padding", barTop + 10.dp, pill.top)
      assertDpEquals(
          "icon centred across",
          (pill.left + pill.right) / 2,
          (icon.left + icon.right) / 2,
      )
      assertDpEquals(
          "icon centred down",
          (pill.top + pill.bottom) / 2,
          (icon.top + icon.bottom) / 2,
      )
      assertDpEquals("label 4dp below the pill", pill.bottom + 4.dp, label.top)
      assertDpEquals(
          "label centred under the pill",
          (pill.left + pill.right) / 2,
          (label.left + label.right) / 2,
      )
    }
  }

  @Test
  fun onlyTheSelectedTabIsSelected() {
    var selected by mutableStateOf<Tab?>(Tab.EXPLORE)
    composeTestRule.setContent {
      AroundAppTheme(dynamicColor = false) {
        AroundBottomBar(selected, {}, windowInsets = WindowInsets(0))
      }
    }

    (Tab.entries + null).forEach { current ->
      selected = current
      composeTestRule.waitForIdle()
      Tab.entries.forEach {
        if (it == current) tab(it).assertIsSelected() else tab(it).assertIsNotSelected()
      }
    }
  }

  @Test
  fun tellsAssistiveTechnologiesTheTabsAreTabsInOneGroup() {
    show()

    bar().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    Tab.entries.forEach {
      tab(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }
  }

  @Test
  fun theIconsAreDecorativeAsTheLabelsNameTheTabs() {
    show()

    Tab.entries.forEach {
      icon(it).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }
  }

  @Test
  fun tappingAnyTabReportsItEvenTheSelectedOne() {
    val clicks = mutableListOf<Tab>()
    show(selected = Tab.EXPLORE, onTabClick = { clicks += it })

    Tab.entries.forEach { tab(it).performClick() }

    assertEquals(Tab.entries, clicks)
  }

  @Test
  fun theSelectedPillIsMintAndTheOthersShowTheBar() {
    show(selected = Tab.FRIENDS)

    Tab.entries.forEach {
      val expected = if (it == Tab.FRIENDS) LightAroundColors.mint else LightAroundColors.surface
      // Inside the pill's rounded end, clear of the icon
      assertPixel(pill(it).captureToImage(), 3.dp.toPx(), 15.dp.toPx(), expected)
    }
  }

  @Test
  fun theSelectedTabIsDrawnInPrimaryAndTheOthersInMuted() {
    show(selected = Tab.QUESTS)

    Tab.entries.forEach {
      val (ink, notInk) =
          if (it == Tab.QUESTS) LightAroundColors.primary to LightAroundColors.muted
          else LightAroundColors.muted to LightAroundColors.primary
      assertDrawnIn(icon(it).captureToImage(), ink, notInk, "$it icon")
      assertDrawnIn(label(it).captureToImage(), ink, notInk, "$it label")
    }
  }

  @Test
  fun hasAHairlineInTheLineColourOnTop() {
    show()

    val image = bar().captureToImage()
    // Midway across, between the second and third tabs, where nothing else is drawn
    val x = image.width / 2
    assertPixel(image, x, 0, LightAroundColors.line)
    assertPixel(image, x, 1.dp.toPx() + 2, LightAroundColors.surface)
  }

  @Test
  fun followsTheDarkTheme() {
    show(selected = Tab.EXPLORE, darkTheme = true)

    val image = bar().captureToImage()
    assertPixel(image, image.width / 2, 5.dp.toPx(), DarkAroundColors.surface)
    assertPixel(
        pill(Tab.EXPLORE).captureToImage(),
        3.dp.toPx(),
        15.dp.toPx(),
        DarkAroundColors.mint,
    )
    assertDrawnIn(
        icon(Tab.EXPLORE).captureToImage(),
        DarkAroundColors.primary,
        DarkAroundColors.muted,
        "icon",
    )
    assertDrawnIn(
        label(Tab.PROFILE).captureToImage(),
        DarkAroundColors.muted,
        DarkAroundColors.primary,
        "label",
    )
  }

  private fun assertPixel(image: ImageBitmap, x: Int, y: Int, expected: Color) {
    val actual = image.toPixelMap()[x, y]
    assertTrue("Pixel ($x, $y) is $actual, not $expected", actual.isCloseTo(expected))
  }

  /** Some pixel of [image] is [ink], and none is [notInk]: the content is drawn in [ink]. */
  private fun assertDrawnIn(image: ImageBitmap, ink: Color, notInk: Color, what: String) {
    val pixels =
        image.toPixelMap().let { map ->
          (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map[x, y] } }
        }
    assertTrue("$what has no pixel in $ink", pixels.any { it.isCloseTo(ink) })
    assertFalse("$what has a pixel in $notInk", pixels.any { it.isCloseTo(notInk) })
  }

  private fun Color.isCloseTo(other: Color): Boolean =
      abs(red - other.red) < TOLERANCE &&
          abs(green - other.green) < TOLERANCE &&
          abs(blue - other.blue) < TOLERANCE

  private companion object {
    /** Allows for the rounding of colours drawn on pixels. */
    const val TOLERANCE = 3f / 255
  }
}
