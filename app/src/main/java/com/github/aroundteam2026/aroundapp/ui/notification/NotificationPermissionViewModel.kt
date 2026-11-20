// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.notification

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.notification.NotificationPromptStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the notification prompt after sign-in stands. */
enum class NotificationPrompt {
  /** The earlier answer is being read; nothing is shown yet. */
  CHECKING,
  /** The explanation from Figma is shown. */
  EXPLAINING,
  /** Android's permission dialog is open over the explanation. */
  ASKING,
  /** Nothing to show: answered before, already allowed, or Android below 13. */
  DONE,
}

/**
 * Decides whether to explain notifications after sign-in, and remembers the answer.
 *
 * Android 13 and later ask for the notification permission at run time; below that, apps have it
 * from install, so there is nothing to ask. An answer, "Not now" or "Turn on notifications", is
 * remembered before Android's dialog opens, so a refusal there isn't asked about again either.
 *
 * @param sdkInt the phone's Android version, `Build.VERSION.SDK_INT` outside tests.
 * @param isGranted whether notifications are already allowed.
 */
class NotificationPermissionViewModel(
    private val store: NotificationPromptStore,
    sdkInt: Int,
    isGranted: () -> Boolean,
) : ViewModel() {

  private val _prompt = MutableStateFlow(NotificationPrompt.CHECKING)
  val prompt: StateFlow<NotificationPrompt> = _prompt.asStateFlow()

  init {
    viewModelScope.launch {
      val ask = sdkInt >= Build.VERSION_CODES.TIRAMISU && !isGranted() && !store.wasAnswered()
      _prompt.value = if (ask) NotificationPrompt.EXPLAINING else NotificationPrompt.DONE
    }
  }

  /** "Turn on notifications": the screen opens Android's dialog next. */
  fun allow() {
    if (_prompt.value != NotificationPrompt.EXPLAINING) return
    _prompt.value = NotificationPrompt.ASKING
    rememberAnswer()
  }

  /** Android's dialog closed, allowed or not. */
  fun onDialogClosed() {
    _prompt.value = NotificationPrompt.DONE
  }

  /** "Not now", or Back: a refusal. */
  fun notNow() {
    if (_prompt.value != NotificationPrompt.EXPLAINING) return
    _prompt.value = NotificationPrompt.DONE
    rememberAnswer()
  }

  private fun rememberAnswer() {
    viewModelScope.launch {
      try {
        store.markAnswered()
      } catch (_: IOException) {
        // ponytail: the explanation comes back next launch, which is better than never asking
      }
    }
  }

  companion object {
    fun factory(
        store: NotificationPromptStore,
        sdkInt: Int,
        isGranted: () -> Boolean,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { NotificationPermissionViewModel(store, sdkInt, isGranted) }
    }
  }
}
