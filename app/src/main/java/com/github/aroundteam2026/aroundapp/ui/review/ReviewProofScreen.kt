// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.partyLabel
import com.github.aroundteam2026.aroundapp.ui.dashboard.rememberNow
import com.github.aroundteam2026.aroundapp.ui.dashboard.timeAgo
import com.github.aroundteam2026.aroundapp.ui.proof.ProofPhotoViewer
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf
import com.github.aroundteam2026.aroundapp.ui.quest.rewardText
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import java.time.Clock
import kotlinx.coroutines.launch

/**
 * The Review proof screen, as the Figma frames `S4 · Review proof`: who sent the proof, for which
 * quest, the photo or the written answer, and Approve and Reject. Reject opens [RejectProofSheet].
 * A failed answer shows a snackbar once. Once the proof is reviewed, here or on another phone,
 * [onBack] returns to the dashboard; while an answer is being saved, Back waits for it.
 *
 * @param viewModel Holds the proof the dashboard opened.
 * @param onBack Returns to the dashboard.
 */
@Composable
fun ReviewProofScreen(
    viewModel: ReviewProofViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val answerFailed = stringResource(R.string.review_proof_answer_failed)
  LaunchedEffect(state.updateFailed) {
    if (state.updateFailed) {
      // Outside this effect, which clearing the failure ends before the snackbar is gone
      scope.launch { snackbarHostState.showSnackbar(answerFailed) }
      viewModel.clearUpdateError()
    }
  }
  val currentOnBack by rememberUpdatedState(onBack)
  LaunchedEffect(state.reviewed) { if (state.reviewed) currentOnBack() }
  BackHandler(state.saving) {}
  ReviewProofContent(
      state = state,
      onApprove = viewModel::approve,
      onReject = viewModel::reject,
      onRetry = viewModel::retry,
      onBack = onBack,
      modifier = modifier,
      snackbarHostState = snackbarHostState,
  )
}

/**
 * Stateless Review proof screen showing [state]. Reject opens the reject sheet, which passes the
 * reason to [onReject]; [onRetry] reads the proof, or downloads its photo, again. [clock] dates
 * when the proof was sent.
 */
@Composable
internal fun ReviewProofContent(
    state: ReviewProofUiState,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    clock: Clock = Clock.systemDefaultZone(),
) {
  var rejecting by rememberSaveable { mutableStateOf(false) }
  val loaded = state.status == ReviewProofStatus.LOADED
  // The sheet closes while the proof can't be shown; once it loads again it starts closed
  LaunchedEffect(loaded) { if (!loaded) rejecting = false }
  Scaffold(
      modifier = modifier.testTag(C.Tag.REVIEW_PROOF_SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (loaded) {
          Actions(
              state,
              approving = state.saving && !rejecting,
              onApprove = onApprove,
              onReject = { rejecting = true },
          )
        }
      },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState())
            .padding(AroundSpacing.SectionGap),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
    ) {
      TopBar(onBack, enabled = !state.saving)
      when (state.status) {
        ReviewProofStatus.LOADING ->
            CircularProgressIndicator(
                Modifier.align(Alignment.CenterHorizontally).testTag(C.Tag.REVIEW_PROOF_LOADING)
            )
        ReviewProofStatus.FAILED -> LoadFailed(onRetry)
        ReviewProofStatus.LOADED -> {
          Explorer(state, clock)
          Quest(state.questTitle, state.requirements)
          when (val proof = state.proof) {
            is Proof.Photo -> PhotoProof(state, explorerInSentence(state), clock, onRetry)
            is Proof.Text -> AnswerProof(proof.answer)
            null -> Unit
          }
        }
      }
    }
  }
  if (rejecting && loaded && !state.reviewed) {
    RejectProofSheet(
        explorerName = state.explorerName,
        proofType = state.proof?.type ?: ProofType.PHOTO,
        saving = state.saving,
        onReject = onReject,
        onDismiss = { rejecting = false },
        snackbarHostState = snackbarHostState,
    )
  }
}

