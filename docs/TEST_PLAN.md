# Release test plan

This plan is the release gate for a version that claims support for iOS 27 or
Android 17. Passing a compiler build alone is not sufficient because each OS
delegates document selection to installed providers.

## Build matrix

| Target | Required build |
| --- | --- |
| Android current | Latest supported `cordova-android`, JDK and Android SDK |
| Android 17 / API 37 | Build and install with the first `cordova-android` release that officially supports API 37 |
| iOS current | Latest `cordova-ios`, current Xcode, deployment target 15.0 or higher |
| iOS 27 | Xcode with the iOS 27 SDK, simulator build and physical-device smoke test |

## JavaScript compatibility

- `chooser` exists after `deviceready`.
- `chooser.getFiles()` uses `*/*`.
- Whitespace is removed from `image/*, application/pdf`.
- Promise and success/failure callbacks both work.
- One chosen file and many chosen files both resolve to an array of
  `{ mediaType, name, uri }`.
- Cancellation rejects with exactly `RESULT_CANCELED`.
- A second call while the picker is shown rejects with `PICKER_IN_PROGRESS`.

## Android devices and providers

Run the following on at least Android API 24, a recent stable API level, and
API 37 when the Cordova platform supports it:

- Files/Downloads: one PDF, two images, MIME filter, unknown extension.
- A provider capable of persistent URI grants: close/reopen the app, then read
  the returned URI.
- A provider without persistent grants: verify selection still succeeds and
  document the provider's access lifetime.
- Press Back/cancel, rotate during selection, and enable Android's “Don't keep
  activities” developer option.

`EXTRA_LOCAL_ONLY` is intentionally retained for compatibility, so cloud-only
documents may be hidden by the provider. Test the providers the host product
actually enables instead of assuming all cloud drives behave alike.

## iOS devices and providers

Run on iOS 15 and iOS 27, including a physical iOS 27 device:

- Files On My iPhone/iPad and iCloud Drive.
- One file, multiple files, cancellation and every advertised MIME filter.
- Verify an imported file remains readable after the picker is dismissed and
  after the app returns from background.
- Verify a filename with non-ASCII characters and a file with an unknown
  extension produce a non-empty name and `application/octet-stream` fallback.

## Migration smoke test

Create a small application that used
`cordova-plugin-simple-file-chooser`, remove that package, add this package,
and make no JavaScript source changes. Test both its Promise and callback
usage. The only expected configuration change for Xcode 27 is an iOS
deployment target of 15.0 or higher.
