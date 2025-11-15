package cz.forgottenempire.servermanager.steamcmd;

import cz.forgottenempire.servermanager.common.Constants;
import cz.forgottenempire.servermanager.common.PathsFactory;
import cz.forgottenempire.servermanager.common.ServerType;
import cz.forgottenempire.servermanager.installation.ServerInstallation;
import cz.forgottenempire.servermanager.workshop.WorkshopMod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SteamCmdService {

    private final SteamCmdExecutor steamCmdExecutor;
    private final PathsFactory pathsFactory;

    @Autowired
    public SteamCmdService(SteamCmdExecutor steamCmdExecutor, PathsFactory pathsFactory) {
        this.steamCmdExecutor = steamCmdExecutor;
        this.pathsFactory = pathsFactory;
    }

    public CompletableFuture<SteamCmdJob> installOrUpdateServer(ServerInstallation server) {
        ServerType serverType = server.getType();
        String betaBranchParameter = "-beta " + server.getBranch().toString().toLowerCase();

        SteamCmdParameters parameters = new SteamCmdParameters.Builder()
                .withInstallDir(pathsFactory.getServerPath(serverType).toAbsolutePath().toString())
                .withLogin()
                .withAppInstall(Constants.SERVER_IDS.get(serverType), true, betaBranchParameter)
                .build();
        return enqueueJob(new SteamCmdJob(serverType, parameters));
    }

    public CompletableFuture<SteamCmdJob> installOrUpdateWorkshopMods(Collection<WorkshopMod> workshopMods) {
        SteamCmdParameters.Builder parameters = new SteamCmdParameters.Builder()
                .withInstallDir(pathsFactory.getModsBasePath().toAbsolutePath().toString())
                .withLogin();

        workshopMods.forEach(mod ->
                parameters.withWorkshopItemInstall(
                        Constants.GAME_IDS.get(mod.getServerType()),
                        mod.getId(), true
                )
        );

        return enqueueJob(new SteamCmdJob(workshopMods, parameters.build()));
    }

    public CompletableFuture<SteamCmdJob> installOrUpdateWorkshopMod(WorkshopMod workshopMod) {
        SteamCmdParameters parameters = new SteamCmdParameters.Builder()
                .withInstallDir(pathsFactory.getModsBasePath().toAbsolutePath().toString())
                .withLogin()
                .withWorkshopItemInstall(
                        Constants.GAME_IDS.get(workshopMod.getServerType()),
                        workshopMod.getId(), true
                )
                .build();

        return enqueueJob(new SteamCmdJob(List.of(workshopMod), parameters));
    }

    public void clearCache() throws IOException {
        Path steamappsPath = Path.of(pathsFactory.getModsBasePath().toString(), "steamapps");
        
        // Clear appcache directory (Steam metadata cache only)
        // This does NOT delete installed mods in workshop/content
        Path appcachePath = Path.of(steamappsPath.toString(), "appcache");
        if (Files.exists(appcachePath)) {
            Files.walk(appcachePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete cache file: " + path, e);
                        }
                    });
        }
        
        // Clear downloads directory to remove broken/partial downloads only
        // This does NOT delete installed mods in workshop/content
        Path downloadsPath = Path.of(steamappsPath.toString(), "workshop", "downloads");
        if (Files.exists(downloadsPath)) {
            Files.walk(downloadsPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete download file: " + path, e);
                        }
                    });
        }
    }

    private CompletableFuture<SteamCmdJob> enqueueJob(SteamCmdJob job) {
        CompletableFuture<SteamCmdJob> future = new CompletableFuture<>();
        steamCmdExecutor.processJob(job, future);
        return future;
    }
}
