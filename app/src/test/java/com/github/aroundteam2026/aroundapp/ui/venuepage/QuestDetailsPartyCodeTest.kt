// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** The party code on the quest details: shown only when the state gives one, copied and shared. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class QuestDetailsPartyCodeTest {
  @get:Rule val compose = createComposeRule()

  private val app = ApplicationProvider.getApplicationContext<Application>()

  private val quest =
      VenuePageQuest(
          questId = QUEST,
          title = "Find the hidden fox",
          description = "Spot the ceramic fox.",
          requirements = "",
          proofType = ProofType.PHOTO,
          minPartySize = 4,
          reward = null,
      )

  private fun show(partyCodes: Map<String, String>) {
    val state =
        VenuePageUiState(
            status = VenuePageStatus.LOADED,
            venueName = "Café Lumen",
            quests = listOf(quest),
            selectedQuestId = QUEST,
            detailQuestId = QUEST,
            acceptedParties = mapOf(QUEST to 2),
            reservationIds = mapOf(QUEST to RESERVATION),
            partyCodes = partyCodes,
        )
    compose.setContent {
      AroundAppTheme { QuestDetailsScreen(state, quest, onBack = {}, onAccept = {}, onCancel = {}) }
    }
    compose.waitForIdle()
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun theCodeIsShownInTwoHalves() {
    show(mapOf(RESERVATION to CODE))

    node(C.Tag.QUEST_DETAIL_PARTY_CODE).performScrollTo().assertTextEquals("7KQ M42")
  }

  // As for a friend who joined, or once the reservation is approved
  @Test
  fun withoutACodeTheBoxIsLeftOut() {
    show(emptyMap())

    node(C.Tag.QUEST_DETAIL_PARTY_CODE).assertDoesNotExist()
    node(C.Tag.QUEST_DETAIL_PARTY_CODE_SHARE).assertDoesNotExist()
  }

  // Named for screen readers, as it shows an icon only; the button takes its icon's name
  private fun copyButton() =
      compose.onNodeWithContentDescription(app.getString(R.string.quest_detail_party_code_copy))

  // Android 13 and later (Robolectric runs 34 here) confirm the copy themselves, so no toast
  @Test
  fun copyPutsTheCodeOnTheClipboardWithoutTheSpace() {
    show(mapOf(RESERVATION to CODE))

    copyButton().performScrollTo().performClick()

    val clipboard = app.getSystemService(ClipboardManager::class.java)
    assertEquals(CODE, clipboard.primaryClip?.getItemAt(0)?.text)
    assertNull(ShadowToast.getTextOfLatestToast())
  }

  @Test
  @Config(sdk = [32])
  fun copyConfirmsWithAToastBeforeAndroid13() {
    show(mapOf(RESERVATION to CODE))

    copyButton().performScrollTo().performClick()

    assertEquals(
        app.getString(R.string.quest_detail_party_code_copied),
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun theCopyButtonHasAFullSizeTouchTarget() {
    show(mapOf(RESERVATION to CODE))

    copyButton()
        .performScrollTo()
        .assertTouchWidthIsEqualTo(MIN_TOUCH_TARGET)
        .assertTouchHeightIsEqualTo(MIN_TOUCH_TARGET)
  }

  @Test
  fun shareOpensTheShareSheetWithTheCodeTheQuestAndTheVenue() {
    show(mapOf(RESERVATION to CODE))

    node(C.Tag.QUEST_DETAIL_PARTY_CODE_SHARE).performScrollTo().performClick()

    compose.runOnIdle {
      val chooser = shadowOf(app).nextStartedActivity
      assertEquals(Intent.ACTION_CHOOSER, chooser.action)
      @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
      assertEquals(Intent.ACTION_SEND, send.action)
      assertEquals("text/plain", send.type)
      assertEquals(
          "Join my party for “Find the hidden fox” at Café Lumen on Around! " +
              "The party code is 7KQ M42.",
          send.getStringExtra(Intent.EXTRA_TEXT),
      )
    }
  }

  @Test
  fun partyCodeShown_splitsTheCodeInTwoHalves() {
    assertEquals("7KQ M42", partyCodeShown(CODE))
  }

  private companion object {
    const val QUEST = "fox"
    const val RESERVATION = "r"
    const val CODE = "7KQM42"
    val MIN_TOUCH_TARGET = 48.dp
  }
}
