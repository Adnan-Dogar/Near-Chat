# Near-Chat Online · Android MVP

A new **online-first** direction for Near-Chat built with Kotlin, Firebase Authentication, and Cloud Firestore. It intentionally replaces the previous offline Nearby messaging home screen in this branch while preserving the old branch's source history.

## Included

- Sign up and sign in with email and password
- Share a 12-character friend code; search by code and start a one-to-one conversation
- Create small groups of up to 8 members by friend codes
- Live updating chat lists and messages (app in foreground)
- Basic profile status and sign out
- Firestore security rules and GitHub Actions APK build workflow

## Setup

Read **[SETUP_FIREBASE.md](SETUP_FIREBASE.md)**. Creating the APK is not sufficient; the backend must be configured with Firebase settings and security rules.

## Status and safety

This is a development prototype. **Not end-to-end encrypted**, no notifications, no media sharing/calls/stories/social feed yet, and not tested on physical phones. Do not treat this as a ready-to-publish WhatsApp/Facebook replacement. Before public launch, implement moderation, blocking, reporting, secure account recovery, privacy policy, safe media handling, abuse prevention, accessibility, and load testing.
