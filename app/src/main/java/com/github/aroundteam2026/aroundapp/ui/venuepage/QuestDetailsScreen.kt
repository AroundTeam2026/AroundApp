// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.overview.CancelReservationAction
import com.github.aroundteam2026.aroundapp.ui.overview.slotFormat
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChips
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.create.StillRadiusMap
import com.github.aroundteam2026.aroundapp.ui.quest.currentLocale
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import java.time.Clock
import java.util.Date

/**
 * The whole of [quest], as the Figma frame "Quest details": the venue, the quest's title and chips,
 * then a card for each part of it, over a bar that stays in place to accept the quest or, once it
 * is accepted, to cancel the reservation after asking. The rows only fit part of a quest, so this
 * is where the rest is read.
 *
 * @param state The venue's page, for the venue and how accepting and cancelling stand.
 * @param quest The quest to show.
 * @param onBack Called by the back arrow and system back.
 * @param onAccept Accepts the quest.
 * @param onCancel Cancels the quest's reservation, once the explorer confirmed.
 * @param clock Tells the time zone the reward's expiry is written in.
 */
@Composable
fun QuestDetailsScreen(
    state: VenuePageUiState,
    quest: VenuePageQuest,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
  BackHandler(onBack = onBack)
  Column(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag(C.Tag.QUEST_DETAIL_SCREEN)
  ) {
    VenuePageTopBar(onBack, R.string.quest_detail_title)
    Column(
        Modifier.weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AroundSpacing.ScreenHorizontal,
                end = AroundSpacing.ScreenHorizontal,
                bottom = AroundSpacing.ExtraLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      Header(state, quest)
      if (quest.description.isNotBlank()) {
        Card(R.drawable.ic_info, R.string.quest_detail_about) {
          CardText(quest.description, C.Tag.QUEST_DETAIL_DESCRIPTION)
        }
      }
      if (quest.requirements.isNotBlank()) Requirements(quest.requirements)
      quest.reward?.let { RewardCard(it, clock) }
      PartyCard(quest, joined = state.acceptedParties[quest.questId] ?: SOLO_PARTY)
      ProofCard(quest.proofType)
      RadiusCard(state, quest)
    }
    AcceptQuestBar(state, onAccept) {
      if (state.cancellableReservationId != null) {
        CancelReservationAction(
            state.cancelStatus,
            state.venueName,
            onCancel,
            horizontalAlignment = Alignment.CenterHorizontally,
        )
      }
    }
  }
}

/** The venue's avatar, name and, when it gave one, address, above the quest's title and chips. */
@Composable
private fun Header(state: VenuePageUiState, quest: VenuePageQuest) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(
      Modifier.padding(bottom = AroundSpacing.SegmentGap),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      state.avatar?.let { QuestAvatar(it, venueAvatarStyle().copy(size = AVATAR_SIZE)) }
      Column(verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
        Text(
            state.venueName,
            Modifier.testTag(C.Tag.QUEST_DETAIL_VENUE_NAME),
            color = muted,
            style = AroundTextStyles.Caption,
        )
        state.address?.let { address ->
          IconLine(R.drawable.ic_address_pin, address, C.Tag.QUEST_DETAIL_VENUE_ADDRESS)
        }
      }
    }
    Text(
        quest.title,
        Modifier.semantics { heading() }.testTag(C.Tag.QUEST_DETAIL_TITLE),
        color = MaterialTheme.colorScheme.onSurface,
        style = AroundTextStyles.QuestTitle,
    )
    QuestChips(
        quest.reward,
        quest.minPartySize,
        distanceMeters = null,
        venueQuestChipStyle(),
        exactPartySize = quest.exactPartySize,
    )
  }
}

/** What explorers must do, on the one card that stands out. */
@Composable
private fun Requirements(requirements: String) {
  val colors = AroundTheme.colors
  Card(
      R.drawable.ic_check_circle,
      R.string.quest_detail_to_do,
      container = colors.lavender,
      labelColor = colors.accentText,
  ) {
    CardText(requirements, C.Tag.QUEST_DETAIL_REQUIREMENTS)
  }
}

/** The reward's chip, its details when it has any, and when it expires if it does. */
@Composable
private fun RewardCard(reward: Reward, clock: Clock) {
  Card(R.drawable.ic_gift, R.string.quest_detail_reward) {
    // The party size is on the header's chips and the party card
    QuestChips(
        reward,
        QuestLimits.MIN_PARTY_SIZE,
        distanceMeters = null,
        venueQuestChipStyle(),
    )
    rewardDetails(reward)?.let { CardText(it, C.Tag.QUEST_DETAIL_REWARD_DETAILS) }
    reward.expiresAt?.let { expiresAt ->
      val locale = currentLocale()
      val format = remember(locale, clock.zone) { slotFormat(EXPIRY_SKELETON, locale, clock.zone) }
      IconLine(
          R.drawable.ic_calendar,
          stringResource(R.string.quest_detail_reward_expiry, format.format(Date(expiresAt))),
          C.Tag.QUEST_DETAIL_REWARD_EXPIRY,
      )
    }
  }
}

