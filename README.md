# cordova-plugin-modern-file-picker

A modern Android and iOS file picker that is deliberately compatible with the
JavaScript API of
[`cordova-plugin-simple-file-chooser`](https://github.com/hc-oss/cordova-plugin-simple-file-chooser).

It replaces that plugin's deprecated iOS document-picker APIs and Android
`ACTION_GET_CONTENT` flow with platform-supported document-access APIs while
preserving the application-facing `chooser.getFiles(...)` call.

## Compatibility contract

The public bridge is intentionally unchanged:

```js
const files = await chooser.getFiles('image/*,application/pdf');
```

`files` is always an array, including when the person chooses a single file:

```js
[
  {
    mediaType: 'application/pdf',
    name: 'invoice.pdf',
    uri: 'content://...' // Android; normally file://... on iOS
  }
]
```

The optional callback form is also retained:

```js
chooser.getFiles('image/*', onFiles, onError);
```

Cancellation rejects the Promise (or calls `onError`) with the legacy value
`RESULT_CANCELED`. This matches the old plugin's implementation, even though
its README described a different cancellation result.

## Install or migrate

For a new project:

```sh
cordova plugin add cordova-plugin-modern-file-picker
```

To replace the old plugin, change only the installed plugin; application source
code can keep using `chooser.getFiles(...)`:

```sh
cordova plugin rm cordova-plugin-simple-file-chooser
cordova plugin add cordova-plugin-modern-file-picker
```

Do not install both plugins together: both intentionally publish the global
`chooser` object.

## Platform behavior

### Android

The plugin uses the Storage Access Framework with `ACTION_OPEN_DOCUMENT`.
It requests `CATEGORY_OPENABLE`, multiple selection, MIME filtering, read URI
access, and persistable read access. A provider may decline persistent access;
in that case the `uri` remains valid only for the access window granted by that
provider. Applications that need permanent ownership should copy the contents
to their own storage after selection.

`EXTRA_LOCAL_ONLY` is retained to preserve the predecessor's preference for
locally available content. It is a provider hint, not a guarantee.

### iOS

The plugin uses `UniformTypeIdentifiers` and
`UIDocumentPickerViewController(forOpeningContentTypes:asCopy:)`. `asCopy` is
used so the chosen document is copied into the app's sandbox and can be read
without maintaining a security-scoped bookmark.

## Support policy

This package's source is designed for Android API 19+ and iOS 15+. Actual
application availability is bounded by the installed Cordova platform:

- Use the latest `cordova-android` to target current Android SDKs. Android 17
  / API 37 is a release gate: build and device-test it with the Cordova Android
  version that officially supports API 37 before making a shipping claim.
- Use current `cordova-ios` and Xcode for iOS 27. Xcode 27 accepts an iOS 15
  deployment target or newer, so set this in the host application's
  `config.xml` before building with that toolchain:

  ```xml
  <preference name="deployment-target" value="15.0" />
  ```

- The package declares `cordova-android >= 10` and `cordova-ios >= 8`; a
  project can use newer versions without changing this plugin.

No plugin can guarantee that every OEM or cloud document provider grants a
durable URI. The test plan should cover the file providers used by the product.

## MIME filters

Pass one or more MIME types separated by commas:

```js
await chooser.getFiles('image/*');
await chooser.getFiles('application/pdf,text/plain');
await chooser.getFiles(); // equivalent to */*
```

Recognized iOS wildcard groups are `audio/*`, `font/*`, `image/*`, `text/*`,
and `video/*`. Unknown or unsupported MIME types fall back safely to generic
data selection where necessary.

## Development

The JavaScript bridge has dependency-free Node tests:

```sh
npm test
```

Before release, build a real Cordova sample app and test one/multiple files,
cancellation, MIME filters, local and cloud providers, activity recreation on
Android, and Files/iCloud Drive on iOS.
