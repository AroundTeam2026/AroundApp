// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar

/**
 * What a venue's page shows: its header and the quests explorers can take there.
 *
 * The header and [quests] are only meaningful once [status] is [VenuePageStatus.LOADED]; before
 * that they keep their defaults.
 *
 * @property status Whether the page is loading, shown, or why it can't be.
 * @property venueName The venue's name, shown in the header.
 * @property avatar What the header shows for the venue, or null until the venue is loaded.
 * @property address The venue's street address, or null if it gave none.
 * @property quests The venue's valid quests, in the order the page lists them.
 * @property selectedQuestId Id of the quest the explorer picked to accept, or null when none is
 *   picked.
 * @property detailQuestId Id of the quest whose details are open in a sheet, or null when none is.
 * @property acceptedParties For each quest of this venue the explorer has accepted, how many people
 *   its party has. A pending or approved reservation counts; a rejected or cancelled one does not.
 * @property acceptingQuestIds Ids of the quests being accepted right now.
 * @property failedQuestIds Ids of the quests whose last accept failed: the write could not be
 *   issued, or the server refused it afterwards.
 * @property acceptAvailable False once the explorer's reservations can't be read, since then the
 *   page can't tell what they accepted and so doesn't book anything.
 */
data class VenuePageUiState(
    val status: VenuePageStatus = VenuePageStatus.LOADING,
    val venueName: String = "",
    val avatar: VenueAvatar? = null,
    val address: String? = null,
    val quests: List<VenuePageQuest> = emptyList(),
    val selectedQuestId: String? = null,
    val detailQuestId: String? = null,
    val acceptedParties: Map<String, Int> = emptyMap(),
    val acceptingQuestIds: Set<String> = emptySet(),
    val failedQuestIds: Set<String> = emptySet(),
    val acceptAvailable: Boolean = true,
) {
  /** The picked quest, or null when none is picked or it is no longer in [quests]. */
  val selectedQuest: VenuePageQuest?
    get() = selectedQuestId?.let { id -> quests.find { it.questId == id } }

  /** The quest whose details are open, or null when none are or it is no longer in [quests]. */
  val detailQuest: VenuePageQuest?
    get() = detailQuestId?.let { id -> quests.find { it.questId == id } }

  /** How accepting stands for the picked quest; [AcceptStatus.NONE] when none is picked. */
  val acceptStatus: AcceptStatus
    get() {
      val id = selectedQuestId ?: return AcceptStatus.NONE
      return when {
        id in acceptedParties -> AcceptStatus.ACCEPTED
        id in acceptingQuestIds -> AcceptStatus.ACCEPTING
        id in failedQuestIds -> AcceptStatus.FAILED
        else -> AcceptStatus.NONE
      }
    }

  /**
   * How many more people the picked quest needs: its minimum party size less the party's size,
   * never below 0. Until the quest is accepted the party is the explorer alone. 0 when none is
   * picked.
   */
  val morePeopleNeeded: Int
    get() {
      val quest = selectedQuest ?: return 0
      val party = acceptedParties[quest.questId] ?: SOLO_PARTY
      return (quest.minPartySize - party).coerceAtLeast(0)
    }

  /**
   * This page listing [quests] instead. A picked quest that isn't among them is unpicked, so it
   * isn't picked again if it comes back, as when its venue extends an expired reward; likewise, its
   * details close.
   */
  fun withQuests(quests: List<VenuePageQuest>): VenuePageUiState =
      copy(
          quests = quests,
          selectedQuestId = selectedQuestId?.takeIf { id -> quests.any { it.questId == id } },
          detailQuestId = detailQuestId?.takeIf { id -> quests.any { it.questId == id } },
      )

  /**
   * This page with the details of the quest [questId] open; a quest it doesn't list changes
   * nothing.
   */
  fun withQuestDetails(questId: String): VenuePageUiState =
      if (quests.any { it.questId == questId }) copy(detailQuestId = questId) else this

  /**
   * This page once the explorer tapped the quest [questId]: picked, or unpicked if it was picked. A
   * quest the page doesn't list changes nothing.
   */
  fun withQuestTapped(questId: String): VenuePageUiState =
      when {
        selectedQuestId == questId -> copy(selectedQuestId = null)
        quests.none { it.questId == questId } -> this
        else -> copy(selectedQuestId = questId)
      }
}

/** How accepting the picked quest stands. */
enum class AcceptStatus {
  /** Not accepted, and not being accepted. */
  NONE,
  /** The reservation is being created. */
  ACCEPTING,
  /** The explorer has a pending or approved reservation for the quest. */
  ACCEPTED,
  /** The last try failed; accepting again retries. */
  FAILED,
}

/** The party of an explorer who has not formed one yet: themself. */
private const val SOLO_PARTY = 1

/** Where the venue page stands. */
enum class VenuePageStatus {
  /** The venue and its quests are being fetched. */
  LOADING,
  /** The venue was found; the page shows it, even when it has no quests. */
  LOADED,
  /** No venue has this id. */
  NOT_FOUND,
  /** The venue or its quests could not be fetched; trying again may work. */
  FAILED,
}

/**
 * One quest as the venue page lists it.
 *
 * @property questId Id of the quest.
 * @property title The quest's title.
 * @property description What the quest is about.
 * @property requirements What explorers must do to complete it.
 * @property proofType The kind of proof explorers submit.
 * @property minPartySize How many people the quest needs at least.
 * @property reward The quest's reward, or null when it has none.
 */
data class VenuePageQuest(
    val questId: String,
    val title: String,
    val description: String,
    val requirements: String,
    val proofType: ProofType,
    val minPartySize: Int,
    val reward: Reward?,
)
