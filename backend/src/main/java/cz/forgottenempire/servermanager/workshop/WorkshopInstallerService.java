package cz.forgottenempire.servermanager.workshop;

import cz.forgottenempire.servermanager.common.InstallationStatus;
import cz.forgottenempire.servermanager.common.PathsFactory;
import cz.forgottenempire.servermanager.common.ServerType;
import cz.forgottenempire.servermanager.installation.ServerInstallationService;
import cz.forgottenempire.servermanager.steamcmd.ErrorStatus;
import cz.forgottenempire.servermanager.steamcmd.SteamCmdJob;
import cz.forgottenempire.servermanager.steamcmd.SteamCmdService;
import cz.forgottenempire.servermanager.util.FileSystemUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
class WorkshopInstallerService {

    private final PathsFactory pathsFactory;
    private final WorkshopModsService modsService;
    private final SteamCmdService steamCmdService;
    private final ServerInstallationService installationService;

    @Autowired
    public WorkshopInstallerService(
            PathsFactory pathsFactory,
            WorkshopModsService modsService,
            SteamCmdService steamCmdService,
            ServerInstallationService installationService) {
        this.pathsFactory = pathsFactory;
        this.modsService = modsService;
        this.steamCmdService = steamCmdService;
        this.installationService = installationService;
    }

    /**
     * Initiates asynchronous installation or update of workshop mods.
     * Mods are downloaded sequentially one at a time with delays between downloads
     * to avoid rate limiting and ensure each mod is processed independently.
     * Note: This method intentionally does not have @Transactional annotation.
     * The transaction boundary is in handleInstallation instead, which runs asynchronously
     * after SteamCmd completes. This ensures the database session is available when
     * saving mod installation status.
     */
    public void installOrUpdateMods(Collection<WorkshopMod> mods) {
        List<WorkshopMod> modList = List.copyOf(mods);
        log.info("Starting sequential download of {} mods", modList.size());
        installModsSequentiallyWithDelay(modList, 0);
    }

