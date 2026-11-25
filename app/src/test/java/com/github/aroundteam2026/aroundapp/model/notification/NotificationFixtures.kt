// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

/**
 * A venue's "proof to review" message (#642), as sendToUser in functions/src/notify.ts puts it in
 * FCM's `data`.
 */
val PROOF_TO_REVIEW_DATA =
    mapOf(
        "title" to "Proof to review: Latte art",
        "body" to "Sam sent a photo.",
        "kind" to "proof",
        "route" to "venue_dashboard",
        "reservationId" to "r1",
    )

/** What [PROOF_TO_REVIEW_DATA] reads as. */
val PROOF_TO_REVIEW =
    AroundNotification(
        "Proof to review: Latte art",
        "Sam sent a photo.",
        "r1",
        NotificationChannelKind.PROOFS_TO_REVIEW,
    )
