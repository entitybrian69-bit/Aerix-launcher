package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VersionComparatorTest {
    @Test
    public void comparesDottedVersionsNumerically() {
        assertTrue(VersionComparator.compare("1.10.0", "1.9.9") > 0);
        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0);
    }

    @Test
    public void ignoresTagPrefixAndTrailingZeroComponents() {
        assertEquals(0, VersionComparator.compare("v1.0.0", "1.0"));
        assertEquals(0, VersionComparator.compare("release-2.4.0", "2.4"));
    }

    @Test
    public void handlesPrereleaseAndBuildMetadataAsNumericVersion() {
        assertEquals(0, VersionComparator.compare("v1.3.0-rc.1+build.7", "1.3.0"));
        assertTrue(VersionComparator.compare("v1.3.1-beta", "1.3.0") > 0);
    }
}
