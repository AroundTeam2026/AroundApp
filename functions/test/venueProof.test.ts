// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { DocumentReference, getFirestore, Timestamp } from "firebase-admin/firestore";
import { Change } from "firebase-functions/v2/firestore";
import { NotificationMessage, sendToUser, UNNAMED_LEADER } from "../src/notify";
import { notifyVenueProof, UNNAMED_PARTY } from "../src/venueProof";
import {
  BLANK_NAME,
  EXPLORER,
  initTestApp,
  LEADER,
  QUEST_TITLE,
  seedReservation,
  VENUE,
} from "./fixtures";

// The sending itself is sendToUser's, tested in notify.test.ts; the shared helpers stay real
jest.mock("../src/notify", () => ({
  ...jest.requireActual("../src/notify"),
  sendToUser: jest.fn(),
}));

const sent = jest.mocked(sendToUser);

const PHOTO = "gs://proofs/photo-1";
const OTHER_PHOTO = "gs://proofs/photo-2";
const ANSWER = "The fox is by the door";
const SOLO = 1;
const PARTY = 3;

beforeAll(initTestApp);

beforeEach(() => sent.mockReset());

/** The completion document of a new reservation, led by [leader] with [joinedCount] members. */
async function completionOf(joinedCount: number, leader: string = LEADER) {
  const reservationId = await seedReservation(joinedCount, leader);
  return getFirestore().collection("completions").doc(reservationId);
}

/** A pending proof sent by [EXPLORER], at [submittedAtMs]. */
function proof(fields: Record<string, unknown>, submittedAtMs = 1000) {
  return {
    status: "PENDING",
    explorerUid: EXPLORER,
    submittedAt: Timestamp.fromMillis(submittedAtMs),
    ...fields,
  };
}

/** Writes [data] to [ref] and runs the trigger on the change, as Firestore would. */
async function writeAndNotify(ref: DocumentReference, data: Record<string, unknown>) {
  const before = await ref.get();
  await ref.set(data);
  const after = await ref.get();
  await notifyVenueProof.run({
    params: { reservationId: ref.id },
    data: new Change(before, after),
  } as Parameters<typeof notifyVenueProof.run>[0]);
}

function expectMessage(body: string, reservationId: string) {
  expect(sent).toHaveBeenCalledTimes(1);
  expect(sent).toHaveBeenCalledWith(VENUE, {
    title: `Proof to review: ${QUEST_TITLE}`,
    body,
    data: { kind: "proof", route: "venue_dashboard", reservationId },
  } satisfies NotificationMessage);
}

describe("notifyVenueProof", () => {
  it("names the party and the photo it sent", async () => {
    const ref = await completionOf(PARTY);
    await writeAndNotify(ref, proof({ photoUrl: PHOTO }));

    expectMessage(`${LEADER}'s party sent a photo.`, ref.id);
  });

  it("names a solo explorer and their written answer", async () => {
    const ref = await completionOf(SOLO);
    await writeAndNotify(ref, proof({ textAnswer: ANSWER }));

    expectMessage(`${LEADER} sent a written answer.`, ref.id);
  });

  it.each([
    [PARTY, `${UNNAMED_PARTY} sent a photo.`],
    [SOLO, `${UNNAMED_LEADER} sent a photo.`],
  ])(
    "without a leader's name, a party of %s reads as in the venue's other texts",
    async (size, body) => {
      const ref = await completionOf(size, BLANK_NAME);
      await writeAndNotify(ref, proof({ photoUrl: PHOTO }));

      expectMessage(body, ref.id);
    },
  );

  it("notifies for a replacement, a resend of the same proof, and a resend after a rejection", async () => {
    const ref = await completionOf(PARTY);
    const original = proof({ photoUrl: PHOTO });
    await ref.set(original);

    // As the app does, a new submission has its own submittedAt
    await writeAndNotify(ref, proof({ photoUrl: OTHER_PHOTO }, 1500));
    expectMessage(`${LEADER}'s party sent a photo.`, ref.id);

    sent.mockClear();
    await writeAndNotify(ref, proof({ photoUrl: PHOTO }, 2000));
    expectMessage(`${LEADER}'s party sent a photo.`, ref.id);

    sent.mockClear();
    await ref.set({ ...original, status: "REJECTED", rejectReason: "Try again" });
    await writeAndNotify(ref, proof({ photoUrl: PHOTO }, 3000));
    expectMessage(`${LEADER}'s party sent a photo.`, ref.id);
  });

  it.each([
    ["PENDING", "REJECTED"],
    ["PENDING", "APPROVED"],
    ["REJECTED", "APPROVED"],
  ])("notifies nobody for the venue's answer, from %s to %s", async (from, to) => {
    const ref = await completionOf(PARTY);
    const sentProof = proof({ textAnswer: ANSWER });
    await ref.set({ ...sentProof, status: from });

    await writeAndNotify(ref, { ...sentProof, status: to, rejectReason: "Incomplete" });

    expect(sent).not.toHaveBeenCalled();
  });

  it("notifies nobody for a pending update that isn't a new submission", async () => {
    const ref = await completionOf(PARTY);
    const sentProof = proof({ textAnswer: ANSWER });
    await ref.set(sentProof);

    await writeAndNotify(ref, { ...sentProof, metadata: "unrelated" });

    expect(sent).not.toHaveBeenCalled();
  });

  it("runs on every write to a completion", () => {
    expect(notifyVenueProof.__endpoint.eventTrigger).toMatchObject({
      eventType: "google.cloud.firestore.document.v1.written",
      eventFilterPathPatterns: { document: "completions/{reservationId}" },
    });
  });
});
