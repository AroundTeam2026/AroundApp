// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.testQuest
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateQuestUiStateTest {
  @Test
  fun walkMinutesRoundUpAndNeverFallBelowOne() {
    assertEquals(1, CreateQuestUiState(radiusMeters = 20).walkMinutes)
    assertEquals(1, CreateQuestUiState(radiusMeters = 80).walkMinutes)
    assertEquals(2, CreateQuestUiState(radiusMeters = 81).walkMinutes)
    assertEquals(3, CreateQuestUiState(radiusMeters = 200).walkMinutes)
  }

  @Test
  fun filledWithAQuestShowsItsValuesWithoutErrors() {
    val quest =
        testQuest(proofType = ProofType.TEXT, minPartySize = 4, reward = Reward.Other("A tour"))
            .copy(radiusMeters = 75, exactPartySize = true)
    val form = CreateQuestUiState().filledWith(quest, ZoneOffset.UTC, LocalDate.of(2026, 11, 9))

    assertEquals(quest.title, form.title)
    assertEquals(quest.description, form.description)
    assertEquals(quest.requirements, form.requirements)
    assertEquals(ProofType.TEXT, form.proofType)
    assertEquals(75, form.radiusMeters)
    // 75 m is no preset, so the slider shows
    assertTrue(form.customRadius)
    assertEquals(4, form.minPartySize)
    assertTrue(form.exactPartySize)
    assertEquals(RewardType.OTHER, form.reward.type)
    assertNull(form.titleError)
    assertTrue(form.canSubmit)
  }

  @Test
  fun filledWithAQuestOnAPresetRadiusSelectsThePreset() {
    val form =
        CreateQuestUiState().filledWith(testQuest(), ZoneOffset.UTC, LocalDate.of(2026, 11, 9))

    assertFalse(form.customRadius)
    assertEquals(RadiusPreset.AT_THE_DOOR.meters, form.selectedRadiusPreset)
  }

  @Test
  fun filledWithAQuestKeepsWhereItIs() {
    val today = LocalDate.of(2026, 11, 9)
    val elsewhere = Location(46.5191, 6.5668)
    val placed =
        CreateQuestUiState().filledWith(testQuest(location = elsewhere), ZoneOffset.UTC, today)
    val atVenue = CreateQuestUiState().filledWith(testQuest(), ZoneOffset.UTC, today)

    assertEquals(elsewhere, placed.questLocation)
    assertFalse(placed.atVenue)
    assertNull(atVenue.questLocation)
    assertTrue(atVenue.atVenue)
  }

  // A quest saved under other limits opens, and is saved, within today's
  @Test
  fun filledWithAQuestClampsItsRadiusToTheLimits() {
    val today = LocalDate.of(2026, 11, 9)
    val tooFar = testQuest().copy(radiusMeters = QuestLimits.MAX_RADIUS_METERS + 1)
    val tooClose = testQuest().copy(radiusMeters = QuestLimits.MIN_RADIUS_METERS - 1)

    assertEquals(
        QuestLimits.MAX_RADIUS_METERS,
        CreateQuestUiState().filledWith(tooFar, ZoneOffset.UTC, today).radiusMeters,
    )
    assertEquals(
        QuestLimits.MIN_RADIUS_METERS,
        CreateQuestUiState().filledWith(tooClose, ZoneOffset.UTC, today).radiusMeters,
    )
  }
}
