// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.pressBack
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Welcome step, the way back to it, and the auth screens' shared theme background. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthWelcomeTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var backFallbacks = 0

  private fun launch(darkTheme: Boolean = false) {
    compose.runOnUiThread {
      compose.activity.onBackPressedDispatcher.addCallback(
          object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
              backFallbacks++
            }
          }
      )
    }
    val viewModel = AuthViewModel(FakeAuthRepository())
    compose.setContent { AroundAppTheme(darkTheme = darkTheme) { AuthScreen(viewModel) } }
  }

  private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

  private fun assertOnWelcome() {
    compose.onNodeWithTag(C.Tag.AUTH_WELCOME).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertDoesNotExist()
  }

  @Test
  fun signedOutUsersStartOnWelcomeWithTheWordmarkAndTagline() {
    launch()
    assertOnWelcome()
    compose.onNodeWithContentDescription("Around").assertIsDisplayed()
    compose.onNodeWithText("What’s Around ?").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT).assertTextEquals("Create account")
    compose.onNodeWithTag(C.Tag.AUTH_WELCOME_SIGN_IN).assertTextEquals("Sign in")
  }

  @Test
  fun welcomeButtonsOpenTheirFormAndTheBackButtonReturns() {
    launch()
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Create account")
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_GOOGLE).assertIsDisplayed()
    click(C.Tag.AUTH_BACK)
    assertOnWelcome()

    click(C.Tag.AUTH_WELCOME_SIGN_IN)
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Sign in")
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_GOOGLE).assertIsDisplayed()
    click(C.Tag.AUTH_BACK)
    assertOnWelcome()
    assertEquals(0, backFallbacks)
  }

  @Test
  fun systemBackReturnsFromAFormToWelcomeThenLeavesTheScreen() {
    launch()
    click(C.Tag.AUTH_WELCOME_SIGN_IN)
    compose.pressBack()
    assertOnWelcome()
    assertEquals(0, backFallbacks)
    compose.pressBack()
    assertEquals(1, backFallbacks)
  }

  @Test
  fun validationErrorsOutlineTheFieldTheyAreAbout() {
    val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)
    launch()
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    click(C.Tag.AUTH_SUBMIT)
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assert(hasError)
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).assert(!hasError)

    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("654321")
    click(C.Tag.AUTH_SUBMIT)
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assert(!hasError)
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assert(hasError)
  }

  @Test
  fun screenReadersAnnounceEachFieldLabelOnceOnTheField() {
    launch()
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    for ((label, tag) in
        listOf(
            "Email" to C.Tag.AUTH_EMAIL,
            "Password" to C.Tag.AUTH_PASSWORD,
            "Confirm password" to C.Tag.AUTH_CONFIRMATION,
        )) {
      compose.onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(0)
      compose.onAllNodesWithContentDescription(label).assertCountEquals(1)
      compose.onNodeWithTag(tag).assertContentDescriptionEquals(label)
    }
  }

  @Test
  fun lightAuthScreensUseTheCreamThemeBackground() =
      assertBackground(darkTheme = false, expected = AroundColors.Cream)

  @Test
  fun darkAuthScreensUseTheInkThemeBackground() =
      assertBackground(darkTheme = true, expected = AroundColors.Ink)

  /** Checks a corner of Welcome and of each form, and saves them as review artifacts. */
  private fun assertBackground(darkTheme: Boolean, expected: Color) {
    launch(darkTheme)
    val mode = if (darkTheme) "dark" else "light"
    assertCorner(capture("welcome-$mode"), expected)
    click(C.Tag.AUTH_WELCOME_SIGN_IN)
    assertCorner(capture("sign-in-$mode"), expected)
    click(C.Tag.AUTH_BACK)
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    assertCorner(capture("create-account-$mode"), expected)
  }

  private fun capture(name: String): ImageBitmap {
    val image = compose.onNodeWithTag(C.Tag.AUTH_SCREEN).captureToImage()
    val output = File("build/reports/auth-$name.png")
    output.parentFile?.mkdirs()
    output.outputStream().use {
      image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return image
  }

  private fun assertCorner(image: ImageBitmap, expected: Color) {
    assertEquals(expected, image.toPixelMap()[1, 1])
  }
}
