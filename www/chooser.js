'use strict';

/**
 * Normalizes the legacy accept argument before it crosses the Cordova bridge.
 *
 * The previous plugin accepted a comma-separated MIME list and stripped all
 * whitespace. Keeping that behavior matters because existing applications
 * commonly pass values such as "image/*, application/pdf".
 *
 * @param {unknown} accept A MIME type or comma-separated MIME type list.
 * @returns {string} A non-empty MIME filter suitable for native code.
 */
function normalizeAccept(accept) {
    if (typeof accept !== 'string') {
        return '*/*';
    }

    var normalized = accept.replace(/\s/g, '');
    return normalized || '*/*';
}

/**
 * Cordova normally gives this plugin a JSON string because both native
 * implementations deliberately serialize the result. Accepting an already
 * decoded array too makes the JavaScript bridge resilient to Cordova bridge
 * changes and easy to test in isolation.
 *
 * @param {string|Array<object>} payload Native success payload.
 * @returns {Array<object>} The selected file descriptors.
 */
function parseFiles(payload) {
    if (typeof payload === 'string') {
        return JSON.parse(payload);
    }

    if (Array.isArray(payload)) {
        return payload;
    }

    throw new TypeError('Native file picker returned an invalid result.');
}

/**
 * Displays the operating system file picker.
 *
 * This API intentionally matches cordova-plugin-simple-file-chooser:
 * - the global object is `chooser`;
 * - the function is `getFiles`;
 * - optional success and failure callbacks are supported in addition to the
 *   returned Promise;
 * - a successful selection always resolves to an array, including one file.
 *
 * Cancellation rejects with the legacy string `RESULT_CANCELED` so callers
 * that already handle the old plugin retain their behavior.
 *
 * @param {string=} accept Comma-separated MIME filters, e.g. image/*,application/pdf.
 * @param {Function=} successCallback Legacy success callback.
 * @param {Function=} failureCallback Legacy failure callback.
 * @returns {Promise<Array<{mediaType: string, name: string, uri: string}>>}
 */
function getFiles(accept, successCallback, failureCallback) {
    var result = new Promise(function (resolve, reject) {
        /* global cordova */
        cordova.exec(
            function (payload) {
                try {
                    resolve(parseFiles(payload));
                } catch (error) {
                    reject(error);
                }
            },
            reject,
            'Chooser',
            'getFiles',
            [normalizeAccept(accept)]
        );
    });

    // Preserve the callback form without changing Promise behavior.
    if (typeof successCallback === 'function') {
        result.then(successCallback);
    }
    if (typeof failureCallback === 'function') {
        result.catch(failureCallback);
    }

    return result;
}

module.exports = {
    getFiles: getFiles,

    // Deliberately undocumented helpers used only by the package's unit tests.
    _normalizeAccept: normalizeAccept,
    _parseFiles: parseFiles
};
