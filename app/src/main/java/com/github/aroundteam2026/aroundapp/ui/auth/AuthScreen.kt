// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.AnyRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthInputValidation
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.questFieldColors
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import kotlinx.coroutines.launch

internal val AuthButtonHeight = 54.dp
private val BackButtonSize = 44.dp
private val BackIconSize = 22.dp
private val GoogleIconSize = 22.dp
private val OutlineWidth = 1.dp
private const val DIVIDER_ALPHA = 0.2f

/** Where a signed-out user is in the auth entry. */
internal enum class AuthStep {
  WELCOME,
  SIGN_IN,
  ACCOUNT_TYPE,
  CREATE_ACCOUNT,
  VENUE_ACCOUNT,
}

/**
 * Email/password and Google authentication backed by [viewModel], starting on the Welcome step.
 *
 * Welcome opens Sign in or an account-type choice. Explorer and Business use the same credentials
 * fields but submit their own role. Back follows the path used to open the Venue form. The
 * navigation owner observes AuthRepository.currentUserId for success and decides the destination.
 * Google users with no profile choose their role afterward. [completeRegistration] skips Welcome
 * and offers the same two choices without asking for credentials. Use [RegistrationFlow] to provide
 * profile setup and receive account-completion notifications. [googleSignInClient] acquires
 * credentials in the UI; tests supply a chooser result without opening a personal account.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    completeRegistration: Boolean = false,
    googleSignInClient: GoogleSignInClient = rememberGoogleSignInClient(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var step by
      rememberSaveable(completeRegistration) {
        mutableStateOf(if (completeRegistration) AuthStep.CREATE_ACCOUNT else AuthStep.WELCOME)
      }
  var venueOrigin by rememberSaveable { mutableStateOf(AuthStep.ACCOUNT_TYPE) }
  val openStep = { next: AuthStep ->
    step = next
    viewModel.onModeChange()
  }
  val back = {
    openStep(
        when (step) {
          AuthStep.VENUE_ACCOUNT -> venueOrigin
          AuthStep.CREATE_ACCOUNT -> AuthStep.ACCOUNT_TYPE
          else -> AuthStep.WELCOME
        }
    )
  }
  // Leaving mid-request would drop the form while the account choice is being recorded.
  BackHandler(enabled = state.isLoading || (!completeRegistration && step != AuthStep.WELCOME)) {
    if (!state.isLoading) back()
  }
  Box(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag(C.Tag.AUTH_SCREEN)
  ) {
    if (step == AuthStep.WELCOME) {
      WelcomeContent(
          onCreateAccount = { openStep(AuthStep.ACCOUNT_TYPE) },
          onSignIn = { openStep(AuthStep.SIGN_IN) },
      )
    } else if (step == AuthStep.ACCOUNT_TYPE) {
      AccountTypeChoice(
          enabled = !state.isLoading,
          onBack = back,
          onExplorer = { openStep(AuthStep.CREATE_ACCOUNT) },
          onBusiness = {
            venueOrigin = AuthStep.ACCOUNT_TYPE
            openStep(AuthStep.VENUE_ACCOUNT)
          },
          onSignIn = { openStep(AuthStep.SIGN_IN) },
      )
    } else {
      AuthForm(
          viewModel = viewModel,
          state = state,
          isCreatingAccount = step != AuthStep.SIGN_IN,
          isVenue = step == AuthStep.VENUE_ACCOUNT,
          completeRegistration = completeRegistration,
          googleSignInClient = googleSignInClient,
          onBack = back,
          onVenue = {
            venueOrigin = AuthStep.CREATE_ACCOUNT
            openStep(AuthStep.VENUE_ACCOUNT)
          },
      )
    }
  }
}

/** Account type choice from Welcome, before either credentials form. */
@Composable
private fun AccountTypeChoice(
    enabled: Boolean,
    onBack: () -> Unit,
    onExplorer: () -> Unit,
    onBusiness: () -> Unit,
    onSignIn: () -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(
                start = AroundSpacing.ScreenHorizontal,
                end = AroundSpacing.ScreenHorizontal,
                top = AroundSpacing.HeroTop,
                bottom = AroundSpacing.ScreenBottom,
            )
    ) {
      BackButton(enabled = enabled, onClick = onBack)
      Spacer(Modifier.height(AroundSpacing.Large))
      Text(
          stringResource(R.string.auth_choose_account_type),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(C.Tag.AUTH_ACCOUNT_TYPE_TITLE).semantics { heading() },
      )
      Spacer(Modifier.height(AroundSpacing.Small))
      Text(
          stringResource(R.string.auth_account_type_prompt),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(AroundSpacing.ScreenBottom))
      Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        AccountTypeCard(
            title = stringResource(R.string.auth_explorer),
            description = stringResource(R.string.auth_explorer_description),
            icon = R.drawable.auth_explorer_icon,
            business = false,
            enabled = enabled,
            onClick = onExplorer,
            modifier = Modifier.weight(1f).testTag(C.Tag.AUTH_EXPLORER_CARD),
        )
        AccountTypeCard(
            title = stringResource(R.string.auth_business),
            description = stringResource(R.string.auth_business_description),
            icon = R.drawable.auth_business_icon,
            business = true,
            enabled = enabled,
            onClick = onBusiness,
            modifier = Modifier.weight(1f).testTag(C.Tag.AUTH_BUSINESS_CARD),
        )
      }
      Spacer(Modifier.weight(1f).heightIn(min = AroundSpacing.Medium))
      Text(
          stringResource(R.string.auth_already_have_account),
          style = AroundTextStyles.InlineBody,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      Spacer(Modifier.height(AroundSpacing.Medium))
      AuthSignInButton(
          onClick = onSignIn,
          enabled = enabled,
          modifier = Modifier.testTag(C.Tag.AUTH_CHOICE_SIGN_IN),
      )
    }
  }
}

