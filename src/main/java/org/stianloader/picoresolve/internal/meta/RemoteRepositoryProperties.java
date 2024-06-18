package org.stianloader.picoresolve.internal.meta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class RemoteRepositoryProperties {

    private final List<String> lines = new ArrayList<>();

    public RemoteRepositoryProperties(Path source) throws IOException {
        this(Files.readAllLines(source, StandardCharsets.UTF_8));
    }

    public RemoteRepositoryProperties(List<String> lines) {
        for (String s : lines) {
            if (s.startsWith("#") && !s.startsWith("#NOTE: ")) {
                this.lines.add("#" + new Date().toString());
                continue;
            }
            this.lines.add(s);
        }
    }

    public RemoteRepositoryProperties() {
        this(Arrays.asList("#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.",
                "#NOTE: This file was written by picoresolve, a nonstandard resolver implementation!",
                "#" + new Date().toString()));
    }

    public void setSourceRepository(String file, String remote) {
        String n = file + '>';
        this.lines.removeIf(s -> s.startsWith(n));
        this.lines.add(n + remote + '=');
    }

    public Optional<String> getSourceRepository(String file) {
        String n = file + '>';
        return lines.stream()
                .filter(e -> e.startsWith(n))
                .map(e -> e.substring(file.length() + 1, e.lastIndexOf('=')))
                .findFirst();
    }

    public void write(Path path) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    public void tryWrite(Path path) {
        try {
            write(path);
        } catch (Exception ignored) {
        }
    }

    public static RemoteRepositoryProperties tryRead(Path path) {
        try {
            if (Files.exists(path)) {
                return new RemoteRepositoryProperties(path);
            }
        } catch (Exception ignored) {
        }
        return new RemoteRepositoryProperties();
    }
}
