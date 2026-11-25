// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

/** @type {import('jest').Config} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  // Points the Admin SDK at the Firestore emulator before any test imports it
  setupFiles: ["<rootDir>/test/emulator-env.ts"],
  // With --coverage (npm run test:coverage): every function's file, measured against the team's
  // 90 % bar. index.ts only wires them up, and the deploy is what runs it.
  collectCoverageFrom: ["src/**/*.ts", "!src/index.ts"],
  coverageThreshold: { global: { lines: 90, branches: 90 } },
};
