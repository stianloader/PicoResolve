package de.geolykt.mavenresolver.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class InternalLastUpdatedFile {

    private final Map<String, String> errors = new HashMap<>();
    private final Map<String, Long> lastFetch = new HashMap<>();
    private final Map<Object, Object> nonsensePairs = new HashMap<>();

    public InternalLastUpdatedFile updateEntry(String repo, String error, long updateTime) {
        this.errors.put(repo, error);
        this.lastFetch.put(repo, updateTime);
        return this;
    }

    public Long getLastFetchTime(String repoURL) {
        return lastFetch.get(repoURL);
    }

    public static InternalLastUpdatedFile parse(Path src) {
        InternalLastUpdatedFile f = new InternalLastUpdatedFile();
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(src));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        prop.forEach((key, value) -> {
            String keyString = key.toString();
            String valueString = value.toString();
            int dotIndex = keyString.indexOf('.');
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

    public void write(Path out) {
        Properties props = new Properties();
        errors.forEach((key, val) -> {
            props.put(key + ".error", val);
        });
        lastFetch.forEach((key, val) -> {
            props.put(key + ".lastUpdated", val.toString());
        });
        nonsensePairs.forEach(props::put);
        try {
            props.store(Files.newOutputStream(out), "NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.\n"
                    + "NOTE: This file was written by de.geolykt:mavenresolver, a nonstandard resolver implementation!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
