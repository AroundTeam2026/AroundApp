// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

/**
 * Whether explorers can take this quest at [now], in epoch milliseconds: it is
 * [QuestStatus.ACTIVE], and has no reward, a reward that never expires, or one that expires after
 * [now]. A reward stops being valid at its [Reward.expiresAt].
 */
fun Quest.isValidAt(now: Long): Boolean {
  val expiresAt = reward?.expiresAt
  return status == QuestStatus.ACTIVE && (expiresAt == null || expiresAt > now)
}

/**
 * The soonest time after [now] at which one of these quests stops being valid because its reward
 * expires, or null if none will. Quests that aren't [QuestStatus.ACTIVE] never count.
 */
fun List<Quest>.nextExpiryAfter(now: Long): Long? {
  val active = filter { it.status == QuestStatus.ACTIVE }
  return active.mapNotNull { it.reward?.expiresAt }.filter { it > now }.minOrNull()
}
