// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, getFirestore, Timestamp } from "firebase-admin/firestore";
import { Change } from "firebase-functions/v2/firestore";
import { NotificationMessage, sendToUser } from "../src/notify";
import { notifyVenueProof } from "../src/venueProof";

jest.mock("../src/notify", () => ({ sendToUser: jest.fn() }));

const sent = jest.mocked(sendToUser);

beforeAll(() => {
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
});

beforeEach(() => sent.mockReset());

async function fixture(joinedCount: number, explorerName = "Yusuf S.") {
  const reservationId = randomUUID();
  const questId = randomUUID();
  const db = getFirestore();
  await Promise.all([
    db.collection("reservations").doc(reservationId).set({
      questId,
      venueId: "venue-1",
      explorerName,
      joinedCount,
    }),
    db.collection("quests").doc(questId).set({ title: "Find the hidden fox" }),
  ]);
  return db.collection("completions").doc(reservationId);
}

async function writeAndNotify(
  ref: DocumentReference,
  data: Record<string, unknown>,
) {
  const before = await ref.get();
  await ref.set(data);
  const after = await ref.get();
  await notifyVenueProof.run({
    params: { reservationId: ref.id },
    data: new Change(before, after),
  } as Parameters<typeof notifyVenueProof.run>[0]);
}

function expectMessage(body: string, reservationId: string) {
  expect(sent).toHaveBeenCalledWith("venue-1", {
    title: "Proof to review: Find the hidden fox",
    body,
    data: { kind: "proof", route: "venue_dashboard", reservationId },
  } satisfies NotificationMessage);
}

describe("notifyVenueProof", () => {
  it("notifies the venue for a party's new photo", async () => {
    const ref = await fixture(3);
    await writeAndNotify(ref, {
      status: "PENDING",
      photoUrl: "gs://proofs/photo-1",
      explorerUid: "explorer-1",
      submittedAt: Timestamp.now(),
    });

    expectMessage("Yusuf S.'s party sent a photo.", ref.id);
    expect(sent).toHaveBeenCalledTimes(1);
  });

  it("describes a solo explorer's written answer", async () => {
    const ref = await fixture(1);
    await writeAndNotify(ref, {
      status: "PENDING",
      textAnswer: "The fox is by the door",
      explorerUid: "explorer-1",
      submittedAt: Timestamp.now(),
    });

    expectMessage("Yusuf S. sent a written answer.", ref.id);
  });

  it("notifies for a replacement, including a resubmission of the same proof after rejection", async () => {
    const ref = await fixture(2);
    const original = {
      status: "PENDING",
      photoUrl: "gs://proofs/photo-1",
      explorerUid: "explorer-1",
      submittedAt: Timestamp.fromMillis(1000),
    };
    await ref.set(original);

    // As the app does, a new photo is a new submission, with its own submittedAt
    await writeAndNotify(ref, {
      ...original,
      photoUrl: "gs://proofs/photo-2",
      submittedAt: Timestamp.fromMillis(1500),
    });
    expectMessage("Yusuf S.'s party sent a photo.", ref.id);

    sent.mockClear();
    await writeAndNotify(ref, { ...original, submittedAt: Timestamp.fromMillis(2000) });
    expectMessage("Yusuf S.'s party sent a photo.", ref.id);

    sent.mockClear();
    await ref.set({ ...original, status: "REJECTED", rejectReason: "Try again" });
    await writeAndNotify(ref, original);
    expectMessage("Yusuf S.'s party sent a photo.", ref.id);
  });

  it("ignores the venue's answer and an unrelated pending update", async () => {
    const ref = await fixture(2);
    const proof = {
      status: "PENDING",
      textAnswer: "A fox",
      explorerUid: "explorer-1",
      submittedAt: Timestamp.now(),
    };
    await ref.set(proof);

    await writeAndNotify(ref, { ...proof, status: "REJECTED", rejectReason: "Incomplete" });
    expect(sent).not.toHaveBeenCalled();

    await ref.set(proof);
    await writeAndNotify(ref, { ...proof, metadata: "unrelated" });
    expect(sent).not.toHaveBeenCalled();
  });
});
