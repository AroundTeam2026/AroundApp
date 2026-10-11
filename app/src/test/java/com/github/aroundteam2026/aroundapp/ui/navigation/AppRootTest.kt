// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
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
    val destination = mutableStateOf(AppDestination.LOADING)
    val composed = mutableSetOf<AppDestination>()
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = {
            composed.add(AppDestination.AUTH)
            Text(AppDestination.AUTH.name)
          },
          roleSelection = {
            composed.add(AppDestination.ROLE_SELECTION)
            Text(AppDestination.ROLE_SELECTION.name)
          },
          explorerHome = {
            composed.add(AppDestination.EXPLORER_HOME)
            Text(AppDestination.EXPLORER_HOME.name)
          },
          venueEntry = {
            composed.add(AppDestination.VENUE_ENTRY)
            Text(AppDestination.VENUE_ENTRY.name)
          },
          profileError = {
            composed.add(AppDestination.PROFILE_ERROR)
            Text(AppDestination.PROFILE_ERROR.name)
          },
          loading = {
            composed.add(AppDestination.LOADING)
            Text(AppDestination.LOADING.name)
          },
      )
    }
    assertOnly(AppDestination.LOADING)
    compose.runOnIdle { assertEquals(setOf(AppDestination.LOADING), composed) }
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
    val destination = mutableStateOf(AppDestination.LOADING)
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = { Text(AppDestination.AUTH.name) },
          roleSelection = { Text(AppDestination.ROLE_SELECTION.name) },
          explorerHome = { Text(AppDestination.EXPLORER_HOME.name) },
          venueEntry = { Text(AppDestination.VENUE_ENTRY.name) },
          profileError = { Text(AppDestination.PROFILE_ERROR.name) },
      )
    }
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertIsDisplayed()
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    AppDestination.entries.forEach { compose.onNodeWithText(it.name).assertDoesNotExist() }
    AppDestination.entries.drop(1).forEach { selected ->
      compose.runOnIdle { destination.value = selected }
      assertOnly(selected)
      compose.onNodeWithTag(C.Tag.APP_LOADING).assertDoesNotExist()
      compose
          .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
          .assertDoesNotExist()
    }
    compose.runOnIdle { destination.value = AppDestination.LOADING }
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertIsDisplayed()
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    AppDestination.entries.forEach { compose.onNodeWithText(it.name).assertDoesNotExist() }
  }

  @Test
  fun customLoadingReplacesTheDefaultAndIsDisposedWhenLeaving() {
    val destination = mutableStateOf(AppDestination.LOADING)
    var disposals = 0
    compose.setContent {
      AppRoot(
          destination = destination.value,
          authFlow = { Text(AppDestination.AUTH.name) },
          roleSelection = { Text(AppDestination.ROLE_SELECTION.name) },
          explorerHome = { Text(AppDestination.EXPLORER_HOME.name) },
          venueEntry = { Text(AppDestination.VENUE_ENTRY.name) },
          profileError = { Text(AppDestination.PROFILE_ERROR.name) },
          loading = {
            DisposableEffect(Unit) { onDispose { disposals++ } }
            Text(AppDestination.LOADING.name)
          },
      )
    }
    assertOnly(AppDestination.LOADING)
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertDoesNotExist()
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    compose.runOnIdle { destination.value = AppDestination.AUTH }
    assertOnly(AppDestination.AUTH)
    compose.runOnIdle { assertEquals(1, disposals) }
    compose.runOnIdle { destination.value = AppDestination.LOADING }
    assertOnly(AppDestination.LOADING)
    compose.runOnIdle { assertEquals(1, disposals) }
  }
}
