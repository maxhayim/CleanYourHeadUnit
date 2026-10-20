package com.gathin.tvhome;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteKeysTest {
    @Test public void keycodeAndPackage() {
        Map<Integer, String> keys = RemoteKeys.parseAppKeys(new String[] {
                "190:com.google.android.youtube.tv", " 193 : com.netflix.ninja "});
        assertEquals(2, keys.size());
        assertEquals("com.google.android.youtube.tv", keys.get(190));
        assertEquals("com.netflix.ninja", keys.get(193));
    }

    @Test public void badEntriesAreSkipped() {
        Map<Integer, String> keys = RemoteKeys.parseAppKeys(new String[] {
                "com.google.android.youtube.tv", "youtube:com.google.android.youtube.tv", "190:", ""});
        assertTrue(keys.isEmpty());
    }

    @Test public void theLastEntryForAKeycodeWins() {
        Map<Integer, String> keys = RemoteKeys.parseAppKeys(new String[] {"190:a.b", "190:c.d"});
        assertEquals("c.d", keys.get(190));
    }
}
