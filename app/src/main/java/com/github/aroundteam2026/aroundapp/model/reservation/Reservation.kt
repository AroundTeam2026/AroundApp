// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/**
 * A party's booking of a time slot to take a quest, which the venue approves or rejects. The party
 * isn't stored here: each explorer's membership is a `PartyMember`, read through `PartyRepository`.
 *
 * @property id Document id, assigned by the repository.
 * @property questId Id of the quest being booked.
 * @property venueId Id of the venue that owns the quest, equal to the venue owner's uid.
 * @property slotStart Start of the booked slot, in epoch milliseconds.
 * @property status Lifecycle state; see [canTransition] for the allowed changes.
 * @property createdAt Creation time, in epoch milliseconds.
 * @property explorerName Display name of the explorer who made the reservation, copied from their
 *   profile when it was made, so the venue can see who reserved without reading profiles it may not
 *   read. It does not follow later renames. Empty for reservations made before it was stored.
 */
data class Reservation(
    val id: String,
    val questId: String,
    val venueId: String,
    val slotStart: Long,
    val status: ReservationStatus,
    val createdAt: Long,
    val explorerName: String = "",
)

/**
 * The reservation an explorer makes by accepting the quest [questId] of the venue [venueId]: made
 * by the explorer named [explorerName] in their profile, pending, for a slot starting at
 * [slotStart].
 *
 * Accepting a quest doesn't pick a slot yet, so the venue page passes the time of the tap: by the
 * time the venue reads the reservation, that slot has already started. Until explorers choose a
 * slot, it only dates the request, and a screen showing reservations must not treat a pending one
 * with a past [slotStart] as expired.
 *
 * Pass it to [ReservationRepository.createReservation] with the explorer's uid, which makes them
 * the party's first member and assigns the id and creation time; the ones here are placeholders.
 */
fun newPendingReservation(
    questId: String,
    venueId: String,
    explorerName: String,
    slotStart: Long,
) =
    Reservation(
        id = "",
        questId = questId,
        venueId = venueId,
        slotStart = slotStart,
        status = ReservationStatus.PENDING,
        createdAt = slotStart,
        explorerName = explorerName,
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
 * Whether a reservation with this status still stands: waiting for the venue or accepted. Rejected
 * and cancelled ones are over. Exhaustive, so a new status doesn't compile until it is decided
 * here.
 */
val ReservationStatus.isActive: Boolean
  get() =
      when (this) {
        ReservationStatus.PENDING,
        ReservationStatus.APPROVED -> true
        ReservationStatus.REJECTED,
        ReservationStatus.CANCELLED -> false
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