/**
 * How many people [quest] takes, with a seat for each. A party bigger than the quest's minimum gets
 * a seat for everyone. Past the explorer's own seat, [joined] counts who else is in.
 */
@Composable
private fun PartyCard(quest: VenuePageQuest, joined: Int) {
  val seats = maxOf(quest.minPartySize, joined)
  val rule =
      if (quest.exactPartySize) R.string.quest_detail_party_exactly
      else R.string.quest_detail_party_at_least
  Card(
      R.drawable.ic_user,
      R.string.quest_detail_party,
      trailing = stringResource(rule, quest.minPartySize),
      trailingTag = C.Tag.QUEST_DETAIL_PARTY_RULE,
  ) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
    ) {
      repeat(seats) { seat ->
        when {
          seat == 0 -> OwnSeat()
          seat < joined -> JoinedSeat()
          else -> EmptySeat()
        }
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
      Text(
          stringResource(R.string.quest_detail_party_joined, joined, seats),
          Modifier.testTag(C.Tag.QUEST_DETAIL_PARTY_JOINED),
          color = MaterialTheme.colorScheme.onSurface,
          style = AroundTextStyles.FieldLabel,
      )
      if (!quest.exactPartySize) {
        Text(
            stringResource(R.string.quest_detail_party_bigger),
            Modifier.testTag(C.Tag.QUEST_DETAIL_PARTY_BIGGER),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = AroundTextStyles.Caption,
        )
      }
    }
  }
}

/** The explorer's own seat. */
@Composable
private fun OwnSeat() {
  val colors = MaterialTheme.colorScheme
  Box(
      Modifier.size(SEAT_SIZE).background(colors.primary, AroundPillShape),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        stringResource(R.string.quest_detail_party_me),
        color = colors.onPrimary,
        style = AroundTextStyles.Badge,
    )
  }
}

/** The seat of someone else who joined; the counts are all the page knows of them. */
@Composable
private fun JoinedSeat() {
  val colors = MaterialTheme.colorScheme
  Box(
      Modifier.size(SEAT_SIZE).background(colors.tertiary, AroundPillShape),
      contentAlignment = Alignment.Center,
  ) {
    SeatIcon(colors.onTertiary)
  }
}

/** An open seat: a dashed ring around a muted person. */
@Composable
private fun EmptySeat() {
  val colors = MaterialTheme.colorScheme
  val ring = colors.outline
  Box(
      Modifier.size(SEAT_SIZE)
          .drawBehind {
            val width = SEAT_RING.toPx()
            val dash = SEAT_DASH.toPx()
            drawCircle(
                ring,
                radius = (size.minDimension - width) / 2,
                style =
                    Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
          }
          .testTag(C.Tag.QUEST_DETAIL_SEAT_EMPTY),
      contentAlignment = Alignment.Center,
  ) {
    SeatIcon(colors.onSurfaceVariant)
  }
}

/** The person drawn on a seat, which the party's count already reads out. */
@Composable
private fun SeatIcon(tint: Color) {
  Icon(
      painterResource(R.drawable.ic_user),
      contentDescription = null,
      Modifier.size(SEAT_ICON_SIZE),
      tint = tint,
  )
}

/** The proof explorers send to complete the quest, and what becomes of it. */
@Composable
private fun ProofCard(proofType: ProofType) {
  val (icon, text) =
      when (proofType) {
        ProofType.PHOTO -> R.drawable.ic_proof_photo to R.string.quest_detail_proof_photo
        ProofType.TEXT -> R.drawable.ic_proof_text to R.string.quest_detail_proof_text
      }
  val colors = MaterialTheme.colorScheme
  Card(icon, R.string.quest_detail_proof) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          Modifier.size(PROOF_TILE_SIZE).background(colors.surface, AroundPillShape),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            Modifier.size(PROOF_ICON_SIZE),
            tint = AroundTheme.colors.accentText,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
        Text(
            stringResource(text),
            Modifier.testTag(C.Tag.QUEST_DETAIL_PROOF),
            color = colors.onSurface,
            style = AroundTextStyles.Subtitle,
        )
        Text(
            stringResource(R.string.quest_detail_proof_checked),
            color = colors.onSurfaceVariant,
            style = AroundTextStyles.RowDescription,
        )
      }
    }
  }
}

