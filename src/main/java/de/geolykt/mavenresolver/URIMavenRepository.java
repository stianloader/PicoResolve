package de.geolykt.mavenresolver;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import de.geolykt.mavenresolver.misc.ConcurrencyUtil;

public class URIMavenRepository implements MavenRepository {

    private final class BaseMavenResource implements MavenResource {

        private final Path path;

        public BaseMavenResource(final Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public MavenRepository getSourceRepository() {
            return URIMavenRepository.this;
        }
    }

    private final URI base;
    private final Path cacheFolder;

    public URIMavenRepository(Path cacheFolder, URI base) {
        this.base = base;
        this.cacheFolder = cacheFolder;
        if (Files.notExists(cacheFolder)) {
            try {
                Files.createDirectories(cacheFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String toHexHash(byte[] hash) {
        final StringBuilder hex = new StringBuilder(2 * hash.length);
        for (final byte b : hash) {
            int x = ((int) b) & 0x00FF;
            if (x < 16) {
                hex.append('0');
            }
            hex.append(Integer.toHexString(x));
        }
        return hex.toString();
    }

    protected Path getResource0(String path, boolean checkChecksums) throws Exception {
        URI resolved = base.resolve(path);
        String pstring = path;
        while (pstring.codePointAt(0) == '/') {
            // Let's not resolve absolute paths...
            pstring = pstring.substring(1);
        }
        Path cachePath = cacheFolder.resolve(pstring);
        if (!Files.exists(cachePath)) {
            System.out.println("Downloading " + resolved);
            Files.createDirectories(cachePath.getParent());
            if (checkChecksums) {
                DigestInputStream din = new DigestInputStream(resolved.toURL().openStream(), MessageDigest.getInstance("SHA-1"));
                Files.copy(din, cachePath);
                try {
                    Path checksumFile = getResource0(path + ".sha1", false);
                    String reported = Files.readString(checksumFile, StandardCharsets.UTF_8).stripLeading().split(" ")[0].strip();
                    String actual = toHexHash(din.getMessageDigest().digest());
                    if (!reported.equalsIgnoreCase(actual)) {
                        System.err.println("Checksum mismatch: " + path + " (" + reported + ";" + actual + ")");
                        throw new IOException("The reported checksum of \"" + reported + "\" does not match the actual checksum of \"" + actual + "\" for resource " + path + "!");
                    }
                } catch (Exception e) {
                    Files.deleteIfExists(cachePath);
                    throw e;
                }
            } else {
                Files.copy(resolved.toURL().openStream(), cachePath);
            }
            if (!Files.exists(cachePath)) {
                Files.writeString(cachePath, "nofile/" + String.valueOf(System.currentTimeMillis()), StandardCharsets.UTF_8);
                throw new IOException("Unable to download " + path + " to local file storage (for whatever reason)");
            }
        } else if (Files.size(cachePath) > 100) {
            String s = Files.readString(cachePath, StandardCharsets.UTF_8);
            if (s.startsWith("nofile/")) {
                s = s.substring(7);
                try {
                    if (Long.parseLong(s) + 3_600_000 /* 1 hour */ > System.currentTimeMillis()) {
                        throw new IOException("Unable to download " + path + " to local file storage (for whatever reason)");
                    } else {
                        Files.delete(cachePath);
                        return getResource0(path, checkChecksums);
                    }
                } catch (NumberFormatException nfe) {
                    nfe.printStackTrace();
                }
            }
        }
        return cachePath;
    }

    @Override
    public CompletableFuture<MavenResource> getResource(String path, Executor executor) {
        return ConcurrencyUtil.schedule(() -> {
            return new BaseMavenResource(getResource0(path, true));
        }, executor);
    }
}
