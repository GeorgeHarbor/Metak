package app.metak;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherTest {
    @Test
    public void mapsWmoCodes() {
        assertEquals("☀️", Weather.icon(0, true));
        assertEquals("🌙", Weather.icon(0, false));
        assertEquals("☁️", Weather.icon(3, true));
        assertEquals("🌫️", Weather.icon(45, true));
        assertEquals("🌧️", Weather.icon(61, true));
        assertEquals("🌧️", Weather.icon(81, true));
        assertEquals("🌨️", Weather.icon(73, true));
        assertEquals("⛈️", Weather.icon(95, true));
    }
}
