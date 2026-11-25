// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

/** @type {import('jest').Config} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  // Points the Admin SDK at the Firestore emulator before any test imports it
  setupFiles: ["<rootDir>/test/emulator-env.ts"],
  // firebase-admin 14 loads jose 6 (through jwks-rsa), which is ES modules only. Node 22 loads it
  // in production, but jest's require doesn't, so ts-jest compiles jose to CommonJS for the tests.
  transform: {
    "^.+\\.ts$": "ts-jest",
    "^.+/node_modules/jose/.+\\.js$": ["ts-jest", { tsconfig: { allowJs: true } }],
  },
  transformIgnorePatterns: ["/node_modules/(?!jose/)"],
};
