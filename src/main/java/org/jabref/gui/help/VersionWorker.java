package org.jabref.gui.help;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jabref.gui.DialogService;
import org.jabref.gui.util.BackgroundTask;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.Version;
import org.jabref.preferences.InternalPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This worker checks if there is a new version of JabRef available. If there is it will display a dialog to the user
 * offering him multiple options to proceed (see changelog, go to the download page, ignore this version, and remind
 * later).
 *
 * If the versions check is executed manually and this is the latest version it will also display a dialog to inform the
 * user.
 */
public class VersionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionWorker.class);

    /**
     * The current version of the installed JabRef
     */
    private final Version installedVersion;

    private final DialogService dialogService;
    private final TaskExecutor taskExecutor;
    private final InternalPreferences internalPreferences;

    public VersionWorker(Version installedVersion,

                         DialogService dialogService,
                         TaskExecutor taskExecutor,
                         InternalPreferences internalPreferences) {
        this.installedVersion = Objects.requireNonNull(installedVersion);
        this.dialogService = Objects.requireNonNull(dialogService);
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
        this.internalPreferences = internalPreferences;
    }

    /**
     * Returns a newer version excluding any non-stable versions, except if the installed one is unstable too. If no
     * newer version was found, then an empty optional is returned.
     */
    private Optional<Version> getNewVersion() throws IOException {
        List<Version> availableVersions = Version.getAllAvailableVersions();
        return installedVersion.shouldBeUpdatedTo(availableVersions);
    }

    public void checkForNewVersionAsync() {
        if (!internalPreferences.isVersionCheckEnabled()) {
            return;
        }

        BackgroundTask.wrap(this::getNewVersion)
                      .onSuccess(version -> showUpdateInfo(version, true))
                      .onFailure(exception -> showConnectionError(exception, true))
                      .executeWith(taskExecutor);
    }

    public void checkForNewVersionDelayed() {
        if (!internalPreferences.isVersionCheckEnabled()) {
            return;
        }

        BackgroundTask.wrap(this::getNewVersion)
                      .onSuccess(version -> showUpdateInfo(version, false))
                      .onFailure(exception -> showConnectionError(exception, false))
                      .scheduleWith(taskExecutor, 30, TimeUnit.SECONDS);
    }

    /**
     * Prints the connection problem to the status bar and shows a dialog if it was executed manually
     */
    private void showConnectionError(Exception exception, boolean manualExecution) {
        if (manualExecution) {
            String couldNotConnect = Localization.lang("Could not connect to the update server.");
            String tryLater = Localization.lang("Please try again later and/or check your network connection.");
            dialogService.showErrorDialogAndWait(Localization.lang("Error"), couldNotConnect + "\n" + tryLater, exception);
        }
        LOGGER.debug("Could not connect to the update server.", exception);
    }

    /**
     * Prints up-to-date to the status bar (and shows a dialog it was executed manually) if there is now new version.
     * Shows a "New Version" Dialog to the user if there is.
     */
    private void showUpdateInfo(Optional<Version> newerVersion, boolean manualExecution) {
        // no new version could be found, only respect the ignored version on automated version checks
        if (newerVersion.isEmpty() || (newerVersion.get().equals(internalPreferences.getIgnoredVersion()) && !manualExecution)) {
            if (manualExecution) {
                dialogService.notify(Localization.lang("JabRef is up-to-date."));
            }
        } else {
            // notify the user about a newer version
            if (dialogService.showCustomDialogAndWait(new NewVersionDialog(installedVersion, newerVersion.get(), dialogService)).orElse(true)) {
                internalPreferences.setIgnoredVersion(newerVersion.get());
            }
        }
    }
}