/** The back button and the title, as Figma's top bar. */
@Composable
private fun TopBar(onBack: () -> Unit, enabled: Boolean) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    OutlinedIconButton(
        onClick = onBack,
        enabled = enabled,
        modifier =
            Modifier.align(Alignment.CenterStart)
                .size(AVATAR_SIZE)
                .testTag(C.Tag.REVIEW_PROOF_BACK),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors =
            IconButtonDefaults.outlinedIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
    ) {
      Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.review_proof_back))
    }
    Text(
        stringResource(R.string.review_proof_title),
        Modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onBackground,
        style = AroundTextStyles.CardTitle,
    )
  }
}

/** Who sent the proof, their party and when, then the Pending tag. */
@Composable
private fun Explorer(state: ReviewProofUiState, clock: Clock) {
  val name = state.explorerName
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
        Modifier.size(AVATAR_SIZE),
        shape = AroundPillShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
            name?.let(::initialsOf) ?: stringResource(R.string.dashboard_unknown_initials),
            style = AVATAR_STYLE,
        )
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NAME_GAP)) {
      Text(
          name ?: stringResource(R.string.dashboard_unknown_explorer),
          Modifier.testTag(C.Tag.REVIEW_PROOF_EXPLORER),
          color = MaterialTheme.colorScheme.onBackground,
          style = AroundTextStyles.RowTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          sentText(state.partySize, state.sentAt, rememberNow(state.sentAt, clock)),
          Modifier.testTag(C.Tag.REVIEW_PROOF_SENT),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.Caption,
      )
    }
    Surface(
        shape = AroundPillShape,
        color = AroundTheme.colors.lavender,
        contentColor = AroundTheme.colors.accentText,
    ) {
      Text(
          stringResource(R.string.review_proof_pending),
          Modifier.padding(horizontal = AroundSpacing.Medium, vertical = TAG_VERTICAL_PADDING),
          style = AroundTextStyles.Badge,
      )
    }
  }
}

/**
 * "Party of 4 · sent 2 min. ago", "Solo visit · sent …", or only "Sent …" when the party isn't
 * known; "just now" under a minute, rather than "0 min. ago".
 */
@Composable
private fun sentText(partySize: Int, sentAt: Long, now: Long): String {
  val party = partyLabel(partySize)
  val ago = timeAgo(sentAt, now)
  return if (ago == null) {
    party?.let { stringResource(R.string.review_proof_party_and_sent_just_now, it) }
        ?: stringResource(R.string.review_proof_sent_just_now)
  } else {
    party?.let { stringResource(R.string.review_proof_party_and_sent, it, ago) }
        ?: stringResource(R.string.review_proof_sent, ago)
  }
}

/** The quest's title and what it asks for, in an inset panel. */
@Composable
private fun Quest(title: String?, requirements: String?) {
  Surface(
      shape = AroundCorners.Card,
      color = AroundTheme.colors.panel,
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
        Modifier.fillMaxWidth().padding(AroundSpacing.InsetPadding),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
    ) {
      Text(
          title ?: stringResource(R.string.dashboard_unknown_quest),
          Modifier.testTag(C.Tag.REVIEW_PROOF_QUEST),
          style = AroundTextStyles.RowTitle,
      )
      if (!requirements.isNullOrBlank()) {
        Text(
            stringResource(R.string.review_proof_requirements, requirements),
            Modifier.testTag(C.Tag.REVIEW_PROOF_REQUIREMENTS),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = AroundTextStyles.Caption,
        )
      }
    }
  }
}

/**
 * The photo sent by [explorer] once downloaded, whole, so nothing near its edges is cut off, with
 * "Tap to zoom": tapping it opens it full screen, in [ProofPhotoViewer]. Until then a spinner; if
 * it can't be downloaded or read, a placeholder with a way to try again.
 */
