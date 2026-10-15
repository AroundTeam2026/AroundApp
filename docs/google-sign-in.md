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

Ordinary sign-in clears abandoned registration choices and never guesses a role.
Google on Create account explicitly registers an Explorer; an existing saved role
wins. The Business/Venue email-registration link remains available. Accounts with
no profile complete registration through #221's router without reauthenticating.
Cancellation preserves fields, and duplicate requests are blocked. Recreation
cancels the chooser; an exchange already owned by the ViewModel continues.

Run `./gradlew check ktfmtCheck` and `connectedDebugAndroidTest` against the
Firebase emulators. Automated tests inject chooser results, never real accounts.
The Google button uses Google's [official G asset](https://developers.google.com/identity/images/g-logo.png).

The user verified real chooser cancellation, sign-in, sign-out and re-sign-in on
the A55 before #221 integration. The refreshed Firebase configuration and CI secret
are installed. On the integrated APK, also verify the Business/Venue link, saved
roles and explicit completion for an account without a profile. That manual
role check remains pending; release signing is outside the agreed scope.
