// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/**
 * A party's booking of a time slot to take a quest, which the venue approves or rejects.
 *
 * @property id Document id, assigned by the repository.
 * @property questId Id of the quest being booked.
 * @property venueId Id of the venue that owns the quest, equal to the venue owner's uid.
 * @property explorerUids Uids of the explorers in the party; a solo visit is a party of one.
 * @property slotStart Start of the booked slot, in epoch milliseconds.
 * @property status Lifecycle state; see [canTransition] for the allowed changes.
 * @property createdAt Creation time, in epoch milliseconds.
 */
data class Reservation(
    val id: String,
    val questId: String,
    val venueId: String,
    val explorerUids: List<String>,
    val slotStart: Long,
    val status: ReservationStatus,
    val createdAt: Long,
)

/** Lifecycle state of a reservation. */
enum class ReservationStatus {
  /** Requested by the explorers; waiting for the venue's answer. */
  PENDING,
  /** Accepted by the venue. */
  APPROVED,
  /** Declined by the venue. Final. */
  REJECTED,
  /** Withdrawn by the explorers or the venue. Final. */
  CANCELLED,
}

/**
 * Whether a reservation may change from [from] to [to].
 *
 * A pending reservation can be approved, rejected or cancelled; an approved one can only be
 * cancelled; rejected and cancelled reservations never change again.
 */
fun canTransition(from: ReservationStatus, to: ReservationStatus): Boolean =
    when (from) {
      ReservationStatus.PENDING ->
          to == ReservationStatus.APPROVED ||
              to == ReservationStatus.REJECTED ||
              to == ReservationStatus.CANCELLED
      ReservationStatus.APPROVED -> to == ReservationStatus.CANCELLED
      ReservationStatus.REJECTED,
      ReservationStatus.CANCELLED -> false
    }