@Composable
private fun PhotoProof(
    state: ReviewProofUiState,
    explorer: String,
    clock: Clock,
    onRetry: () -> Unit,
) {
  var viewing by rememberSaveable { mutableStateOf(false) }
  Box(
      Modifier.fillMaxWidth()
          .height(PHOTO_HEIGHT)
          .clip(AroundCorners.Photo)
          .background(AroundTheme.colors.sage)
          .testTag(C.Tag.REVIEW_PROOF_PHOTO),
      contentAlignment = Alignment.Center,
  ) {
    when (val photo = state.photo) {
      is ProofPhoto.Loaded -> {
        val description = stringResource(R.string.review_proof_photo_description, explorer)
        // Clickable around both: the chip's Surface takes the taps that land on it, so a clickable
        // on the Image alone would miss them
        Box(
            Modifier.fillMaxSize().clickable(
                onClickLabel = stringResource(R.string.proof_viewer_open),
                role = Role.Image,
            ) {
              viewing = true
            }
        ) {
          Image(
              photo.image,
              description,
              Modifier.fillMaxSize().testTag(C.Tag.REVIEW_PROOF_PHOTO_IMAGE),
              contentScale = ContentScale.Fit,
          )
          TapToZoom(Modifier.align(Alignment.BottomEnd).padding(AroundSpacing.Medium))
        }
        if (viewing) {
          ProofPhotoViewer(
              photo.image,
              description,
              title = state.explorerName ?: stringResource(R.string.dashboard_unknown_explorer),
              subtitle = viewerSubtitle(state, clock),
              onClose = { viewing = false },
          )
        }
      }
      ProofPhoto.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
      ProofPhoto.Failed -> PhotoPlaceholder(onRetry)
      null -> PhotoPlaceholder(onRetry = null)
    }
  }
}

/** The "Tap to zoom" hint in the photo's corner, as Figma's "Zoom hint". */
@Composable
private fun TapToZoom(modifier: Modifier) {
  Surface(
      modifier.testTag(C.Tag.REVIEW_PROOF_PHOTO_ZOOM),
      shape = AroundPillShape,
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
        Modifier.padding(
            start = AroundSpacing.CompactGap,
            end = AroundSpacing.Medium,
            top = AroundSpacing.SegmentGap,
            bottom = AroundSpacing.SegmentGap,
        ),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(R.drawable.ic_zoom_in),
          contentDescription = null,
          Modifier.size(ZOOM_ICON_SIZE),
      )
      Text(stringResource(R.string.proof_viewer_tap_to_zoom), style = AroundTextStyles.Badge)
    }
  }
}

/** Under the explorer's name on the full-screen photo: "Find the hidden fox · sent 2 min. ago". */
@Composable
private fun viewerSubtitle(state: ReviewProofUiState, clock: Clock): String {
  val quest = state.questTitle ?: stringResource(R.string.dashboard_unknown_quest)
  val ago = timeAgo(state.sentAt, rememberNow(state.sentAt, clock))
  return if (ago == null) stringResource(R.string.proof_viewer_quest_and_sent_just_now, quest)
  else stringResource(R.string.proof_viewer_quest_and_sent, quest, ago)
}

/** A camera and "Photo proof" where the photo can't be shown; [onRetry], if any, tries again. */
@Composable
private fun PhotoPlaceholder(onRetry: (() -> Unit)?) {
  val color = MaterialTheme.colorScheme.onPrimary
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    Icon(
        painterResource(R.drawable.ic_proof_photo),
        contentDescription = null,
        Modifier.size(PLACEHOLDER_ICON_SIZE),
        tint = color,
    )
    Text(
        stringResource(R.string.review_proof_photo),
        color = color,
        style = AroundTextStyles.Subtitle,
    )
    if (onRetry != null) {
      TextButton(
          onClick = onRetry,
          modifier = Modifier.testTag(C.Tag.REVIEW_PROOF_PHOTO_RETRY),
          colors = ButtonDefaults.textButtonColors(contentColor = color),
      ) {
        Text(stringResource(R.string.review_proof_photo_retry))
      }
    }
  }
}

