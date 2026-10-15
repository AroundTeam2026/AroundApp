<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->
# Google sign-in (#199)

Credential Manager acquires the Google ID token in the UI. `AuthViewModel` passes
it to `AuthRepository.signInWithGoogle`; platform and Firebase types stay out of
ViewModels. Sign-out clears Credential Manager's active session.

## Configuration

Support is limited to debug builds for now, as agreed. Before supporting release
or Play builds, register and verify their actual signing certificates.

1. Enable Google's provider in Firebase Authentication.
2. Register the package and supported debug SHA-1 certificates under the Firebase
   Android app. Fingerprints identify signing keys, not individual phones.
3. Download the refreshed `app/google-services.json`; it needs an Android OAuth
   client and a Web OAuth client. Requests use `default_web_client_id` (the Web
   client). Keep the JSON ignored and update CI's `GOOGLE_SERVICES` secret.
4. If the Firebase API key restricts Android applications, allow the same package
   and signing fingerprints. Maps uses a separate key.

[Firebase setup](https://firebase.google.com/docs/auth/android/google-signin) ·
[Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)

## Registration and validation

Both Google actions clear abandoned registration choices and never guess a role.
An existing saved role wins; an account without a profile chooses Explorer or
Venue through the existing completion screen. Email/password registration keeps
its Explorer and Business/Venue paths.
Cancellation preserves fields, and duplicate requests are blocked. Recreation
cancels the chooser; an exchange already owned by the ViewModel continues.

Run `./gradlew check ktfmtCheck` and `connectedDebugAndroidTest` against the
Firebase emulators. Automated tests inject chooser results, never real accounts.
The Google button uses Google's [official SVG paths](https://developers.google.com/static/identity/images/branding_guideline_sample_nt_rd_sl.svg).

## Same-email provider check

On an A55 debug build, an unverified email/password account was signed in with
Google using the same Gmail address. Signing out and signing back in with the
original password still worked. This records the observed manual result; provider
linking and overwrite behavior may depend on the Firebase account's state.
