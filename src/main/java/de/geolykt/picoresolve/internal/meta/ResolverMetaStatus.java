package de.geolykt.picoresolve.internal.meta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jetbrains.annotations.NotNull;

/**
 * The resolver-status.properties file is functionally the same as {@link LastUpdatedFile}
 * but is indexing maven-metadata.xml files.
 */
public class ResolverMetaStatus {

    private final Map<String, String> errors = new HashMap<>();
    private final Map<String, Long> lastFetch = new HashMap<>();
    private final Map<Object, Object> nonsensePairs = new HashMap<>();

    public ResolverMetaStatus updateEntryErrored(String repoId, String error, long updateTime) {
        this.errors.put("maven-metadata-" + repoId + ".xml", error);
        this.lastFetch.put("maven-metadata-" + repoId + ".xml", updateTime);
        return this;
    }

    public ResolverMetaStatus updateEntrySuccess(String repoId, long updateTime) {
        this.errors.remove("maven-metadata-" + repoId + ".xml");
        this.lastFetch.put("maven-metadata-" + repoId + ".xml", updateTime);
        return this;
    }

    public boolean hasErrored(String repoId) {
        return errors.containsKey("maven-metadata-" + repoId + ".xml");
    }

    public Long getLastFetchTime(String repoId) {
        return lastFetch.get("maven-metadata-" + repoId + ".xml");
    }

    public static ResolverMetaStatus tryParse(@NotNull Path src) {
        ResolverMetaStatus f = new ResolverMetaStatus();
        if (Files.notExists(src)) {
            return f;
        }
        Properties prop = new Properties();
        try (InputStream is = Files.newInputStream(src)) {
            prop.load(is);
        } catch (IOException ignored) {
            ignored.printStackTrace();
            return f;
        }
        prop.forEach((key, value) -> {
            String keyString = key.toString();
            String valueString = value.toString();
            int dotIndex = keyString.lastIndexOf('.');
            if (dotIndex != -1) {
                String repo = keyString.substring(0, dotIndex);
                String action = keyString.substring(dotIndex + 1);
                if (action.equals("error")) {
                    f.errors.put(repo, valueString);
                } else if (action.equals("lastUpdated")) {
                    f.lastFetch.put(repo, Long.valueOf(valueString));
                } else {
                    f.nonsensePairs.put(key, value);
                }
            } else {
                f.nonsensePairs.put(key, value);
            }
        });
        return f;
    }

    public void write(@NotNull Path out) {
        Properties props = new Properties();
        errors.forEach((key, val) -> {
            props.put(key + ".error", val);
        });
        lastFetch.forEach((key, val) -> {
            props.put(key + ".lastUpdated", val.toString());
        });
        nonsensePairs.forEach(props::put);
        try (OutputStream os = Files.newOutputStream(out)) {
            props.store(os, "NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.\n"
                    + "NOTE: This file was written by de.geolykt:picoresolve, a nonstandard resolver implementation!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
