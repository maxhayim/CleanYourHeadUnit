package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AndroidVersionTest {
    @Test
    public void realVersionBeatsSettings() {
        // Seen on a real unit (2014 Camry, sp7731e): Settings say 13, API 27 is 8.1.
        assertEquals("8.1", AndroidVersion.real(27, "13"));
        assertTrue(AndroidVersion.spoofed(27, "13"));
        assertFalse(AndroidVersion.spoofed(27, "8.1.0"));
        assertFalse(AndroidVersion.spoofed(32, "12"));
        assertFalse(AndroidVersion.spoofed(20, "4.4W"));
        assertFalse("unknown level: trust nothing new", AndroidVersion.spoofed(99, "17"));
        assertEquals("17", AndroidVersion.real(99, "17"));
    }
}
