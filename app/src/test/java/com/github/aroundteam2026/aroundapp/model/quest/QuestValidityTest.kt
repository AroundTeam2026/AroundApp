// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.rewardExpiringAt
import com.github.aroundteam2026.aroundapp.model.testQuest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests which quests explorers can take now, and when that next changes. */
class QuestValidityTest {

  private val now = 10_000L

  @Test
  fun anActiveQuestWithARewardThatNeverExpiresIsValid() {
    assertTrue(testQuest(reward = Reward.Other("Free coffee", expiresAt = null)).isValidAt(now))
  }

  @Test
  fun anActiveQuestWithoutARewardIsValid() {
    assertTrue(testQuest(reward = null).isValidAt(now))
  }

  @Test
  fun anActiveQuestWhoseRewardExpiresLaterIsValid() {
    assertTrue(testQuest(reward = rewardExpiringAt(now + 1)).isValidAt(now))
  }

  @Test
  fun aRewardStopsBeingValidAtItsExpiryTime() {
    // Reward.expiresAt is when it "stops being valid", so that instant is already too late
    assertFalse(testQuest(reward = rewardExpiringAt(now)).isValidAt(now))
    assertFalse(testQuest(reward = rewardExpiringAt(now - 1)).isValidAt(now))
  }

  @Test
  fun draftAndArchivedQuestsAreNeverValid() {
    // Explorers only see active quests, whatever the reward says
    listOf(QuestStatus.DRAFT, QuestStatus.ARCHIVED).forEach {
      assertFalse("$it", testQuest(status = it, reward = null).isValidAt(now))
    }
  }

  @Test
  fun theNextChangeIsTheSoonestRewardExpiryStillToCome() {
    val quests =
        listOf(
            testQuest(id = "later", reward = rewardExpiringAt(now + 500)),
            testQuest(id = "sooner", reward = rewardExpiringAt(now + 100)),
            testQuest(id = "never", reward = Reward.Other("Free coffee")),
            testQuest(id = "none", reward = null),
        )

    assertEquals(now + 100, quests.nextExpiryAfter(now))
  }

  @Test
  fun rewardsThatAlreadyExpiredDoNotCountAsTheNextChange() {
    val quests =
        listOf(
            testQuest(id = "expired", reward = rewardExpiringAt(now - 100)),
            testQuest(id = "expiring now", reward = rewardExpiringAt(now)),
            testQuest(id = "later", reward = rewardExpiringAt(now + 300)),
        )

    assertEquals(now + 300, quests.nextExpiryAfter(now))
  }

  @Test
  fun questsThatAreNotActiveDoNotCountAsTheNextChange() {
    // A draft's reward expiring changes nothing on the map
    val quests =
        listOf(
            testQuest(id = "draft", status = QuestStatus.DRAFT, reward = rewardExpiringAt(now + 1)),
            testQuest(id = "active", reward = rewardExpiringAt(now + 900)),
        )

    assertEquals(now + 900, quests.nextExpiryAfter(now))
  }

  @Test
  fun thereIsNoNextChangeWhenNoRewardWillExpire() {
    val quests = listOf(testQuest(reward = null), testQuest(reward = Reward.Other("Coffee")))

    assertNull(quests.nextExpiryAfter(now))
    assertNull(emptyList<Quest>().nextExpiryAfter(now))
  }
}