@Composable
private fun AccountTypeCard(
    title: String,
    description: String,
    icon: Int,
    business: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val foreground = if (business) colors.onPrimary else colors.onSurface
  val cardColor = if (business) colors.primary else colors.surface
  Surface(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(2.dp, colors.primary),
      color = cardColor,
      modifier = modifier.height(280.dp),
  ) {
    Column(Modifier.padding(AroundSpacing.Large)) {
      Box(
          Modifier.size(48.dp)
              .background(
                  if (business) colors.background else colors.primary.copy(alpha = 0.12f),
                  RoundedCornerShape(24.dp),
              ),
          contentAlignment = Alignment.Center,
      ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
      }
      Spacer(Modifier.height(AroundSpacing.CompactGap))
      Text(title, style = MaterialTheme.typography.titleMedium, color = foreground)
      Spacer(Modifier.height(AroundSpacing.CompactGap))
      Text(description, style = MaterialTheme.typography.bodySmall, color = foreground)
      Spacer(Modifier.weight(1f))
      Box(
          Modifier.fillMaxWidth()
              .height(37.dp)
              .background(
                  if (business) colors.background else colors.primary,
                  RoundedCornerShape(12.dp),
              ),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            stringResource(R.string.auth_sign_up),
            style = AroundTextStyles.Button,
            color = if (business) colors.primary else colors.onPrimary,
        )
      }
    }
  }
}

/**
 * The Sign in or Create account form, or the role choices alone in [completeRegistration] mode.
 *
 * @param isCreatingAccount shows the confirmation field and the registration actions.
 * @param onBack returns to the step that opened this form; not offered in [completeRegistration]
 *   mode.
 */
