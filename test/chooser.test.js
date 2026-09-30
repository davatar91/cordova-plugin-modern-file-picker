'use strict';

var assert = require('node:assert/strict');
var test = require('node:test');

var chooserPath = require.resolve('../www/chooser');

/** Loads the bridge with a small Cordova mock for each independent test. */
function loadChooser(exec) {
    global.cordova = {exec: exec};
    delete require.cache[chooserPath];
    return require('../www/chooser');
}

test('uses the legacy default MIME filter when accept is omitted', async function () {
    var receivedArgs;
    var chooser = loadChooser(function (success, failure, service, action, args) {
        receivedArgs = {service: service, action: action, args: args};
        success('[]');
    });

    assert.deepEqual(await chooser.getFiles(), []);
    assert.deepEqual(receivedArgs, {
        service: 'Chooser',
        action: 'getFiles',
        args: ['*/*']
    });
});

test('removes whitespace from comma-separated legacy MIME filters', async function () {
    var receivedFilter;
    var chooser = loadChooser(function (success, failure, service, action, args) {
        receivedFilter = args[0];
        success('[]');
    });

    await chooser.getFiles(' image/*, application/pdf ');
    assert.equal(receivedFilter, 'image/*,application/pdf');
});

test('parses the native JSON string into the legacy descriptor array', async function () {
    var chooser = loadChooser(function (success) {
        success('[{"mediaType":"application/pdf","name":"a.pdf","uri":"content://a"}]');
    });

    assert.deepEqual(await chooser.getFiles(), [
        {mediaType: 'application/pdf', name: 'a.pdf', uri: 'content://a'}
    ]);
});

test('accepts an already-decoded array from a Cordova bridge implementation', async function () {
    var chooser = loadChooser(function (success) {
        success([{mediaType: 'image/png', name: 'a.png', uri: 'content://a'}]);
    });

    assert.deepEqual(await chooser.getFiles(), [
        {mediaType: 'image/png', name: 'a.png', uri: 'content://a'}
    ]);
});

test('keeps optional success and failure callback compatibility', async function () {
    var chooser = loadChooser(function (success) {
        success('[]');
    });
    var callbackValue;

    await chooser.getFiles(undefined, function (files) {
        callbackValue = files;
    });

    // Let the Promise .then registered for the callback run.
    await Promise.resolve();
    assert.deepEqual(callbackValue, []);
});

test('propagates the legacy cancellation failure unchanged', async function () {
    var chooser = loadChooser(function (success, failure) {
        failure('RESULT_CANCELED');
    });

    await assert.rejects(chooser.getFiles(), function (error) {
        return error === 'RESULT_CANCELED';
    });
});

test('keeps the optional failure callback compatible with cancellation', async function () {
    var chooser = loadChooser(function (success, failure) {
        failure('RESULT_CANCELED');
    });
    var callbackError;

    await assert.rejects(
        chooser.getFiles(undefined, undefined, function (error) {
            callbackError = error;
        }),
        function (error) {
            return error === 'RESULT_CANCELED';
        }
    );

    // Let the Promise .catch registered for the callback run.
    await Promise.resolve();
    assert.equal(callbackError, 'RESULT_CANCELED');
});
