// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [QuestRepository] backed by the Firestore `quests` collection.
 *
 * Firestore types stay inside this class. [Location] is stored as a `GeoPoint`, epoch-millisecond
 * times as `Timestamp`s, enums as their names and [Reward] as a nested map whose `type` field names
 * its subtype; a reward that cannot be read back is logged and read as no reward. Null fields are
 * omitted, and the quest id is the document id rather than a stored field. Documents that cannot be
 * mapped back to a [Quest] are logged and skipped: the `observe*` flows leave them out and
 * [getQuest] returns null.
 *
 * @param db Firestore client to read from and write to.
 * @param now Clock used for `createdAt` and `updatedAt`, in epoch milliseconds.
 */
class QuestRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val now: () -> Long = System::currentTimeMillis,
) : QuestRepository {
  private val quests
    get() = db.collection(QUESTS)

  override fun observeActiveQuests(): Flow<List<Quest>> =
      quests.whereEqualTo(STATUS, QuestStatus.ACTIVE.name).observe()

  override fun observeQuestsByVenue(venueId: String): Flow<List<Quest>> =
      quests.whereEqualTo(VENUE_ID, venueId).observe()

  override suspend fun getQuest(questId: String): Quest? =
      quests.document(questId).get().await().toQuest()

  /**
   * Issues the write without waiting for the server, so it also succeeds offline; Firestore sends
   * it once the device is back online. A later server rejection is only logged. Exceptions thrown
   * while issuing the write (e.g. on a terminated client) are returned as a failure.
   */
  override suspend fun createQuest(quest: Quest): Result<String> =
      try {
        val document = quests.document()
        document.set(quest.toFirestore(Timestamp(Date(now())))).addOnFailureListener {
          Log.w(TAG, "Write of quest ${document.id} was rejected", it)
        }
        Result.success(document.id)
      } catch (e: Exception) {
        Result.failure(e)
      }

  /**
   * Turns Firestore's callback-style listener into a Kotlin Flow<List<Quest>>, which ViewModels can
   * collect. Both observeActiveQuests and observeQuestsByVenue use it.
   */
  private fun Query.observe(): Flow<List<Quest>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
      } else if (snapshot != null) {
        trySend(snapshot.documents.mapNotNull { it.toQuest() })
      }
    }
    awaitClose { registration.remove() }
  }

  /** The document fields for this quest, with [time] as both creation and update time. */
  private fun Quest.toFirestore(time: Timestamp): Map<String, Any> = buildMap {
    put(VENUE_ID, venueId)
    put(VENUE_NAME, venueName)
    put(LOCATION, GeoPoint(location.lat, location.lng))
    put(RADIUS_METERS, radiusMeters)
    put(TITLE, title)
    put(DESCRIPTION, description)
    put(REQUIREMENTS, requirements)
    put(PROOF_TYPE, proofType.name)
    put(MIN_PARTY_SIZE, minPartySize)
    reward?.let { put(REWARD, it.toFirestore()) }
    put(STATUS, status.name)
    put(CREATED_AT, time)
    put(UPDATED_AT, time)
  }

  private fun Reward.toFirestore(): Map<String, Any> = buildMap {
    when (val reward = this@toFirestore) {
      is Reward.Discount -> {
        put(REWARD_TYPE, DISCOUNT)
        put(REWARD_AMOUNT, reward.amount)
        put(REWARD_UNIT, reward.unit.name)
        reward.specifics?.let { put(REWARD_SPECIFICS, it) }
      }
      is Reward.FreeItem -> {
        put(REWARD_TYPE, FREE_ITEM)
        put(REWARD_NAME, reward.name)
        reward.specifics?.let { put(REWARD_SPECIFICS, it) }
      }
      is Reward.Other -> {
        put(REWARD_TYPE, OTHER)
        put(REWARD_DESCRIPTION, reward.description)
      }
    }
    expiresAt?.let { put(REWARD_EXPIRES_AT, Timestamp(Date(it))) }
  }

  /** Maps this document to a [Quest], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toQuest(): Quest? {
    if (!exists()) return null
    return try {
      val location = required(LOCATION) { getGeoPoint(it) }
      Quest(
          id = id,
          venueId = required(VENUE_ID) { getString(it) },
          venueName = required(VENUE_NAME) { getString(it) },
          location = Location(location.latitude, location.longitude),
          radiusMeters = required(RADIUS_METERS) { getLong(it) }.toInt(),
          title = required(TITLE) { getString(it) },
          description = required(DESCRIPTION) { getString(it) },
          requirements = required(REQUIREMENTS) { getString(it) },
          proofType = ProofType.valueOf(required(PROOF_TYPE) { getString(it) }),
          minPartySize = required(MIN_PARTY_SIZE) { getLong(it) }.toInt(),
          reward = (get(REWARD) as Map<*, *>?)?.toReward(),
          status = QuestStatus.valueOf(required(STATUS) { getString(it) }),
          createdAt = required(CREATED_AT) { getTimestamp(it) }.toDate().time,
          updatedAt = required(UPDATED_AT) { getTimestamp(it) }.toDate().time,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed quest document $id", e)
      null
    }
  }

  /**
   * Maps this reward map to a [Reward], or returns null if its type is missing or unknown, or a
   * field is missing or malformed, so a bad reward does not hide the whole quest.
   */
  private fun Map<*, *>.toReward(): Reward? =
      try {
        val expiresAt = (this[REWARD_EXPIRES_AT] as Timestamp?)?.toDate()?.time
        when (val type = this[REWARD_TYPE]) {
          DISCOUNT ->
              Reward.Discount(
                  amount = (this[REWARD_AMOUNT] as Number).toDouble(),
                  unit = DiscountUnit.valueOf(this[REWARD_UNIT] as String),
                  specifics = this[REWARD_SPECIFICS] as String?,
                  expiresAt = expiresAt,
              )
          FREE_ITEM ->
              Reward.FreeItem(
                  name = this[REWARD_NAME] as String,
                  specifics = this[REWARD_SPECIFICS] as String?,
                  expiresAt = expiresAt,
              )
          OTHER -> Reward.Other(this[REWARD_DESCRIPTION] as String, expiresAt)
          else -> throw IllegalArgumentException("Unknown reward type $type")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Ignoring malformed reward $this", e)
        null
      }

  /** Reads [field] with [read], throwing if it is absent. */
  private fun <T : Any> required(field: String, read: (String) -> T?): T =
      read(field) ?: throw IllegalArgumentException("Missing field $field")

  private companion object {
    const val TAG = "QuestRepositoryFirestore"
    const val QUESTS = "quests"

    const val VENUE_ID = "venueId"
    const val VENUE_NAME = "venueName"
    const val LOCATION = "location"
    const val RADIUS_METERS = "radiusMeters"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val REQUIREMENTS = "requirements"
    const val PROOF_TYPE = "proofType"
    const val MIN_PARTY_SIZE = "minPartySize"
    const val REWARD = "reward"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"

    const val REWARD_TYPE = "type"
    const val REWARD_AMOUNT = "amount"
    const val REWARD_UNIT = "unit"
    const val REWARD_NAME = "name"
    const val REWARD_SPECIFICS = "specifics"
    const val REWARD_DESCRIPTION = "description"
    const val REWARD_EXPIRES_AT = "expiresAt"

    const val DISCOUNT = "DISCOUNT"
    const val FREE_ITEM = "FREE_ITEM"
    const val OTHER = "OTHER"
  }
}
