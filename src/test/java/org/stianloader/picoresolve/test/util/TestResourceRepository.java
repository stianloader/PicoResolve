package org.stianloader.picoresolve.test.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.NotNull;
import org.stianloader.picoresolve.internal.JavaInterop;
import org.stianloader.picoresolve.repo.MavenRepository;
import org.stianloader.picoresolve.repo.RepositoryAttachedValue;

public final class TestResourceRepository implements MavenRepository {
    @Override
    @NotNull
    public CompletableFuture<RepositoryAttachedValue<byte[]>> getResource(@NotNull String path, @NotNull Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream is = TestResourceRepository.class.getResourceAsStream("/" + path)) {
                if (is == null) {
                    throw new IOException("No resource at path '" + path + "'");
                }

                return new RepositoryAttachedValue<>(this, JavaInterop.readAllBytes(is));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    @Override
    @NotNull
    public String getRepositoryId() {
        return "test-resources";
    }

    @Override
    @NotNull
    public String getPlaintextURL() {
        return "/";
    }

    @Override
    public long getUpdateIntervall() {
        return -1;
    }
}
