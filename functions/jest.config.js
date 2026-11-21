// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

/** @type {import('jest').Config} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  // Points the Admin SDK at the Firestore emulator before any test imports it
  setupFiles: ["<rootDir>/test/emulator-env.ts"],
};
