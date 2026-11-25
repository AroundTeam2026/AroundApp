// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { initializeApp } from "firebase-admin/app";

initializeApp();

// Every deployed function is exported from here, one line each, e.g.
// export { notifyReservationAnswered } from "./reservationAnswered";
export { notifyVenueReservation } from "./venueReservation";
