// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.auth

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthInputValidation
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * Minimal email/password form backed by [viewModel].
 *
 * Account creation requires confirmation. The navigation owner observes
 * AuthRepository.currentUserId for success and decides the destination.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var isCreatingAccount by rememberSaveable { mutableStateOf(false) }
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
          stringResource(
              if (isCreatingAccount) R.string.auth_create_account else R.string.auth_sign_in
          ),
          style = MaterialTheme.typography.titleLarge,
      )
      OutlinedTextField(
          value = state.email,
          onValueChange = viewModel::onEmailChange,
          label = { Text(stringResource(R.string.auth_email)) },
          singleLine = true,
          enabled = !state.isLoading,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_EMAIL),
      )
      OutlinedTextField(
          value = state.password,
          onValueChange = viewModel::onPasswordChange,
          label = { Text(stringResource(R.string.auth_password)) },
          singleLine = true,
          enabled = !state.isLoading,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_PASSWORD),
      )
      if (isCreatingAccount) {
        OutlinedTextField(
            value = state.passwordConfirmation,
            onValueChange = viewModel::onPasswordConfirmationChange,
            label = { Text(stringResource(R.string.auth_confirm_password)) },
            singleLine = true,
            enabled = !state.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_CONFIRMATION),
        )
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
      Button(
          onClick = { if (isCreatingAccount) viewModel.signUp() else viewModel.signIn() },
          enabled = !state.isLoading,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.AUTH_SUBMIT),
      ) {
        Text(
            stringResource(
                if (isCreatingAccount) R.string.auth_create_account else R.string.auth_sign_in
            )
        )
      }
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
      AuthFormError.UNKNOWN -> R.string.auth_unknown_error
    }
