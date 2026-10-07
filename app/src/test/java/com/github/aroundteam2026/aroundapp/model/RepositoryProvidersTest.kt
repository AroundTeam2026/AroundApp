// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import com.github.aroundteam2026.aroundapp.model.demo.MapDemoData
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests the repositories the app runs on until it reads Firestore: the demo data, in memory. */
class RepositoryProvidersTest {

  private val now = 1_800_000_000_000L

  @Test
  fun theQuestsAreTheActiveDemoQuests() = runTest {
    val repository = QuestRepositoryProvider.create { now }

    val expected = MapDemoData.quests(now).filter { it.status == QuestStatus.ACTIVE }
    assertEquals(expected.toSet(), repository.observeActiveQuests().first().toSet())
  }

  @Test
  fun theVenuesAreTheDemoVenues() = runTest {
    val repository = VenueRepositoryProvider.create()

    MapDemoData.venues().forEach { assertEquals(it, repository.getVenue(it.id)) }
  }

  @Test
  fun eachNewRepositoryStartsFromTheDemoDataAlone() = runTest {
    // Writes to one repository must not reach another, or tests would leak into each other
    val written = VenueRepositoryProvider.create()
    val venue = testVenue(id = "written-to-another-repository")
    written.createVenue(venue)
    val quests = QuestRepositoryProvider.create { now }
    quests.createQuest(MapDemoData.quests(now).first())

    assertNull(VenueRepositoryProvider.create().getVenue(venue.id))
    assertEquals(
        MapDemoData.quests(now).count { it.status == QuestStatus.ACTIVE },
        QuestRepositoryProvider.create { now }.observeActiveQuests().first().size,
    )
  }
}
