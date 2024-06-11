package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.stianloader.picoresolve.version.MavenVersion;
import org.stianloader.picoresolve.version.VersionRange;

public class VersionRangeTest {
    @Test
    public void testIntervalTypes() {
        assertFalse(VersionRange.parse("[4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.5")));
        assertTrue(VersionRange.parse("[4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.4")));
        assertTrue(VersionRange.parse("[4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.3")));
        assertTrue(VersionRange.parse("[4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.2")));
        assertFalse(VersionRange.parse("[4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.1")));

        assertFalse(VersionRange.parse("[4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.5")));
        assertFalse(VersionRange.parse("[4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.4")));
        assertTrue(VersionRange.parse("[4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.3")));
        assertTrue(VersionRange.parse("[4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.2")));
        assertFalse(VersionRange.parse("[4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.1")));

        assertFalse(VersionRange.parse("(4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.5")));
        assertTrue(VersionRange.parse("(4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.4")));
        assertTrue(VersionRange.parse("(4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.3")));
        assertFalse(VersionRange.parse("(4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.2")));
        assertFalse(VersionRange.parse("(4.0.2,4.0.4]").containsVersion(MavenVersion.parse("4.0.1")));

        assertFalse(VersionRange.parse("(4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.5")));
        assertFalse(VersionRange.parse("(4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.4")));
        assertTrue(VersionRange.parse("(4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.3")));
        assertFalse(VersionRange.parse("(4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.2")));
        assertFalse(VersionRange.parse("(4.0.2,4.0.4)").containsVersion(MavenVersion.parse("4.0.1")));
    }

    @Test
    public void testUnboundedIntervals() {
        assertTrue(VersionRange.parse("[1,]").containsVersion(MavenVersion.parse("1")));
        assertTrue(VersionRange.parse("[1,]").containsVersion(MavenVersion.parse("5")));
        assertFalse(VersionRange.parse("[1,]").containsVersion(MavenVersion.parse("0.1")));

        assertFalse(VersionRange.parse("(1,]").containsVersion(MavenVersion.parse("1")));
        assertTrue(VersionRange.parse("(1,]").containsVersion(MavenVersion.parse("5")));
        assertFalse(VersionRange.parse("(1,]").containsVersion(MavenVersion.parse("0.1")));

        assertTrue(VersionRange.parse("[1,)").containsVersion(MavenVersion.parse("1")));
        assertTrue(VersionRange.parse("[1,)").containsVersion(MavenVersion.parse("5")));
        assertFalse(VersionRange.parse("[1,)").containsVersion(MavenVersion.parse("0.1")));

        assertFalse(VersionRange.parse("(1,)").containsVersion(MavenVersion.parse("1")));
        assertTrue(VersionRange.parse("(1,)").containsVersion(MavenVersion.parse("5")));
        assertFalse(VersionRange.parse("(1,)").containsVersion(MavenVersion.parse("0.1")));

        assertTrue(VersionRange.parse("[,1]").containsVersion(MavenVersion.parse("1")));
        assertFalse(VersionRange.parse("[,1]").containsVersion(MavenVersion.parse("5")));
        assertTrue(VersionRange.parse("[,1]").containsVersion(MavenVersion.parse("0.1")));

        assertTrue(VersionRange.parse("(,1]").containsVersion(MavenVersion.parse("1")));
        assertFalse(VersionRange.parse("(,1]").containsVersion(MavenVersion.parse("5")));
        assertTrue(VersionRange.parse("(,1]").containsVersion(MavenVersion.parse("0.1")));

        assertFalse(VersionRange.parse("[,1)").containsVersion(MavenVersion.parse("1")));
        assertFalse(VersionRange.parse("[,1)").containsVersion(MavenVersion.parse("5")));
        assertTrue(VersionRange.parse("[,1)").containsVersion(MavenVersion.parse("0.1")));

        assertFalse(VersionRange.parse("(,1)").containsVersion(MavenVersion.parse("1")));
        assertFalse(VersionRange.parse("(,1)").containsVersion(MavenVersion.parse("5")));
        assertTrue(VersionRange.parse("(,1)").containsVersion(MavenVersion.parse("0.1")));
    }

    @Test
    public void testSingleVersionRange() {
        assertTrue(VersionRange.parse("1").containsVersion(MavenVersion.parse("1")));
        assertTrue(VersionRange.parse("1").containsVersion(MavenVersion.parse("1.0.0")));

        assertEquals(0, Objects.requireNonNull(VersionRange.parse("1").getRecommended()).compareTo(MavenVersion.parse("1.0")));
        assertNotEquals(0, Objects.requireNonNull(VersionRange.parse("1").getRecommended()).compareTo(MavenVersion.parse("1.0.1")));

        assertTrue(VersionRange.parse("[1]").containsVersion(MavenVersion.parse("1")));
        assertTrue(VersionRange.parse("[1]").containsVersion(MavenVersion.parse("1.0.0")));
        assertFalse(VersionRange.parse("[1]").containsVersion(MavenVersion.parse("1.0.1")));
        assertFalse(VersionRange.parse("[1]").containsVersion(MavenVersion.parse("2")));
    }

    @Test
    public void testImplicitPinVersionRange() {
        assertFalse(VersionRange.parse("1").containsVersion(MavenVersion.parse("1.1")));
    }
}
