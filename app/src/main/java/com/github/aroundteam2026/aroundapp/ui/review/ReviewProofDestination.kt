// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

private const val TAG = "ReviewProofDestination"

/**
 * Registers the Review proof screen, which takes the completion's id as [Routes.COMPLETION_ID].
 *
 * @param onClose Returns to the dashboard: on Back, and once the proof is reviewed.
 * @param factory Builds the screen's ViewModel for a completion's id; tests pass one on fakes.
 */
fun NavGraphBuilder.reviewProofDestination(
    onClose: () -> Unit,
    factory: (String) -> ViewModelProvider.Factory,
) {
  composable(
      Routes.REVIEW_PROOF,
      arguments = listOf(navArgument(Routes.COMPLETION_ID) { type = NavType.StringType }),
  ) { entry ->
    // The route only matches a non-blank id, so the argument is always there
    val completionId = requireNotNull(entry.arguments?.getString(Routes.COMPLETION_ID))
    // Keyed by the proof: single-top compares the route pattern, so two quick taps on different
    // proofs could otherwise keep the first proof's ViewModel
    ReviewProofScreen(
        viewModel(key = completionId, factory = factory(completionId)),
        onBack = onClose,
    )
  }
}

/** The app's ViewModel of the proof [completionId], with the app's repositories. */
internal fun defaultReviewProofFactory(completionId: String) =
    ReviewProofViewModel.factory(completionId)

/**
 * Opens the Review proof screen of the completion [completionId], without stacking it again if a
 * double tap already opened it.
 *
 * A blank [completionId] matches no route, so it opens nothing and is logged instead of crashing.
 */
fun NavController.navigateToReviewProof(completionId: String) {
  if (completionId.isBlank()) {
    Log.w(TAG, "Not opening a proof to review: the completion id is blank")
    return
  }
  navigate(Routes.reviewProof(completionId)) { launchSingleTop = true }
}
