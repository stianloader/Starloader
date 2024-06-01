package de.geolykt.starloader.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stianloader.picoresolve.internal.ConcurrencyUtil;
import org.stianloader.picoresolve.internal.JavaInterop;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;

public class MirroringURIMavenRepository implements MavenRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirroringURIMavenRepository.class);

    @NotNull
    private final URI base;
    @NotNull
    private final String id;
    @Nullable
    private final Path mirrorOut;

    public MirroringURIMavenRepository(@NotNull String id, @NotNull URI base, @Nullable Path mirrorOut) {
        if (base.getPath().isEmpty()) {
            base = base.resolve("/");
        } else if (!base.getPath().endsWith("/")) {
            base = base.resolve(base.getPath() + "/");
        }
        this.base = base;
        this.id = id;
        this.mirrorOut = mirrorOut;
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public String getPlaintextURL() {
        return this.base.toString();
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public String getRepositoryId() {
        return this.id;
    }

    @Override
    @NotNull
    public CompletableFuture<RepositoryAttachedValue<byte[]>> getResource(@NotNull String path, @NotNull Executor executor) {
        return ConcurrencyUtil.schedule(() -> {
            byte[] data = this.getResource0(path);
            Path mirrorOut = this.mirrorOut;
            if (mirrorOut != null) {
                String dumpPath = path;
                while (dumpPath.startsWith("/")) {
                    dumpPath = dumpPath.substring(1);
                }
                Path file = mirrorOut.resolve(dumpPath);
                Path dir = file.getParent();
                Files.createDirectories(dir);
                Files.write(file, data);
            }
            return new RepositoryAttachedValue<>(this, data);
        }, executor);
    }

    protected byte @NotNull[] getResource0(@NotNull String path) throws Exception {
        URI resolved = this.base.resolve(path);
        URLConnection connection = resolved.toURL().openConnection();
        MirroringURIMavenRepository.LOGGER.debug("Downloading {}", resolved);
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpUrlConn = (HttpURLConnection) connection;
            if ((httpUrlConn.getResponseCode() / 100) != 2) {
                throw new IOException("Query for " + connection.getURL() + " returned with a response code of " + httpUrlConn.getResponseCode() + " (" + httpUrlConn.getResponseMessage() + ")");
            }
        }

        try (InputStream is = connection.getInputStream()) {
            return JavaInterop.readAllBytes(is);
        }
    }

    @Override
    @Contract(pure = true)
    public long getUpdateIntervall() {
        return 7 * 24 * 60 * 60 * 1000; // Once a week should be enough
    }
}
