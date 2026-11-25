// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { getFirestore } from "firebase-admin/firestore";

/**
 * The uids of the joined members of the party of [reservationId], each once. Membership lives in
 * party_user (PartyUserFirestore.kt), one document per member, not on the reservation; withdrawn
 * members are left out, so they hear nothing more of a party they left.
 *
 * Every notification sent to a party goes through here: the answer to its reservation (#638) and
 * to its proof (#640).
 */
export async function joinedMembers(reservationId: string): Promise<string[]> {
  const members = await getFirestore()
    .collection("party_user")
    .where("reservationId", "==", reservationId)
    .where("status", "==", "JOINED")
    .get();
  const uids = members.docs
    .map((doc) => doc.get("uid"))
    .filter((uid): uid is string => typeof uid === "string" && uid.length > 0);
  return [...new Set(uids)];
}
