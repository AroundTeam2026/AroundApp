// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { PENDING, readQuest, sendToUser, storedName, UNNAMED_LEADER } from "./notify";

/** The party size of a quest explorers take alone, whose text names no party. */
export const SOLO_PARTY_SIZE = 1;

/**
 * Tells the venue about a new reservation request, as #631's Figma words it: "New request: {quest}"
 * and "{leader} wants to book it for a party of {size}.", or "{leader} wants to book it." for a
 * quest explorers take alone.
 *
 * The size is the quest's minPartySize, the party the leader books it for: a reservation is
 * created with its leader alone (joinedCount 1), and the others join with the party code before
 * the venue can approve it.
 *
 * Only a new reservation triggers it, so a member joining or leaving, or the venue's answer,
 * notifies nobody. The rules only let a reservation be created PENDING; any other is skipped.
 */
export const notifyVenueReservation = onDocumentCreated(
  "reservations/{reservationId}",
  async (event) => {
    const reservation = event.data?.data();
    if (reservation?.status !== PENDING) return;

    const { questId, venueId, explorerName } = reservation;
    const quest = await readQuest(questId);

    const leader = storedName(explorerName) ?? UNNAMED_LEADER;
    const size = quest.get("minPartySize");
    const body =
      typeof size === "number" && size > SOLO_PARTY_SIZE
        ? `${leader} wants to book it for a party of ${size}.`
        : `${leader} wants to book it.`;
    const reservationId = event.params.reservationId;
    await sendToUser(venueId, {
      title: `New request: ${quest.get("title")}`,
      body,
      data: { kind: "reservation", route: "venue_dashboard", reservationId },
    });
  },
);
