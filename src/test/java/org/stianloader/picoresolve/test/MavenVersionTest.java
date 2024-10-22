package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.stianloader.picoresolve.version.MavenVersion;

public class MavenVersionTest {

    private boolean isNewer(@NotNull String newer, @NotNull String older) {
        return MavenVersion.parse(newer).isNewerThan(MavenVersion.parse(older));
    }

    @Test
    public void testQualifierPreference() {
        assertFalse(isNewer("1.0-aaa", "1.0-aab"));
        assertTrue(isNewer("1.0-aab", "1.0-aaa"));

        assertFalse(isNewer("1.0-alpha", "1.0-aaa"));
        assertTrue(isNewer("1.0-aaa", "1.0-alpha"));

        assertTrue(isNewer("1.0.alpha", "1.0-aaa"));
        assertFalse(isNewer("1.0-aaa", "1.0.alpha"));

        assertFalse(isNewer("1.0-alpha", "1.0.aaa"));
        assertTrue(isNewer("1.0.aaa", "1.0-alpha"));

        assertFalse(isNewer("1.0", "1.0.aaa"));
        assertTrue(isNewer("1.0.aaa", "1.0"));
    }

    @Test
    public void testMavenPreferenceInterop() {
        // Based on examples provided by https://maven.apache.org/pom.html#Version_Order_Specification
        assertFalse(isNewer("1.0-rc", "1.0-cr"));
        assertFalse(isNewer("1.0-cr", "1.0-rc"));
        assertFalse(isNewer("1.0-ga", "1.0.ga"));
        assertFalse(isNewer("1.0.ga", "1.0-ga"));
        assertFalse(isNewer("1-1.foo-bar1baz-.1", "1-1.foo-bar-1-baz-0.1"));
        assertFalse(isNewer("1-1.foo-bar-1-baz-0.1", "1-1.foo-bar1baz-.1"));

        assertFalse(isNewer("1", "1.1"));
        assertFalse(isNewer("1-snapshot", "1"));
        assertFalse(isNewer("1", "1-sp"));

        assertFalse(isNewer("1-foo2", "1-foo10"));
        assertFalse(isNewer("1.foo", "1-foo"));
        assertFalse(isNewer("1-foo", "1-1"));
        assertFalse(isNewer("1-1", "1.1"));

        assertTrue(isNewer("1.0-sp", "1.0-ga"));
        assertTrue(isNewer("1.0-sp.1", "1.0-ga.1"));
        assertFalse(isNewer("1-sp-1", "1-ga-1"));
        assertTrue(isNewer("1-1", "1-ga-1"));

        assertFalse(isNewer("1-a1", "1-alpha-1"));
        assertFalse(isNewer("1-alpha-1", "1-a1"));

        assertFalse(isNewer("1-final", "1"));
        assertFalse(isNewer("1", "1-final"));
    }