/** The explorer's written [answer], under "Their answer". */
@Composable
private fun AnswerProof(answer: String) {
  Surface(
      shape = AroundCorners.Card,
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
        Modifier.fillMaxWidth().padding(AroundSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
    ) {
      Text(
          stringResource(R.string.review_proof_answer_label).uppercase(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.Label,
      )
      Text(answer, Modifier.testTag(C.Tag.REVIEW_PROOF_ANSWER), style = ANSWER_STYLE)
    }
  }
}

/** Why the proof isn't shown, and a way to read it again. */
@Composable
private fun ColumnScope.LoadFailed(onRetry: () -> Unit) {
  Text(
      stringResource(R.string.review_proof_load_failed),
      Modifier.testTag(C.Tag.REVIEW_PROOF_LOAD_FAILED),
      color = MaterialTheme.colorScheme.error,
      style = AroundTextStyles.Body,
  )
  TextButton(
      onClick = onRetry,
      modifier = Modifier.align(Alignment.Start).testTag(C.Tag.REVIEW_PROOF_RETRY),
  ) {
    Text(stringResource(R.string.review_proof_retry))
  }
}

/**
 * What approving gives, then Reject and Approve, disabled unless the proof can be answered. While
 * [approving], Approve shows it; the reject sheet shows a rejection being saved.
 */
@Composable
private fun Actions(
    state: ReviewProofUiState,
    approving: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
  val explorer = explorerInSentence(state)
  val reward = state.reward
  val hint =
      when {
        state.saving -> stringResource(R.string.review_proof_sending, explorer)
        reward == null -> null
        // A party of 0 couldn't be read: it may be solo or a whole party
        state.partySize <= 0 ->
            stringResource(R.string.review_proof_reward_unknown_party, rewardText(reward))
        else ->
            stringResource(
                if (state.partySize > 1) R.string.review_proof_reward_party
                else R.string.review_proof_reward_solo,
                explorer,
                rewardText(reward),
            )
      }
  Column(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(AroundSpacing.SectionGap),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    if (hint != null) {
      Text(
          hint,
          Modifier.fillMaxWidth().testTag(C.Tag.REVIEW_PROOF_HINT),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.Small,
          textAlign = TextAlign.Center,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap)) {
      SecondaryButton(
          stringResource(R.string.review_proof_reject),
          onReject,
          enabled = state.canReject,
          tag = C.Tag.REVIEW_PROOF_REJECT,
      )
      PrimaryButton(
          stringResource(
              if (approving) R.string.review_proof_approving else R.string.review_proof_approve
          ),
          onApprove,
          enabled = state.canApprove,
          tag = C.Tag.REVIEW_PROOF_APPROVE,
          busy = approving,
      )
    }
  }
}

/** The explorer's name, or "the explorer", to put in a sentence. */
@Composable
private fun explorerInSentence(state: ReviewProofUiState) =
    state.explorerName ?: stringResource(R.string.review_proof_the_explorer)

private val AVATAR_SIZE = 44.dp
private val AVATAR_STYLE = AroundTextStyles.AvatarSmall.copy(fontSize = 16.sp)
private val NAME_GAP = 2.dp
private val TAG_VERTICAL_PADDING = 5.dp
private val PHOTO_HEIGHT = 300.dp
private val PLACEHOLDER_ICON_SIZE = 40.dp
private val ZOOM_ICON_SIZE = 16.dp
private val ANSWER_STYLE = AroundTextStyles.BodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp)

// Internal, not private, so a test can draw them: the previews are code too
@Preview(name = "Photo - Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Photo - Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun ReviewProofPhotoPreview() {
  ReviewProofPreview(PREVIEW_STATE)
}

@Preview(name = "Answer - Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Answer - Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun ReviewProofAnswerPreview() {
  ReviewProofPreview(
      PREVIEW_STATE.copy(
          explorerName = "Nora B.",
          partySize = 1,
          requirements = "Ask the barista for the name of the café cat.",
          proof = Proof.Text("Mochi, the orange one sleeping by the window."),
          photo = null,
      )
  )
}

@Composable
private fun ReviewProofPreview(state: ReviewProofUiState) {
  AroundAppTheme {
    ReviewProofContent(
        state.copy(sentAt = System.currentTimeMillis() - 2 * DateUtils.MINUTE_IN_MILLIS),
        onApprove = {},
        onReject = {},
        onRetry = {},
        onBack = {},
    )
  }
}

private val PREVIEW_STATE =
    ReviewProofUiState(
        status = ReviewProofStatus.LOADED,
        explorerName = "yusuf.s",
        partySize = 4,
        questTitle = "Find the hidden fox",
        requirements = "Order any drink. One photo per party.",
        reward = Reward.FreeItem("croissant", specifics = null),
        proof = Proof.Photo("gs://around/proofs/preview.jpg"),
        photo = ProofPhoto.Failed,
    )
