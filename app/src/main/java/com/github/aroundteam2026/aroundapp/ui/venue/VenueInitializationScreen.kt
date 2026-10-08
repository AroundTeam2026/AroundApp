// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * Entry point for the venue sign-up flow. The navigation owner supplies the existing ViewModel and
 * moves to location setup when [onContinueToLocation] receives the saved venue id.
 */
@Composable
fun VenueInitializationScreen(
    viewModel: VenueInitializationViewModel,
    onBack: () -> Unit,
    onContinueToLocation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsState()
  val currentOnContinue by rememberUpdatedState(onContinueToLocation)
  LaunchedEffect(state.venueId) {
    state.venueId?.let {
      currentOnContinue(it)
      viewModel.onLocationNavigationHandled()
    }
  }
  BackHandler { if (!state.isSaving) onBack() }
  VenueInitializationContent(
      state = state,
      onNameChange = viewModel::updateBusinessName,
      onSubmit = viewModel::submit,
      onBack = onBack,
      modifier = modifier,
  )
}

/** Stateless form, also used by previews without authentication or repository dependencies. */
@Composable
fun VenueInitializationContent(
    state: VenueInitializationUiState,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  MaterialTheme(colorScheme = VenueSetupColors, typography = VenueSetupTypography) {
    val keyboard = LocalSoftwareKeyboardController.current
    val nameLabel = stringResource(R.string.venue_initialization_name)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .testTag(C.Tag.VENUE_INITIALIZATION_SCREEN)
    ) {
      Column(
          Modifier.fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .heightIn(min = maxHeight)
              .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp)
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Surface(
              onClick = onBack,
              enabled = !state.isSaving,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.size(48.dp).testTag(C.Tag.VENUE_INITIALIZATION_BACK),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Image(
                  painterResource(R.drawable.venue_setup_back),
                  stringResource(R.string.venue_initialization_back),
                  Modifier.size(22.dp),
              )
            }
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.venue_initialization_step),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Box(
                  Modifier.weight(1f)
                      .height(4.dp)
                      .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
              )
              Box(
                  Modifier.weight(1f)
                      .height(4.dp)
                      .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
              )
            }
          }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(R.string.venue_initialization_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.venue_initialization_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.venue_initialization_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.businessName,
            onValueChange = onNameChange,
            enabled = state.canEditName,
            singleLine = true,
            isError =
                state.error == VenueInitializationError.EMPTY_NAME ||
                    state.error == VenueInitializationError.NAME_TOO_LONG,
            placeholder = { Text(stringResource(R.string.venue_initialization_name_hint)) },
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                      keyboard?.hide()
                      onSubmit()
                    }
                ),
            modifier =
                Modifier.fillMaxWidth().testTag(C.Tag.VENUE_INITIALIZATION_NAME).semantics {
                  contentDescription = nameLabel
                },
        )
        state.error?.let { error ->
          val message =
              when (error) {
                VenueInitializationError.EMPTY_NAME ->
                    stringResource(R.string.venue_initialization_empty_name)
                VenueInitializationError.NAME_TOO_LONG ->
                    pluralStringResource(
                        R.plurals.venue_initialization_name_too_long,
                        VenueLimits.MAX_NAME_LENGTH,
                        VenueLimits.MAX_NAME_LENGTH,
                    )
                VenueInitializationError.SIGN_IN_REQUIRED ->
                    stringResource(R.string.venue_initialization_sign_in_required)
                VenueInitializationError.SAVE_FAILED ->
                    stringResource(R.string.venue_initialization_save_failed)
              }
          Text(
              message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
              modifier =
                  Modifier.padding(top = 8.dp).testTag(C.Tag.VENUE_INITIALIZATION_ERROR).semantics {
                    liveRegion = LiveRegionMode.Polite
                  },
          )
        }
        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
        Button(
            onClick = {
              keyboard?.hide()
              onSubmit()
            },
            enabled = state.canContinue,
            shape = RoundedCornerShape(16.dp),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            colors = ButtonDefaults.buttonColors(),
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag(C.Tag.VENUE_INITIALIZATION_CONTINUE),
        ) {
          Text(
              stringResource(
                  if (state.isSaving) R.string.venue_initialization_saving
                  else R.string.venue_initialization_continue
              ),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

// Scoped to this screen until the app's shared design tokens are implemented.
private val VenueSetupColors =
    lightColorScheme(
        primary = Color(0xFF4D0092),
        onPrimary = Color(0xFFFDFFE8),
        background = Color(0xFFFDFFE8),
        onBackground = Color(0xFF2D2D2D),
        surface = Color(0xFFFFFFF6),
        onSurface = Color(0xFF2D2D2D),
        onSurfaceVariant = Color(0xFF6E6C66),
        outline = Color(0xFFE3E5CF),
    )

@OptIn(ExperimentalTextApi::class)
private val VenueBodyFont =
    FontFamily(
        Font(
            R.font.venue_instrument_sans,
            weight = FontWeight.Normal,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(400),
                    FontVariation.Setting("wdth", 100f),
                ),
        ),
        Font(
            R.font.venue_instrument_sans,
            weight = FontWeight.SemiBold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(600),
                    FontVariation.Setting("wdth", 100f),
                ),
        ),
    )

@OptIn(ExperimentalTextApi::class)
private val VenueTitleFont =
    FontFamily(
        Font(
            R.font.venue_bricolage_grotesque,
            weight = FontWeight.Bold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.Setting("wdth", 100f),
                    FontVariation.opticalSizing(14.sp),
                ),
        ),
    )

private val VenueSetupTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = VenueTitleFont,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 35.sp,
                letterSpacing = (-0.96).sp,
            ),
        bodyLarge = TextStyle(fontFamily = VenueBodyFont, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = VenueBodyFont, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge =
            TextStyle(
                fontFamily = VenueBodyFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = VenueBodyFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            ),
    )

@Preview(showBackground = true, widthDp = 413, heightDp = 917)
@Composable
private fun VenueInitializationPreview() {
  VenueInitializationContent(
      VenueInitializationUiState(),
      onNameChange = {},
      onSubmit = {},
      onBack = {},
  )
}