    private void installModsSequentiallyWithDelay(List<WorkshopMod> mods, int currentIndex) {
        if (currentIndex >= mods.size()) {
            log.info("All {} mod downloads completed", mods.size());
            return;
        }

        WorkshopMod mod = mods.get(currentIndex);
        log.info("Downloading mod {}/{}: {} (ID: {})", 
                currentIndex + 1, mods.size(), mod.getName(), mod.getId());

        // Download single mod with retry logic (handled by SteamCmdExecutor)
        steamCmdService.installOrUpdateWorkshopMod(mod)
                .whenComplete((steamCmdJob, throwable) -> {
                    if (throwable != null) {
                        log.error("Unexpected error downloading mod {} (ID {})", mod.getName(), mod.getId(), throwable);
                        // Create an error job manually
                        handleInstallationError(mod, throwable);
                    } else {
                        // Handle normal installation
                        handleInstallation(mod, steamCmdJob);
                    }
                    
                    // Continue with next mod after delay
                    if (currentIndex + 1 < mods.size()) {
                        int delaySeconds = 3; // Configurable delay to avoid rate limiting
                        log.info("Waiting {} seconds before next download ({} remaining)", 
                                delaySeconds, mods.size() - currentIndex - 1);
                        CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)
                                .execute(() -> installModsSequentiallyWithDelay(mods, currentIndex + 1));
                    } else {
                        log.info("All mod downloads completed");
                    }
                });
    }

    @Transactional
    private void handleInstallationError(WorkshopMod mod, Throwable throwable) {
        // Reload the mod entity from database to ensure it's attached to the current transaction
        WorkshopMod managedMod = modsService.getMod(mod.getId())
                .orElseThrow(() -> new IllegalStateException("Mod " + mod.getId() + " not found in database"));
        
        log.error("Download of mod '{}' (id {}) failed due to unexpected error",
                managedMod.getName(), managedMod.getId(), throwable);
        managedMod.setInstallationStatus(InstallationStatus.ERROR);
        managedMod.setErrorStatus(ErrorStatus.GENERIC);
        modsService.saveMod(managedMod);
    }

    public void uninstallMod(WorkshopMod mod) {
        File modDirectory = pathsFactory.getModInstallationPath(mod.getId(), mod.getServerType()).toFile();
        try {
            deleteBiKeys(mod);
            deleteSymlink(mod);
            FileUtils.deleteDirectory(modDirectory);
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            log.error("Could not delete mod (directory {})", modDirectory, e);
            throw new RuntimeException(e);
        }
        log.info("Mod {} ({}) successfully deleted", mod.getName(), mod.getId());
    }

    @Transactional
    private void handleInstallation(WorkshopMod mod, SteamCmdJob steamCmdJob) {
        // Reload the mod entity from database to ensure it's attached to the current transaction
        WorkshopMod managedMod = modsService.getMod(mod.getId())
                .orElseThrow(() -> new IllegalStateException("Mod " + mod.getId() + " not found in database"));
        
        if (steamCmdJob.getErrorStatus() != null) {
            log.error("Download of mod '{}' (id {}) failed, reason: {}",
                    managedMod.getName(), managedMod.getId(), steamCmdJob.getErrorStatus());
            managedMod.setInstallationStatus(InstallationStatus.ERROR);
            managedMod.setErrorStatus(steamCmdJob.getErrorStatus());
        } else if (!verifyModDirectoryExists(managedMod.getId(), managedMod.getServerType())) {
            log.error("Could not find downloaded mod directory for mod '{}' (id {}) " +
                    "even though download finished successfully", managedMod.getName(), managedMod.getId());
            managedMod.setInstallationStatus(InstallationStatus.ERROR);
            managedMod.setErrorStatus(ErrorStatus.GENERIC);
        } else {
            log.info("Mod '{}' (ID {}) successfully downloaded, now installing", managedMod.getName(), managedMod.getId());
            installMod(managedMod);
        }

        modsService.saveMod(managedMod);
    }

    private void installMod(WorkshopMod mod) {
        try {
            convertModFilesToLowercase(mod);
            updateBiKeys(mod);
            createSymlink(mod);
            updateModInfo(mod);
            mod.setInstallationStatus(InstallationStatus.FINISHED);
            log.info("Mod '{}' (ID {}) successfully installed", mod.getName(), mod.getId());
        } catch (Exception e) {
            log.error("Failed to install mod {} (ID {})", mod.getName(), mod.getId(), e);
            mod.setInstallationStatus(InstallationStatus.ERROR);
            mod.setErrorStatus(ErrorStatus.IO);
        }
    }

    private void convertModFilesToLowercase(WorkshopMod mod) throws IOException {
        File modDir = pathsFactory.getModInstallationPath(mod.getId(), mod.getServerType()).toFile();
        FileSystemUtils.directoryToLowercase(modDir);
    }

    private void updateBiKeys(WorkshopMod mod) throws IOException {
        deleteBiKeys(mod);
        installNewBiKeys(mod);
    }

    private void deleteBiKeys(WorkshopMod mod) {
        mod.getBiKeys().forEach(biKey -> {
            for (ServerType serverType : getRelevantServerTypes(mod)) {
                File keyFile = pathsFactory.getServerKeyPath(biKey, serverType).toFile();
                FileUtils.deleteQuietly(keyFile);
            }
        });
    }

    private void installNewBiKeys(WorkshopMod mod) throws IOException {
        String[] extensions = new String[]{"bikey"};
        File modDirectory = pathsFactory.getModInstallationPath(mod.getId(), mod.getServerType()).toFile();

        for (Iterator<File> it = FileUtils.iterateFiles(modDirectory, extensions, true); it.hasNext(); ) {
            File key = it.next();
            mod.addBiKey(key.getName());
            for (ServerType serverType : getRelevantServerTypes(mod)) {
                log.debug("Copying BiKey {} to server {}", key.getName(), serverType);
                FileUtils.copyFile(key, pathsFactory.getServerKeyPath(key.getName(), serverType).toFile());
            }
        }
    }

    private void createSymlink(WorkshopMod mod) throws IOException {
        // create symlink to server directory
        Path targetPath = pathsFactory.getModInstallationPath(mod.getId(), mod.getServerType());

        for (ServerType serverType : getRelevantServerTypes(mod)) {
            Path linkPath = pathsFactory.getModLinkPath(mod.getNormalizedName(), serverType);
            if (!Files.isSymbolicLink(linkPath)) {
                log.debug("Creating symlink - link {}, target {}", linkPath, targetPath);
                Files.createSymbolicLink(linkPath, targetPath);
            }
        }
    }

    private Collection<ServerType> getRelevantServerTypes(WorkshopMod mod) {
        Set<ServerType> serverTypes = new HashSet<>();
        if (installationService.isServerInstalled(mod.getServerType())) {
            serverTypes.add(mod.getServerType());
        }
        if (mod.getServerType() == ServerType.DAYZ && installationService.isServerInstalled(ServerType.DAYZ_EXP)) {
            serverTypes.add(ServerType.DAYZ_EXP);
        }
        return serverTypes;
    }

    private void updateModInfo(WorkshopMod mod) {
        mod.setLastUpdated(LocalDateTime.now());
        mod.setFileSize(getActualSizeOfMod(mod.getId(), mod.getServerType()));
    }

    private void deleteSymlink(WorkshopMod mod) throws IOException {
        Path linkPath = pathsFactory.getModLinkPath(mod.getNormalizedName(), mod.getServerType());
        log.debug("Deleting symlink {}", linkPath);
        if (Files.isSymbolicLink(linkPath)) {
            Files.delete(linkPath);
        }
    }

    private boolean verifyModDirectoryExists(Long modId, ServerType type) {
        return pathsFactory.getModInstallationPath(modId, type)
                .toFile()
                .isDirectory();
    }

    // as data about mod size from workshop API are not reliable, find the size of disk instead
    private Long getActualSizeOfMod(Long modId, ServerType type) {
        return FileUtils.sizeOfDirectory(
                pathsFactory.getModInstallationPath(modId, type).toFile()
        );
    }
}
