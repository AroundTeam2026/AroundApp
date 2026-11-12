// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.createdParty
import com.github.aroundteam2026.aroundapp.model.party.creatorFirst
import com.github.aroundteam2026.aroundapp.model.party.isJoined
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/**
 * A joined member of a party, as the quest details show them.
 *
 * @property name Their display name; empty while their profile can't be read, and the seat then
 *   says "An explorer".
 * @property picture Their profile picture, or null without one or until it arrives.
 * @property isCreator Whether they made the reservation, so they cancel it rather than leave.
 */
data class PartyMemberUi(
    val uid: String,
    val name: String,
    val picture: ImageBitmap? = null,
    val isMe: Boolean = false,
    val isCreator: Boolean = false,
)

/** The joined members of the picked quest's party, its creator first; empty until it is read. */
val VenuePageUiState.members: List<PartyMemberUi>
  get() = selectedQuestId?.let(reservationIds::get)?.let(partyMembers::get).orEmpty()

/**
 * The joined members of each of [parties], by reservation id, in [creatorFirst] order given
 * [reservations]. Names come from [profiles], and pictures from [pictureOf] given a profile's
 * location.
 */
internal fun partyMembersOf(
    reservations: List<Reservation>,
    parties: Map<String, List<PartyMember>>,
    profiles: Map<String, User?>,
    me: String,
    pictureOf: (location: String) -> ImageBitmap?,
): Map<String, List<PartyMemberUi>> {
  val byId = reservations.associateBy { it.id }
  return parties.mapValues { (id, members) ->
    val reservation = byId[id]
    members
        .filter { it.isJoined }
        .creatorFirst(reservation)
        .map { member ->
          val profile = profiles[member.uid]
          PartyMemberUi(
              uid = member.uid,
              name = profile?.displayName.orEmpty(),
              picture = profile?.pictureUrl?.let(pictureOf),
              isMe = member.uid == me,
              isCreator = reservation != null && member.createdParty(reservation),
          )
        }
  }
}

/**
 * [member]'s seat on the party card: their picture, or their initials without one, in a circle of
 * [size], with their name below: "You" for the explorer, and "An explorer" without a name.
 */
@Composable
internal fun MemberSeat(member: PartyMemberUi, size: Dp) {
  val colors = MaterialTheme.colorScheme
  val (container, content) =
      if (member.isMe) colors.primary to colors.onPrimary else colors.tertiary to colors.onTertiary
  Column(
      Modifier.width(MEMBER_WIDTH).testTag(C.Tag.QUEST_DETAIL_MEMBER),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
  ) {
    Box(
        Modifier.size(size).clip(AroundPillShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
      val picture = member.picture
      if (picture != null) {
        // The name below says who it is
        Image(
            picture,
            contentDescription = null,
            Modifier.fillMaxSize().testTag(C.Tag.QUEST_DETAIL_MEMBER_PICTURE),
            contentScale = ContentScale.Crop,
        )
      } else {
        Text(
            initialsOf(member.name),
            Modifier.testTag(C.Tag.QUEST_DETAIL_MEMBER_INITIALS),
            color = content,
            style = AroundTextStyles.Badge,
            maxLines = 1,
        )
      }
    }
    Text(
        when {
          member.isMe -> stringResource(R.string.quest_detail_member_you)
          member.name.isBlank() -> stringResource(R.string.quest_detail_member_unknown)
          else -> member.name
        },
        Modifier.testTag(C.Tag.QUEST_DETAIL_MEMBER_NAME),
        color = colors.onSurface,
        style = AroundTextStyles.Caption,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
  }
}

/** Wide enough for a short name under a seat. */
private val MEMBER_WIDTH = 56.dp
private val PREVIEW_SEAT_SIZE = 36.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun PartyMembersPreview() {
  AroundAppTheme {
    Row(
        Modifier.background(AroundTheme.colors.panel).padding(AroundSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
    ) {
      MemberSeat(PartyMemberUi("bea", "Bea Smith", isCreator = true), PREVIEW_SEAT_SIZE)
      MemberSeat(PartyMemberUi("me", "Ada Lovelace", isMe = true), PREVIEW_SEAT_SIZE)
      MemberSeat(PartyMemberUi("cleo", "Cleo Martin"), PREVIEW_SEAT_SIZE)
    }
  }
}
