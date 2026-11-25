// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

// The tests only ever talk to the Firestore emulator on demo-project, as the app's instrumented
// tests do: a demo- project has no production counterpart, so nothing can reach the real data.
// `firebase emulators:exec` sets both variables itself; these defaults cover an emulator started
// by hand with `firebase emulators:start`.
process.env.GCLOUD_PROJECT ??= "demo-project";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
