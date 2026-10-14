// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.AnyRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/**
 * Minimal email/password form backed by [viewModel].
 *
 * Account creation requires confirmation. The navigation owner observes
 * AuthRepository.currentUserId for success and decides the destination. Create account registers an
 * Explorer and the venue link a Venue. [completeRegistration] offers the same two choices without
 * asking for credentials. Use [RegistrationFlow] to provide profile setup and receive
 * account-completion notifications.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    completeRegistration: Boolean = false,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var isCreatingAccount by
      rememberSaveable(completeRegistration) { mutableStateOf(completeRegistration) }
  val title =
      when {
        completeRegistration -> R.string.auth_complete_registration
        isCreatingAccount -> R.string.auth_create_account
        else -> R.string.auth_sign_in
      }
  val register = { role: Role ->
    if (completeRegistration) viewModel.completeRegistration(role) else viewModel.signUp(role)
  }
  // Leaving mid-request would drop the form while the account choice is being recorded.
  BackHandler(enabled = state.isLoading) {}
  Surface(modifier = modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.testTag(C.Tag.AUTH_SCREEN)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineMedium)
      Text(
          stringResource(title),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.testTag(C.Tag.AUTH_TITLE),
      )
      if (!completeRegistration) {
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_EMAIL),
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            enabled = !state.isLoading,
            fieldTag = C.Tag.AUTH_PASSWORD,
            toggleTag = C.Tag.AUTH_PASSWORD_VISIBILITY,
        )
        if (isCreatingAccount) {
          PasswordField(
              value = state.passwordConfirmation,
              onValueChange = viewModel::onPasswordConfirmationChange,
              label = stringResource(R.string.auth_confirm_password),
              enabled = !state.isLoading,
              fieldTag = C.Tag.AUTH_CONFIRMATION,
              toggleTag = C.Tag.AUTH_CONFIRMATION_VISIBILITY,
          )
        }
      }

      state.error?.let { error ->
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
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier.testTag(C.Tag.AUTH_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
      if (state.isLoading) {
        CircularProgressIndicator(Modifier.testTag(C.Tag.AUTH_LOADING))
      }
      if (viewModel.supportsRegistration && isCreatingAccount) {
        TextButton(
            onClick = { register(Role.VENUE) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_VENUE_LINK),
            // The default primary colour is unreadable on the dark background.
            colors = ButtonDefaults.textButtonColors(contentColor = AroundTheme.colors.accentText),
        ) {
          Text(stringResource(R.string.auth_venue_link))
        }
      }
      Button(
          onClick = { if (isCreatingAccount) register(Role.EXPLORER) else viewModel.signIn() },
          enabled = !state.isLoading,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_SUBMIT),
      ) {
        Text(
            stringResource(
                if (isCreatingAccount) R.string.auth_create_account else R.string.auth_sign_in
            )
        )
      }
      if (!completeRegistration) {
        TextButton(
            onClick = {
              isCreatingAccount = !isCreatingAccount
              viewModel.onModeChange()
            },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_SWITCH_MODE),
        ) {
          Text(
              stringResource(
                  if (isCreatingAccount) R.string.auth_have_account else R.string.auth_need_account
              )
          )
        }
      }
    }
  }
}

/** Visibility is local presentation state; toggling never changes the entered credentials. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    fieldTag: String,
    toggleTag: String,
) {
  var visible by rememberSaveable { mutableStateOf(false) }
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      enabled = enabled,
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
              stringResource(
                  if (visible) R.string.auth_hide_password else R.string.auth_show_password,
                  label,
              ),
          )
        }
      },
      modifier = Modifier.fillMaxWidth().testTag(fieldTag),
  )
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
      AuthFormError.UNKNOWN -> R.string.auth_unknown_error
    }
