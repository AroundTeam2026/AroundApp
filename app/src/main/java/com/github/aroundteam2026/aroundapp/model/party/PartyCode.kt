// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlin.random.Random

/**
 * The characters of a party code: the digits 2 to 9 and the capital letters but I, L and O, which
 * are easy to mistake for 1, 0 and one another. The same set as `isPartyCode` in firestore.rules.
 */
const val PARTY_CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** How many characters a party code has. */
const val PARTY_CODE_LENGTH = 6

/**
 * How many codes creating a reservation draws at most before giving up, each new one after the
 * previous was already taken. With about 887 million codes, a second draw is already rare.
 */
const val MAX_PARTY_CODE_ATTEMPTS = 5

/**
 * The message of the [IllegalStateException] creating a reservation fails with when every one of
 * its [MAX_PARTY_CODE_ATTEMPTS] codes was taken.
 */
const val EVERY_PARTY_CODE_TAKEN = "Every party code drawn was taken"

/** A new party code of [PARTY_CODE_LENGTH] characters drawn from [PARTY_CODE_ALPHABET]. */
fun newPartyCode(random: Random = Random.Default): String = buildString {
  repeat(PARTY_CODE_LENGTH) {
    append(PARTY_CODE_ALPHABET[random.nextInt(PARTY_CODE_ALPHABET.length)])
  }
}

/** Whether [code] is a party code as stored: [PARTY_CODE_LENGTH] characters of the alphabet. */
fun isPartyCode(code: String): Boolean =
    code.length == PARTY_CODE_LENGTH && code.all { it in PARTY_CODE_ALPHABET }

/**
 * The party code an explorer typed, as stored: in capitals and without whitespace, so `7kq m42`
 * becomes `7KQM42`. Null if what remains is not a party code.
 */
fun normalizePartyCode(typed: String): String? =
    typed.filterNot { it.isWhitespace() }.uppercase().takeIf(::isPartyCode)

/**
 * No reservation has the party code [code]: the code as stored, or the text as typed if it is not a
 * party code at all. A [NoSuchElementException], which also means a missing reservation elsewhere
 * (e.g. `ReservationRepository.updateStatus`), so catch this type first to tell the two apart.
 */
class PartyCodeNotFoundException(val code: String) :
    NoSuchElementException("No party has the code $code")
