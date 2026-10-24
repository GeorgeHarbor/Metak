package app.metak;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class WatchNextTest {
    @Test
    public void keepsOnlyNuvioAndSmartTube() {
        assertTrue(WatchNext.wanted("com.nuvio.tv"));
        assertTrue(WatchNext.wanted("com.nuvio.app"));
        assertTrue(WatchNext.wanted("org.smarttube.stable"));
        assertTrue(WatchNext.wanted("com.teamsmart.videomanager.tv"));
        assertFalse(WatchNext.wanted("com.netflix.ninja"));
        assertFalse(WatchNext.wanted(null));
    }

    @Test
    public void newestFirstWithSourceAndProgress() {
        List<WatchNext.Item> items = new ArrayList<>();
        items.add(item("com.nuvio.tv", 10, 30, 60));
        items.add(item("org.smarttube.stable", 20, 0, 0));
        WatchNext.sort(items);

        assertEquals("SmartTube", items.get(0).source());
        assertEquals(-1, items.get(0).progress());
        assertEquals("Nuvio", items.get(1).source());
        assertEquals(500, items.get(1).progress());
    }

    private static WatchNext.Item item(String pkg, long lastEngaged, long position, long duration) {
        WatchNext.Item i = new WatchNext.Item();
        i.pkg = pkg;
        i.lastEngaged = lastEngaged;
        i.position = position;
        i.duration = duration;
        return i;
    }
}
