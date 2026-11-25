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
  // Unlike transformIgnorePatterns, transform keys see the OS's path separator, so either is matched.
  transform: {
    "^.+\\.ts$": "ts-jest",
    "^.+[/\\\\]node_modules[/\\\\]jose[/\\\\].+\\.js$": ["ts-jest", { tsconfig: { allowJs: true } }],
  },
  transformIgnorePatterns: ["/node_modules/(?!jose/)"],
  // With --coverage (npm run test:coverage): every function's file, measured against the team's
  // 90 % bar. index.ts only wires them up, and the deploy is what runs it.
  collectCoverageFrom: ["src/**/*.ts", "!src/index.ts"],
  coverageThreshold: { global: { lines: 90, branches: 90 } },
};
