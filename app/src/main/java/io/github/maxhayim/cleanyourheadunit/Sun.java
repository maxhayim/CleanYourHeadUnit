package io.github.maxhayim.cleanyourheadunit;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Sunrise and sunset from latitude and longitude, worked out on the unit (no internet), with the
 * sunrise equation from the US Naval Observatory's "Almanac for Computers" (accurate to a couple
 * of minutes away from the poles). Used for day/night brightness.
 */
final class Sun {
    private Sun() {}

    /** Official zenith: the sun's centre 50 arcminutes below the horizon. */
    private static final double ZENITH = 90.833;

    /** Sunrise and sunset in epoch milliseconds for the local date of {@code dayMillis}; null on days the sun doesn't rise or set. */
    static long[] riseSet(double lat, double lon, long dayMillis, TimeZone tz) {
        Calendar local = Calendar.getInstance(tz);
        local.setTimeInMillis(dayMillis);
        local.set(Calendar.HOUR_OF_DAY, 0);
        local.set(Calendar.MINUTE, 0);
        local.set(Calendar.SECOND, 0);
        local.set(Calendar.MILLISECOND, 0);
        long localMidnight = local.getTimeInMillis();
        int dayOfYear = local.get(Calendar.DAY_OF_YEAR);

        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        long utcMidnight = utc.getTimeInMillis();

        Double rise = hourUtc(dayOfYear, lat, lon, true), set = hourUtc(dayOfYear, lat, lon, false);
        if (rise == null || set == null) return null;
        return new long[]{within(utcMidnight + (long) (rise * 3_600_000), localMidnight), within(utcMidnight + (long) (set * 3_600_000), localMidnight)};
    }

    /** The event belongs to the local day starting at localMidnight. */
    private static long within(long t, long localMidnight) {
        while (t < localMidnight) t += 86_400_000L;
        while (t >= localMidnight + 86_400_000L) t -= 86_400_000L;
        return t;
    }

    /** Hours after 00:00 UTC, or null if the sun stays up or down all day. */
    static Double hourUtc(int dayOfYear, double lat, double lon, boolean rising) {
        double lngHour = lon / 15;
        double t = dayOfYear + ((rising ? 6 : 18) - lngHour) / 24;
        double m = 0.9856 * t - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634, 360);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))), 360);
        ra += Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90;
        ra /= 15;
        double sinDec = 0.39782 * sin(l);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (cos(ZENITH) - sinDec * sin(lat)) / (cosDec * cos(lat));
        if (cosH > 1 || cosH < -1) return null;
        double h = rising ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        h /= 15;
        double localMean = h + ra - 0.06571 * t - 6.622;
        return norm(localMean - lngHour, 24);
    }

    /** True between today's sunrise and sunset. Where the sun doesn't set or rise, by season. */
    static boolean isDay(double lat, double lon, long now, TimeZone tz) {
        long[] rs = riseSet(lat, lon, now, tz);
        if (rs == null) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(now);
            int m = c.get(Calendar.MONTH);
            boolean northernSummer = m >= Calendar.APRIL && m <= Calendar.SEPTEMBER;
            return lat >= 0 == northernSummer;
        }
        return now >= rs[0] && now < rs[1];
    }

    /** The next sunrise or sunset after now (or in six hours where there's none). */
    static long nextChange(double lat, double lon, long now, TimeZone tz) {
        for (int d = 0; d < 3; d++) {
            long[] rs = riseSet(lat, lon, now + d * 86_400_000L, tz);
            if (rs == null) continue;
            if (rs[0] > now) return rs[0];
            if (rs[1] > now) return rs[1];
        }
        return now + 6 * 3_600_000L;
    }

    private static double norm(double v, double range) {
        v %= range;
        return v < 0 ? v + range : v;
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }
}
