// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRootTest {
  @get:Rule val compose = createComposeRule()

  private fun assertOnly(destination: AppDestination) {
    AppDestination.entries.forEach {
      val node = compose.onNodeWithText(it.name)
      if (it == destination) node.assertIsDisplayed() else node.assertDoesNotExist()
    }
  }

  @Test
  fun eachDestinationComposesOnlyItsPlaceholderAndReplacesThePreviousSlot() {
    val destination = mutableStateOf(AppDestination.Loading)
    val composed = mutableSetOf<AppDestination>()
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = {
            composed.add(AppDestination.Auth)
            Text("Auth")
          },
          roleSelection = {
            composed.add(AppDestination.RoleSelection)
            Text("RoleSelection")
          },
          explorerHome = {
            composed.add(AppDestination.ExplorerHome)
            Text("ExplorerHome")
          },
          venueEntry = {
            composed.add(AppDestination.VenueEntry)
            Text("VenueEntry")
          },
          loading = {
            composed.add(AppDestination.Loading)
            Text("Loading")
          },
      )
    }
    assertOnly(AppDestination.Loading)
    compose.runOnIdle { assertEquals(setOf(AppDestination.Loading), composed) }
    AppDestination.entries.drop(1).forEach { selected ->
      compose.runOnIdle {
        composed.clear()
        destination.value = selected
      }
      assertOnly(selected)
      compose.runOnIdle { assertEquals(setOf(selected), composed) }
    }
  }

  @Test
  fun loadingDefaultsToAProgressIndicator() {
    val destination = mutableStateOf(AppDestination.Loading)
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = { Text("Auth") },
          roleSelection = { Text("RoleSelection") },
          explorerHome = { Text("ExplorerHome") },
          venueEntry = { Text("VenueEntry") },
      )
    }
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    AppDestination.entries.forEach { compose.onNodeWithText(it.name).assertDoesNotExist() }
    AppDestination.entries.drop(1).forEach { selected ->
      compose.runOnIdle { destination.value = selected }
      assertOnly(selected)
      compose
          .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
          .assertDoesNotExist()
    }
    compose.runOnIdle { destination.value = AppDestination.Loading }
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    AppDestination.entries.forEach { compose.onNodeWithText(it.name).assertDoesNotExist() }
  }

  @Test
  fun customLoadingReplacesTheDefaultAndIsDisposedWhenLeaving() {
    val destination = mutableStateOf(AppDestination.Loading)
    var disposals = 0
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = { Text("Auth") },
          roleSelection = { Text("RoleSelection") },
          explorerHome = { Text("ExplorerHome") },
          venueEntry = { Text("VenueEntry") },
          loading = {
            DisposableEffect(Unit) { onDispose { disposals++ } }
            Text("Loading")
          },
      )
    }
    assertOnly(AppDestination.Loading)
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    compose.runOnIdle { destination.value = AppDestination.Auth }
    assertOnly(AppDestination.Auth)
    compose.runOnIdle { assertEquals(1, disposals) }
    compose.runOnIdle { destination.value = AppDestination.Loading }
    assertOnly(AppDestination.Loading)
    compose.runOnIdle { assertEquals(1, disposals) }
  }

  @Test
  fun slotCanUseCallerStateAndCallbacksToChangeDestination() {
    val destination = mutableStateOf(AppDestination.Auth)
    val clicks = mutableStateOf(0)
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = {
            Column {
              Text("Clicks: ${clicks.value}")
              Button(onClick = { clicks.value++ }) { Text("Increment") }
              Button(onClick = { destination.value = AppDestination.ExplorerHome }) {
                Text("Continue")
              }
            }
          },
          roleSelection = { Text("RoleSelection") },
          explorerHome = { Text("ExplorerHome") },
          venueEntry = { Text("VenueEntry") },
      )
    }
    compose.onNodeWithText("Increment").performClick()
    compose.onNodeWithText("Clicks: 1").assertIsDisplayed()
    compose.onNodeWithText("Continue").performClick()
    assertOnly(AppDestination.ExplorerHome)
    listOf("Clicks: 1", "Increment", "Continue").forEach {
      compose.onNodeWithText(it).assertDoesNotExist()
    }
  }
}
