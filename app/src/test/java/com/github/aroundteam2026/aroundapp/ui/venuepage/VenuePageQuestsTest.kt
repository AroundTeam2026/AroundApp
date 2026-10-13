// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.rewardExpiringAt
import com.github.aroundteam2026.aroundapp.model.testQuest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [venuePageQuests]: which of a venue's quests the page lists, and in what order. */
class VenuePageQuestsTest {
  private val now = 10_000L

  private fun ids(rows: List<VenuePageQuest>) = rows.map { it.questId }

  @Test
  fun listsTheValidQuestsNewestFirst() {
    val quests =
        listOf(
            testQuest(id = "old", createdAt = 1_000),
            testQuest(id = "new", createdAt = 3_000),
            testQuest(id = "mid", createdAt = 2_000),
        )

    assertEquals(listOf("new", "mid", "old"), ids(venuePageQuests(quests, null, now)))
  }

  @Test
  fun questsCreatedTogetherGoSmallestIdFirst() {
    val quests = listOf(testQuest(id = "b"), testQuest(id = "a"))

    assertEquals(listOf("a", "b"), ids(venuePageQuests(quests, null, now)))
  }

  @Test
  fun theFeaturedQuestComesFirst() {
    val quests =
        listOf(
            testQuest(id = "featured", createdAt = 1_000),
            testQuest(id = "newest", createdAt = 3_000),
        )

    assertEquals(listOf("featured", "newest"), ids(venuePageQuests(quests, "featured", now)))
  }

  @Test
  fun aFeaturedQuestThatIsNotValidIsLeftOut() {
    val quests =
        listOf(
            testQuest(id = "featured", status = QuestStatus.ARCHIVED),
            testQuest(id = "other"),
        )

    assertEquals(listOf("other"), ids(venuePageQuests(quests, "featured", now)))
  }

  @Test
  fun leavesOutDraftsArchivedQuestsAndExpiredRewards() {
    val quests =
        listOf(
            testQuest(id = "draft", status = QuestStatus.DRAFT),
            testQuest(id = "archived", status = QuestStatus.ARCHIVED),
            testQuest(id = "expired", reward = rewardExpiringAt(now)),
            testQuest(id = "expiring", reward = rewardExpiringAt(now + 1)),
            testQuest(id = "noReward", reward = null),
        )

    assertEquals(
        listOf("expiring", "noReward"),
        ids(venuePageQuests(quests, null, now)).sorted(),
    )
  }

  @Test
  fun aRowCarriesTheQuestsFields() {
    val reward = Reward.Discount(10.0, DiscountUnit.PERCENT, specifics = "Drinks")
    val quest = testQuest(id = "q", title = "Find the fox", reward = reward, minPartySize = 3)

    assertEquals(
        listOf(
            VenuePageQuest(
                questId = "q",
                title = "Find the fox",
                description = quest.description,
                requirements = quest.requirements,
                proofType = ProofType.PHOTO,
                minPartySize = 3,
                reward = reward,
            )
        ),
        venuePageQuests(listOf(quest), null, now),
    )
  }
}