@Composable
private fun AuthForm(
    viewModel: AuthViewModel,
    state: AuthUiState,
    isCreatingAccount: Boolean,
    isVenue: Boolean,
    completeRegistration: Boolean,
    googleSignInClient: GoogleSignInClient,
    onBack: () -> Unit,
    onVenue: () -> Unit,
) {
  val googleScope = rememberCoroutineScope()
  val title =
      when {
        completeRegistration -> R.string.auth_complete_registration
        isVenue -> R.string.auth_venue_account
        isCreatingAccount -> R.string.auth_create_account
        else -> R.string.auth_sign_in
      }
  val register = { role: Role ->
    if (completeRegistration) viewModel.completeRegistration(role) else viewModel.signUp(role)
  }
  BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    start = AroundSpacing.ScreenHorizontal,
                    end = AroundSpacing.ScreenHorizontal,
                    top = AroundSpacing.HeroTop,
                    bottom = AroundSpacing.ScreenBottom,
                ),
    ) {
      if (!completeRegistration) {
        BackButton(enabled = !state.isLoading, onClick = onBack)
        Spacer(Modifier.height(AroundSpacing.Large))
      }
      Text(
          stringResource(title),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.testTag(C.Tag.AUTH_TITLE).semantics { heading() },
      )
      Spacer(Modifier.height(AroundSpacing.SetupGap))
      if (!completeRegistration) {
        LabeledField(stringResource(R.string.auth_email)) { label ->
          OutlinedTextField(
              value = state.email,
              onValueChange = viewModel::onEmailChange,
              singleLine = true,
              enabled = !state.isLoading,
              isError = state.error == AuthFormError.INVALID_EMAIL,
              shape = MaterialTheme.shapes.small,
              colors = questFieldColors(),
              textStyle = AroundTextStyles.FieldValue,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              modifier =
                  Modifier.fillMaxWidth().testTag(C.Tag.AUTH_EMAIL).semantics {
                    contentDescription = label
                  },
          )
        }
        Spacer(Modifier.height(AroundSpacing.Large))
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            showLabel = stringResource(R.string.auth_show_password),
            hideLabel = stringResource(R.string.auth_hide_password),
            enabled = !state.isLoading,
            isError =
                state.error == AuthFormError.EMPTY_PASSWORD ||
                    state.error == AuthFormError.PASSWORD_TOO_SHORT,
            fieldTag = C.Tag.AUTH_PASSWORD,
            toggleTag = C.Tag.AUTH_PASSWORD_VISIBILITY,
        )
        if (isCreatingAccount) {
          Spacer(Modifier.height(AroundSpacing.Large))
          PasswordField(
              value = state.passwordConfirmation,
              onValueChange = viewModel::onPasswordConfirmationChange,
              label = stringResource(R.string.auth_confirm_password),
              showLabel = stringResource(R.string.auth_show_confirmation_password),
              hideLabel = stringResource(R.string.auth_hide_confirmation_password),
              enabled = !state.isLoading,
              isError = state.error == AuthFormError.PASSWORD_MISMATCH,
              fieldTag = C.Tag.AUTH_CONFIRMATION,
              toggleTag = C.Tag.AUTH_CONFIRMATION_VISIBILITY,
          )
        }
      }
      state.error?.let { error ->
        Spacer(Modifier.height(AroundSpacing.Small))
        Text(
            if (error == AuthFormError.PASSWORD_TOO_SHORT) {
              pluralStringResource(
                  error.messageResource(),
                  AuthInputValidation.MIN_PASSWORD_LENGTH,
                  AuthInputValidation.MIN_PASSWORD_LENGTH,
              )
            } else {
              stringResource(error.messageResource())
            },
            style = AroundTextStyles.Caption,
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier.testTag(C.Tag.AUTH_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
      if (state.isLoading) {
        Spacer(Modifier.height(AroundSpacing.Large))
        CircularProgressIndicator(
            Modifier.align(Alignment.CenterHorizontally).testTag(C.Tag.AUTH_LOADING)
        )
      }
      Spacer(Modifier.weight(1f).heightIn(min = AroundSpacing.ExtraLarge))
      if (viewModel.supportsRegistration && isCreatingAccount && !isVenue) {
        TextButton(
            onClick = { if (completeRegistration) register(Role.VENUE) else onVenue() },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_VENUE_LINK),
            // The default primary colour is unreadable on the dark background.
            colors = ButtonDefaults.textButtonColors(contentColor = AroundTheme.colors.accentText),
        ) {
          Text(stringResource(R.string.auth_venue_link))
        }
        Spacer(Modifier.height(AroundSpacing.Small))
      }
      Button(
          onClick = {
            if (isCreatingAccount) register(if (isVenue) Role.VENUE else Role.EXPLORER)
            else viewModel.signIn()
          },
          enabled = !state.isLoading,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().height(AuthButtonHeight).testTag(C.Tag.AUTH_SUBMIT),
      ) {
        Text(
            stringResource(
                if (isVenue) R.string.auth_venue_account
                else if (isCreatingAccount) R.string.auth_create_account else R.string.auth_sign_in
            ),
            style = AroundTextStyles.Button,
        )
      }
      // Venue Google signup needs the role handoff added in the next change.
      if (!completeRegistration && !isVenue) {
        Spacer(Modifier.height(AroundSpacing.InsetPadding))
        OrDivider()
        Spacer(Modifier.height(AroundSpacing.InsetPadding))
        GoogleSignInButton(
            enabled = !state.isLoading,
            onClick = {
              if (viewModel.beginGoogleSignIn()) {
                googleScope.launch {
                  try {
                    viewModel.onGoogleCredential(googleSignInClient.requestToken())
                  } finally {
                    viewModel.cancelGoogleSignIn()
                  }
                }
              }
            },
        )
      }
    }
  }
}