    @Test
    public void testPreference() {
        // Based on "common sense" and examples provided by https://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#MAVEN400
        assertTrue(isNewer("5", "5-SNAPSHOT"));
        assertFalse(isNewer("5-SNAPSHOT", "5"));
        assertFalse(isNewer("5", "5"));
        assertFalse(isNewer("5", "6"));
        assertTrue(isNewer("6", "5"));

        assertTrue(isNewer("1.2", "1.0"));
        assertTrue(isNewer("1.2-SNAPSHOT", "1.0"));
        assertTrue(isNewer("1.2", "1.0-SNAPSHOT"));
        assertTrue(isNewer("1.2-SNAPSHOT", "1.0-SNAPSHOT"));

        assertFalse(isNewer("1.0", "1.2"));
        assertFalse(isNewer("1.0-SNAPSHOT", "1.2"));
        assertFalse(isNewer("1.0", "1.2-SNAPSHOT"));
        assertFalse(isNewer("1.0-SNAPSHOT", "1.2-SNAPSHOT"));

        assertFalse(isNewer("1.0", "1.0"));
        assertFalse(isNewer("1.0-SNAPSHOT", "1.0"));
        assertTrue(isNewer("1.0", "1.0-SNAPSHOT"));
        assertFalse(isNewer("1.0-SNAPSHOT", "1.0-SNAPSHOT"));

        assertTrue(isNewer("1.2-12", "1.1"));
        assertTrue(isNewer("1.2-12", "1.1-33"));
        assertTrue(isNewer("1.2-12", "1.2-11"));
        assertFalse(isNewer("1.1", "1.2-12"));
        assertFalse(isNewer("1.2-11", "1.2-12"));
        assertFalse(isNewer("1.1-33", "1.2-12"));

        assertTrue(isNewer("1.2-alpha", "1.1"));
        assertTrue(isNewer("1.2-alpha", "1.1-beta"));
        assertTrue(isNewer("1.2-beta", "1.2-alpha"));
        assertFalse(isNewer("1.1", "1.2-alpha"));
        assertFalse(isNewer("1.2-alpha", "1.2-alpha"));
        assertFalse(isNewer("1.1-beta", "1.2-alpha"));

        assertTrue(isNewer("1.0.0", "0.0.9"));
        assertTrue(isNewer("1.0.0", "0.99.9"));
        assertTrue(isNewer("1.0.0", "0.99.9-alpha"));
        assertTrue(isNewer("1.0.0", "0.99.9-gamma"));
        assertTrue(isNewer("1.0.0-alpha", "0.99.9-alpha"));
        assertTrue(isNewer("1.0.0-alpha", "0.99.9-gamma"));
        assertTrue(isNewer("1.0.0-gamma", "0.99.9-alpha"));
        assertTrue(isNewer("1.0.0-gamma", "0.99.9-gamma"));

        assertFalse(isNewer("0.0.9", "1.0.9"));
        assertFalse(isNewer("0.99.9", "1.99.9"));
        assertFalse(isNewer("0.99.9", "1.99.9-alpha"));
        assertFalse(isNewer("0.99.9", "1.99.9-gamma"));
        assertFalse(isNewer("0.99.0-alpha", "1.99.9-alpha"));
        assertFalse(isNewer("0.99.0-alpha", "1.99.9-gamma"));
        assertFalse(isNewer("0.99.0-gamma", "1.99.9-alpha"));
        assertFalse(isNewer("0.99.0-gamma", "1.99.9-gamma"));

        assertTrue(isNewer("1.0.10.1", "1.0.1.0"));
        assertTrue(isNewer("1.0.10.2", "1.0.10.1"));
        assertTrue(isNewer("1.0.10.2", "1.0.9.3"));  // Our sources seem to disagree there. However maven's internal utilities say that this is the right way - so this is how it is

        assertTrue(isNewer("1.2-beta-2", "1.2-alpha-6"));

        assertFalse(isNewer("1.2.3", "1.3.2"));
        assertFalse(isNewer("0.2.3", "1.3.2"));
        assertTrue(isNewer("1.2.3", "0.3.2"));
        assertFalse(isNewer("1.2.3-SNAPSHOT", "1.2.3"));
        assertFalse(isNewer("1.2.3-SNAPSHOT", "1.2.3-SNAPSHOT"));
        assertTrue(isNewer("1.2.3", "1.2.3-SNAPSHOT"));

        assertTrue(isNewer("1.0.10-1", "1.0.1-0"));
        assertTrue(isNewer("1.0.10-2", "1.0.10-1"));
        assertFalse(isNewer("1.0.9-3", "1.0.10-2"));
        assertTrue(isNewer("1.0.9-3", "1.0.1-0"));

        // Eclipse stuff
        assertTrue(isNewer("3.3.0-I20070605-0010", "3.3.0"));
        assertFalse(isNewer("3.3.0", "3.3.0-I20070605-0010"));
    }

    @Test
    public void testRoguePrefix() {
        MavenVersion.parse("0-0-1");
        MavenVersion.parse(".1");
        MavenVersion.parse("0.foo");
        MavenVersion.parse("0-foo");
        assertTrue(isNewer("0.1-max-version", "0-min-version"));
    }
}
