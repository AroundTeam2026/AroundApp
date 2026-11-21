// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { logger } from "firebase-functions";
import { sendToUser } from "../src/notify";

describe("sendToUser (stub)", () => {
  afterEach(() => jest.restoreAllMocks());

  it("logs the message instead of sending it", async () => {
    const info = jest.spyOn(logger, "info").mockImplementation(() => undefined);

    await sendToUser("explorer-1", {
      title: "Reservation accepted",
      body: "Café Lumen accepted your reservation",
      data: { kind: "reservation", route: "quests", reservationId: "reservation-1" },
    });

    expect(info).toHaveBeenCalledWith("sendToUser is a stub: nothing was sent", {
      uid: "explorer-1",
      title: "Reservation accepted",
      kind: "reservation",
      route: "quests",
      reservationId: "reservation-1",
    });
  });
});
