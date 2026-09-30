package com.hcoss.cordova.filepicker;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native Android implementation of the legacy `chooser.getFiles` bridge.
 *
 * The implementation uses the Storage Access Framework rather than a raw
 * filesystem path. That works with scoped storage and lets a document provider
 * grant durable read access when it supports persistable URI permissions.
 */
public final class Chooser extends CordovaPlugin {
    private static final String ACTION_GET_FILES = "getFiles";
    private static final int PICK_FILES_REQUEST_CODE = 9157;
    private static final String RESULT_CANCELED = "RESULT_CANCELED";
    private static final String PICKER_IN_PROGRESS = "PICKER_IN_PROGRESS";

    /** Only one Cordova activity-result callback may be active at a time. */
    private CallbackContext pendingCallback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (!ACTION_GET_FILES.equals(action)) {
            return false;
        }

        if (pendingCallback != null) {
            callbackContext.error(PICKER_IN_PROGRESS);
            return true;
        }

        String accept = "*/*";
        if (args != null && args.length() > 0) {
            accept = args.optString(0, "*/*");
        }
        if (accept == null || accept.trim().isEmpty()) {
            accept = "*/*";
        }

        try {
            openPicker(callbackContext, accept);
        } catch (Exception exception) {
            callbackContext.error("Unable to open file picker: " + exception.getMessage());
        }
        return true;
    }

    /**
     * Starts the system DocumentsUI picker. ACTION_OPEN_DOCUMENT is preferred
     * over ACTION_GET_CONTENT because it can grant a persistable read URI.
     * Providers that do not offer persistent permissions still return a normal
     * URI, matching the old plugin's observable API.
     */
    private void openPicker(CallbackContext callbackContext, String accept) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, splitMimeTypes(accept));
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        // Keep the old plugin's local-only preference. It is a request rather
        // than a guarantee; individual document providers may interpret it.
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        // Request read access now and persistent access for a future app run.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        pendingCallback = callbackContext;
        PluginResult waiting = new PluginResult(PluginResult.Status.NO_RESULT);
        waiting.setKeepCallback(true);
        callbackContext.sendPluginResult(waiting);

        Intent chooser = Intent.createChooser(intent, "Select File");
        cordova.startActivityForResult(this, chooser, PICK_FILES_REQUEST_CODE);
    }

    /** Splits the public comma-separated accept string into Android MIME types. */
    private String[] splitMimeTypes(String accept) {
        String[] candidates = accept.split(",");
        List<String> mimeTypes = new ArrayList<String>();
        for (String candidate : candidates) {
            String value = candidate == null ? "" : candidate.trim();
            if (!value.isEmpty()) {
                mimeTypes.add(value);
            }
        }

        if (mimeTypes.isEmpty()) {
            mimeTypes.add("*/*");
        }
        return mimeTypes.toArray(new String[0]);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILES_REQUEST_CODE || pendingCallback == null) {
            return;
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            finishWithError(RESULT_CANCELED);
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            finishWithError("File picker failed with result code " + resultCode + ".");
            return;
        }

        try {
            JSONArray files = new JSONArray();
            if (data != null && data.getClipData() != null) {
                for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                    Uri uri = data.getClipData().getItemAt(index).getUri();
                    if (uri != null) {
                        files.put(describeUri(uri));
                    }
                }
            } else if (data != null && data.getData() != null) {
                files.put(describeUri(data.getData()));
            }

            if (files.length() == 0) {
                finishWithError("File URI was null.");
                return;
            }

            // The legacy JavaScript bridge expects a JSON string, not a native
            // JSONArray result. Keeping that wire format avoids breaking apps.
            finishWithSuccess(files.toString());
        } catch (Exception exception) {
            finishWithError("Failed to read selected file: " + exception.getMessage());
        }
    }

    /** Creates the exact file descriptor shape exposed by the old plugin. */
    private JSONObject describeUri(Uri uri) throws JSONException {
        ContentResolver resolver = cordova.getActivity().getContentResolver();
        takePersistableReadPermission(resolver, uri);

        JSONObject file = new JSONObject();
        file.put("mediaType", readMimeType(resolver, uri));
        file.put("name", readDisplayName(resolver, uri));
        file.put("uri", uri.toString());
        return file;
    }

    /**
     * A provider is allowed to decline persistable permission. That is not an
     * error: the user just selected a valid file and callers must still receive
     * the same URI shape they received from the previous plugin.
     */
    private void takePersistableReadPermission(ContentResolver resolver, Uri uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Transient read access remains available for providers that do not
            // implement persistable permissions.
        }
    }

    /** Reads a provider display name first, then uses a safe URI fallback. */
    private String readDisplayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && !cursor.isNull(column)) {
                    return cursor.getString(column);
                }
            }
        } catch (Exception ignored) {
            // Some document providers intentionally expose no metadata query.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null || lastSegment.isEmpty() ? "unknown" : lastSegment;
    }

    /** Uses the provider MIME type and falls back to the filename extension. */
    private String readMimeType(ContentResolver resolver, Uri uri) {
        String mimeType = resolver.getType(uri);
        if (mimeType != null && !mimeType.isEmpty()) {
            return mimeType;
        }

        String name = readDisplayName(resolver, uri);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            // MIME extensions are ASCII protocol tokens; use ROOT so a device
            // locale (for example Turkish) cannot alter the lookup.
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (fromExtension != null && !fromExtension.isEmpty()) {
                return fromExtension;
            }
        }
        return "application/octet-stream";
    }

    private void finishWithSuccess(String payload) {
        CallbackContext callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) {
            callback.success(payload);
        }
    }

    private void finishWithError(String message) {
        CallbackContext callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) {
            callback.error(message);
        }
    }

    @Override
    public void onReset() {
        // A WebView navigation invalidates the JavaScript callback. Clear it so
        // a later picker request cannot accidentally resolve the old page.
        pendingCallback = null;
    }
}