/** Figma's round back button for the current auth step. */
@Composable
private fun BackButton(enabled: Boolean, onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      enabled = enabled,
      shape = AroundPillShape,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(OutlineWidth, MaterialTheme.colorScheme.outline),
      modifier = Modifier.size(BackButtonSize).testTag(C.Tag.AUTH_BACK),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
          painterResource(R.drawable.ic_chevron_left),
          contentDescription = stringResource(R.string.back),
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(BackIconSize),
      )
    }
  }
}

/**
 * A field with its label above it, as in Figma.
 *
 * @param label the visible label, also given to [field] for its accessibility description, so
 *   screen readers announce it once, on the field.
 */
@Composable
private fun LabeledField(label: String, field: @Composable (label: String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
    Text(
        label,
        style = AroundTextStyles.FieldLabel,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clearAndSetSemantics {},
    )
    field(label)
  }
}

/** The thin "or" divider between the main button and Google. */
@Composable
private fun OrDivider() {
  val line = MaterialTheme.colorScheme.primary.copy(alpha = DIVIDER_ALPHA)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    HorizontalDivider(Modifier.weight(1f), color = line)
    Text(
        stringResource(R.string.auth_or),
        style = AroundTextStyles.InlineBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider(Modifier.weight(1f), color = line)
  }
}

/**
 * Visibility is local presentation state; toggling never changes the entered credentials.
 *
 * @param value the entered password.
 * @param onValueChange forwards credential edits to the form ViewModel.
 * @param label the field's visible label.
 * @param showLabel the accessibility action for revealing this field.
 * @param hideLabel the accessibility action for masking this field.
 * @param enabled whether editing and visibility changes are allowed.
 * @param isError outlines the field in the error colour.
 * @param fieldTag identifies the input for UI tests.
 * @param toggleTag identifies its visibility button for UI tests.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showLabel: String,
    hideLabel: String,
    enabled: Boolean,
    isError: Boolean,
    fieldTag: String,
    toggleTag: String,
) {
  var visible by rememberSaveable { mutableStateOf(false) }
  LabeledField(label) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = MaterialTheme.shapes.small,
        colors = questFieldColors(),
        textStyle = AroundTextStyles.FieldValue,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
          IconButton(
              onClick = { visible = !visible },
              enabled = enabled,
              modifier = Modifier.testTag(toggleTag),
          ) {
            Icon(
                painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                if (visible) hideLabel else showLabel,
                tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
        modifier =
            Modifier.fillMaxWidth().testTag(fieldTag).semantics { contentDescription = label },
    )
  }
}

/** Returns the message resource; [AuthFormError.PASSWORD_TOO_SHORT] uses a plural resource. */
@AnyRes
internal fun AuthFormError.messageResource(): Int =
    when (this) {
      AuthFormError.INVALID_EMAIL -> R.string.auth_invalid_email
      AuthFormError.EMPTY_PASSWORD -> R.string.auth_empty_password
      AuthFormError.PASSWORD_TOO_SHORT -> R.plurals.auth_short_password
      AuthFormError.PASSWORD_MISMATCH -> R.string.auth_password_mismatch
      AuthFormError.WRONG_CREDENTIALS -> R.string.auth_wrong_credentials
      AuthFormError.EMAIL_TAKEN -> R.string.auth_email_taken
      AuthFormError.NETWORK -> R.string.auth_network_error
      AuthFormError.ACCOUNT_SETUP -> R.string.auth_account_setup_error
      AuthFormError.GOOGLE_UNAVAILABLE -> R.string.auth_google_unavailable
      AuthFormError.GOOGLE_CONFIGURATION -> R.string.auth_google_configuration
      AuthFormError.UNKNOWN -> R.string.auth_unknown_error
    }

/** Figma's outlined Google action, with Google's official multicolour branding asset. */
@Composable
private fun GoogleSignInButton(enabled: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().height(AuthButtonHeight).testTag(C.Tag.AUTH_GOOGLE),
      shape = MaterialTheme.shapes.medium,
      border = BorderStroke(OutlineWidth, colors.outline),
      colors =
          ButtonDefaults.outlinedButtonColors(
              containerColor = colors.surface,
              contentColor = colors.onSurface,
          ),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
          painterResource(R.drawable.ic_google_g),
          contentDescription = null,
          modifier = Modifier.size(GoogleIconSize),
      )
      Text(stringResource(R.string.auth_google_continue), style = AroundTextStyles.Button)
    }
  }
}
