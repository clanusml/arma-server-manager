package cz.forgottenempire.servermanager.workshop;

import cz.forgottenempire.servermanager.common.Constants;
import cz.forgottenempire.servermanager.common.InstallationStatus;
import cz.forgottenempire.servermanager.common.PathsFactory;
import cz.forgottenempire.servermanager.common.ServerType;
import cz.forgottenempire.servermanager.common.exceptions.NotFoundException;
import cz.forgottenempire.servermanager.common.exceptions.ServerNotInitializedException;
import cz.forgottenempire.servermanager.installation.ServerInstallationService;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import cz.forgottenempire.servermanager.workshop.metadata.ModMetadata;
import cz.forgottenempire.servermanager.workshop.metadata.ModMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class WorkshopModsFacade {

    private final WorkshopModsService modsService;
    private final WorkshopInstallerService installerService;
    private final ModMetadataService fileDetailsService;
    private final ServerInstallationService serverInstallationService;
    private final PathsFactory pathsFactory;

    @Autowired
    public WorkshopModsFacade(
            WorkshopModsService modsService,
            WorkshopInstallerService installerService,
            ModMetadataService fileDetailsService,
            ServerInstallationService serverInstallationService,
            PathsFactory pathsFactory) {
        this.modsService = modsService;
        this.installerService = installerService;
        this.fileDetailsService = fileDetailsService;
        this.serverInstallationService = serverInstallationService;
        this.pathsFactory = pathsFactory;
    }

    public Optional<WorkshopMod> getMod(long id) {
        return modsService.getMod(id);
    }

    public Collection<WorkshopMod> getAllMods() {
        Collection<WorkshopMod> mods = modsService.getAllMods();
        ensureFileSizesCalculated(mods);
        return mods;
    }

    public Collection<WorkshopMod> getAllMods(@Nullable ServerType filter) {
        if (filter == null) {
            return getAllMods();
        }
        if (filter == ServerType.DAYZ_EXP) {
            filter = ServerType.DAYZ;
        }
        Collection<WorkshopMod> mods = modsService.getAllMods(filter);
        ensureFileSizesCalculated(mods);
        return mods;
    }

    /**
     * Ensures that installed mods have their file sizes calculated.
     * This is needed for mods that were installed before file size tracking was added.
     */
    @Transactional
    private void ensureFileSizesCalculated(Collection<WorkshopMod> mods) {
        boolean anyModUpdated = false;
        for (WorkshopMod mod : mods) {
            if (shouldRecalculateFileSize(mod)) {
                Long calculatedSize = calculateModFileSize(mod.getId(), mod.getServerType());
                if (calculatedSize != null && calculatedSize > 0) {
                    mod.setFileSize(calculatedSize);
                    anyModUpdated = true;
                    log.debug("Updated file size for mod {} (ID {}): {} bytes", 
                            mod.getName(), mod.getId(), calculatedSize);
                }
            }
        }
        if (anyModUpdated) {
            modsService.saveAllMods(mods.stream().toList());
        }
    }

    /**
     * Determines if a mod's file size should be recalculated.
     */
    private boolean shouldRecalculateFileSize(WorkshopMod mod) {
        // Only recalculate for finished installations with missing or zero file size
        if (mod.getInstallationStatus() != InstallationStatus.FINISHED) {
            return false;
        }
        Long fileSize = mod.getFileSize();
        return fileSize == null || fileSize == 0L;
    }

    /**
     * Calculates the actual file size of a mod on disk.
     */
    private Long calculateModFileSize(Long modId, ServerType type) {
        try {
            return FileUtils.sizeOfDirectory(
                    pathsFactory.getModInstallationPath(modId, type).toFile()
            );
        } catch (Exception e) {
            log.warn("Failed to calculate file size for mod {} ({}): {}", modId, type, e.getMessage());
            return null;
        }
    }

    @Transactional
    public List<WorkshopMod> saveAndInstallMods(List<Long> ids) {
        List<WorkshopMod> workshopMods = ids.stream()
                .map(id -> getMod(id).orElse(new WorkshopMod(id)))
                .toList();

        workshopMods.forEach(mod -> {
            mod.setInstallationStatus(InstallationStatus.INSTALLATION_IN_PROGRESS);
            mod.setErrorStatus(null);

            ModMetadata modMetadata = fileDetailsService.fetchModMetadata(mod.getId());
            mod.setName(modMetadata.name());
            setModServerType(mod, modMetadata.consumerAppId());
            validateServerInitialized(mod);
        });
        modsService.saveAllModsForInstallation(workshopMods);

        installerService.installOrUpdateMods(workshopMods);
        return workshopMods;
    }

    @Transactional
    public void updateAllMods() {
        List<Long> allModIds = modsService.getAllMods().stream()
                .map(WorkshopMod::getId)
                .toList();
        saveAndInstallMods(allModIds);
    }

    public void uninstallMod(long id) {
        WorkshopMod workshopMod = getMod(id)
                .orElseThrow(() -> new NotFoundException("Mod ID " + id + " not found."));
        installerService.uninstallMod(workshopMod);
        modsService.deleteMod(workshopMod);
    }

    public void setModServerOnly(WorkshopMod mod, boolean serverOnly) {
        mod.setServerOnly(serverOnly);
        modsService.saveMod(mod);
    }

    private void setModServerType(WorkshopMod mod, String consumerAppId) {
        if (Constants.GAME_IDS.get(ServerType.ARMA3).toString().equals(consumerAppId)) {
            mod.setServerType(ServerType.ARMA3);
        } else if (Constants.GAME_IDS.get(ServerType.DAYZ).toString().equals(consumerAppId)) {
            mod.setServerType(ServerType.DAYZ);
        } else {
            log.warn("Tried to install mod ID {} which is not consumed by any of the supported servers", mod.getId());
            throw new ModNotConsumedByGameException(
                    "The mod " + mod.getId() + " is not consumed by any supported game");
        }
    }

    private void validateServerInitialized(WorkshopMod workshopMod) {
        if (workshopMod.getServerType() == ServerType.ARMA3 && isServerNotInitialized(ServerType.ARMA3)) {
            throw new ServerNotInitializedException("Mod installation failed: Arma 3 server is not installed");
        }
        if (workshopMod.getServerType() == ServerType.DAYZ && isServerNotInitialized(ServerType.DAYZ)
                && isServerNotInitialized(ServerType.DAYZ_EXP)) {
            throw new ServerNotInitializedException(
                    "Mod installation failed: Neither DayZ nor DayZ Experimental server is installed");
        }
    }

    private boolean isServerNotInitialized(ServerType serverType) {
        return !serverInstallationService.isServerInstalled(serverType);
    }
}
