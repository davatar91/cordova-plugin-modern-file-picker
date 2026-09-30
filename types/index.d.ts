/** A file selected by the native operating system picker. */
export interface ChosenFile {
    mediaType: string;
    name: string;
    uri: string;
}

export interface Chooser {
    /**
     * Opens the native file picker and resolves to one or more selected files.
     * Cancellation rejects with the legacy string `RESULT_CANCELED`.
     */
    getFiles(
        accept?: string,
        successCallback?: (files: ChosenFile[]) => void,
        failureCallback?: (error: unknown) => void
    ): Promise<ChosenFile[]>;
}

declare const chooser: Chooser;

export default chooser;

declare global {
    const chooser: Chooser;
}
