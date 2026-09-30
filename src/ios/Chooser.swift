import Cordova
import Foundation
import UIKit
import UniformTypeIdentifiers

/**
 * Native iOS implementation of the legacy `chooser.getFiles` bridge.
 *
 * It uses UniformTypeIdentifiers and the non-deprecated document-picker
 * initializer available to the package's iOS 15+ deployment baseline. This
 * avoids deprecated MobileCoreServices APIs in current Xcode and iOS SDKs.
 */
@objc(Chooser)
final class Chooser: CDVPlugin, UIDocumentPickerDelegate {
    private static let actionGetFiles = "getFiles"
    private static let resultCanceled = "RESULT_CANCELED"
    private static let pickerInProgress = "PICKER_IN_PROGRESS"

    /** One document picker can be presented by this plugin instance at once. */
    private var pendingCallbackId: String?

    private struct FileInfo: Encodable {
        let mediaType: String
        let name: String
        let uri: String
    }

    @objc(getFiles:)
    func getFiles(command: CDVInvokedUrlCommand) {
        guard pendingCallbackId == nil else {
            sendError(Self.pickerInProgress, callbackId: command.callbackId)
            return
        }

        let accept = normalizedAccept(command.arguments.first as? String)
        pendingCallbackId = command.callbackId

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            guard self.viewController != nil else {
                self.sendError("Unable to present file picker.")
                return
            }

            let picker = self.makePicker(accept: accept)
            picker.delegate = self
            // Multiple selection has existed since iOS 11, our oldest runtime
            // fallback. Keep it enabled to match the old plugin's behavior.
            picker.allowsMultipleSelection = true
            self.viewController.present(picker, animated: true)
        }
    }

    /** Keeps MIME whitespace/default behavior consistent with the JS bridge. */
    private func normalizedAccept(_ accept: String?) -> String {
        guard let accept = accept else { return "*/*" }
        let withoutWhitespace = accept.components(separatedBy: .whitespacesAndNewlines).joined()
        return withoutWhitespace.isEmpty ? "*/*" : withoutWhitespace
    }

    private func makePicker(accept: String) -> UIDocumentPickerViewController {
        let mimeTypes = accept.split(separator: ",").map(String.init)
        return makeModernPicker(mimeTypes: mimeTypes)
    }

    private func makeModernPicker(mimeTypes: [String]) -> UIDocumentPickerViewController {
        let contentTypes = mimeTypes.compactMap(modernType(forMimeType:))
        let allowedTypes = contentTypes.isEmpty ? [UTType.data] : contentTypes

        // asCopy makes the selected item usable in the app's sandbox instead
        // of requiring long-lived security-scoped access to an external file.
        return UIDocumentPickerViewController(
            forOpeningContentTypes: allowedTypes,
            asCopy: true
        )
    }

    private func modernType(forMimeType mimeType: String) -> UTType? {
        switch mimeType.lowercased() {
        case "*/*":
            return UTType.data
        case "audio/*":
            return UTType.audio
        case "font/*":
            return UTType.font
        case "image/*":
            return UTType.image
        case "text/*":
            return UTType.text
        case "video/*":
            return UTType.video
        default:
            // Apple resolves registered MIME types (for example application/pdf)
            // to the associated UTType. Unknown types are intentionally omitted.
            return UTType(mimeType: mimeType)
        }
    }

    func documentPicker(
        _ controller: UIDocumentPickerViewController,
        didPickDocumentsAt urls: [URL]
    ) {
        sendSelectedFiles(urls)
    }

    // Some document providers use the single-selection delegate method even
    // when multiple selection is enabled, so preserve both delegate paths.
    func documentPicker(
        _ controller: UIDocumentPickerViewController,
        didPickDocumentAt url: URL
    ) {
        sendSelectedFiles([url])
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        sendError(Self.resultCanceled)
    }

    /** Serializes successful selections into the legacy JSON-string wire format. */
    private func sendSelectedFiles(_ urls: [URL]) {
        let files = urls.map {
            FileInfo(
                mediaType: mimeType(for: $0),
                name: $0.lastPathComponent.isEmpty ? "unknown" : $0.lastPathComponent,
                uri: $0.absoluteString
            )
        }

        do {
            let data = try JSONEncoder().encode(files)
            guard let json = String(data: data, encoding: .utf8) else {
                sendError("Unable to serialize selected files.")
                return
            }
            sendSuccess(json)
        } catch {
            sendError("Unable to serialize selected files: \(error.localizedDescription)")
        }
    }

    /** Obtains a MIME type without reading the selected file's contents. */
    private func mimeType(for url: URL) -> String {
        guard let type = UTType(filenameExtension: url.pathExtension),
              let mimeType = type.preferredMIMEType else {
            return "application/octet-stream"
        }
        return mimeType
    }

    private func sendSuccess(_ payload: String) {
        guard let callbackId = pendingCallbackId else { return }
        pendingCallbackId = nil
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: payload)
        commandDelegate.send(result, callbackId: callbackId)
    }

    private func sendError(_ message: String, callbackId: String? = nil) {
        let targetCallbackId = callbackId ?? pendingCallbackId
        if callbackId == nil {
            pendingCallbackId = nil
        }
        guard let resolvedCallbackId = targetCallbackId else { return }
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        commandDelegate.send(result, callbackId: resolvedCallbackId)
    }

    override func onReset() {
        // A navigation discards JavaScript callbacks, so do not route a later
        // picker result back to a page that no longer exists.
        pendingCallbackId = nil
    }
}
