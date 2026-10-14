// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
 */
data class VenuePageUiState(
    val status: VenuePageStatus = VenuePageStatus.LOADING,
    val venueName: String = "",
    val avatar: VenueAvatar? = null,
    val address: String? = null,
    val quests: List<VenuePageQuest> = emptyList(),
    val selectedQuestId: String? = null,
    val detailQuestId: String? = null,
) {
  /** The picked quest, or null when none is picked or it is no longer in [quests]. */
  val selectedQuest: VenuePageQuest?
    get() = selectedQuestId?.let { id -> quests.find { it.questId == id } }

  /** The quest whose details are open, or null when none are or it is no longer in [quests]. */
  val detailQuest: VenuePageQuest?
    get() = detailQuestId?.let { id -> quests.find { it.questId == id } }

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