/** How close to the venue the proof must be sent, on a map once the venue has a location. */
@Composable
private fun RadiusCard(state: VenuePageUiState, quest: VenuePageQuest) {
  Card(
      R.drawable.ic_address_pin,
      R.string.quest_detail_radius,
      trailing = stringResource(R.string.quest_detail_radius_meters, quest.radiusMeters),
      trailingTag = C.Tag.QUEST_DETAIL_RADIUS,
  ) {
    state.venueLocation?.let { location ->
      StillRadiusMap(
          location,
          quest.radiusMeters,
          stringResource(R.string.quest_detail_radius_map, quest.radiusMeters, state.venueName),
          Modifier.testTag(C.Tag.QUEST_DETAIL_RADIUS_MAP),
      )
    }
    Text(
        stringResource(R.string.quest_detail_radius_note, state.venueName),
        Modifier.testTag(C.Tag.QUEST_DETAIL_RADIUS_NOTE),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = AroundTextStyles.RowDescription,
    )
  }
}

/**
 * A rounded card of the screen: [icon] and [label] in small capitals, [trailing] on the end when
 * there is one, tagged [trailingTag], and [content] below.
 */
@Composable
private fun Card(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    container: Color = AroundTheme.colors.panel,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: String? = null,
    trailingTag: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(
      Modifier.fillMaxWidth().clip(CARD_SHAPE).background(container).padding(AroundSpacing.Large),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(icon),
          contentDescription = null,
          Modifier.size(CARD_ICON_SIZE),
          tint = AroundTheme.colors.accentText,
      )
      Text(
          stringResource(label).uppercase(currentLocale()),
          Modifier.weight(1f).semantics { heading() },
          color = labelColor,
          style = AroundTextStyles.Label,
      )
      trailing?.let {
        Text(
            it,
            Modifier.testTag(trailingTag),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = AroundTextStyles.Small,
        )
      }
    }
    content()
  }
}

/** The body of a card, [text], tagged [tag]. */
@Composable
private fun CardText(text: String, tag: String) {
  Text(
      text,
      Modifier.testTag(tag),
      color = MaterialTheme.colorScheme.onSurface,
      style = AroundTextStyles.Description,
  )
}

/** A muted small line of [text] after [icon], tagged [tag]. */
@Composable
private fun IconLine(@DrawableRes icon: Int, text: String, tag: String) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(CARD_ICON_SIZE), muted)
    Text(text, Modifier.testTag(tag), color = muted, style = AroundTextStyles.Caption)
  }
}

/**
 * The whole of [reward], beyond what its one-line chip can fit, or null when there is nothing more:
 * what a discount or free item applies to, or the full description of an other reward.
 */
internal fun rewardDetails(reward: Reward?): String? =
    when (reward) {
      is Reward.Discount -> reward.specifics
      is Reward.FreeItem -> reward.specifics
      is Reward.Other -> reward.description
      null -> null
    }?.takeIf { it.isNotBlank() }

/** A date such as "Dec 31, 2026", in the order of the phone's locale. */
private const val EXPIRY_SKELETON = "yMMMd"

private val AVATAR_SIZE = 44.dp
private val CARD_SHAPE = RoundedCornerShape(20.dp)
private val CARD_ICON_SIZE = 14.dp
private val TEXT_GAP = 2.dp
private val SEAT_SIZE = 36.dp
private val SEAT_ICON_SIZE = 16.dp
private val SEAT_RING = 1.5.dp
private val SEAT_DASH = 4.dp
private val PROOF_TILE_SIZE = 40.dp
private val PROOF_ICON_SIZE = 20.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QuestDetailsPreview() {
  val quest =
      VenuePageQuest(
          questId = "quest",
          title = "Find the hidden fox",
          description =
              "Spot the little ceramic fox hidden somewhere in the café and snap a photo.",
          requirements = "Show the photo at the counter before you leave.",
          proofType = ProofType.PHOTO,
          minPartySize = 4,
          reward = Reward.FreeItem("croissant", specifics = "Plain or almond"),
      )
  AroundAppTheme {
    QuestDetailsScreen(
        VenuePageUiState(
            status = VenuePageStatus.LOADED,
            venueName = "Café Lumière",
            avatar = VenueAvatar.Initials("CL"),
            address = "Rue de Bourg 12, Lausanne",
            quests = listOf(quest),
            selectedQuestId = quest.questId,
            detailQuestId = quest.questId,
        ),
        quest,
        onBack = {},
        onAccept = {},
        onCancel = {},
    )
  }
}
