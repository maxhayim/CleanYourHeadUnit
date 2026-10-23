package com.gathin.tvhome;

import android.content.res.Resources;
import android.icu.text.SimpleDateFormat;
import android.icu.util.HebrewCalendar;
import android.icu.util.ULocale;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Optional civil display, computed offline; sunset is approximate, not a source of religious times. */
final class JewishDate {
    final boolean showDate, greetings;
    private final double latitude, longitude;
    private final String region;

    private JewishDate(boolean date, boolean greetings, String region, double latitude, double longitude) {
        this.showDate = date;
        this.greetings = greetings;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    static JewishDate load(Resources res, JSONObject json) {
        boolean date = flag(res, json, "hebrew_date", R.bool.hebrew_date);
        boolean greetings = flag(res, json, "jewish_holiday_greetings", R.bool.jewish_holiday_greetings);
        if (!date && !greetings) return null;
        try {
            double lat = Double.parseDouble(res.getString(R.string.latitude));
            double lon = Double.parseDouble(res.getString(R.string.longitude));
            if (!validLocation(lat, lon)) return null;
            return new JewishDate(date, greetings,
                    value(res, json, "jewish_holiday_region", R.string.jewish_holiday_region), lat, lon);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean flag(Resources res, JSONObject json, String key, int id) {
        return json == null ? res.getBoolean(id) : json.optBoolean(key, res.getBoolean(id));
    }

    private static String value(Resources res, JSONObject json, String key, int id) {
        return (json == null ? res.getString(id) : json.optString(key, res.getString(id))).trim();
    }

    static boolean validLocation(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    /** Null when there is no sunset; never silently substitute another city's sunset. */
    LocalDate day(ZonedDateTime now) {
        return day(now, latitude, longitude);
    }

    static LocalDate day(ZonedDateTime now, double latitude, double longitude) {
        if (!validLocation(latitude, longitude)) return null;
        LocalDate date = now.toLocalDate();
        int[] sun = Sun.riseAndSet(date, latitude, longitude, now.getZone());
        if (sun == null) return null;
        return now.getHour() * 60 + now.getMinute() >= sun[1] ? date.plusDays(1) : date;
    }

    static boolean israel(Locale locale, String region) {
        if ("israel".equals(region)) return true;
        if ("diaspora".equals(region)) return false;
        return "IL".equals(locale.getCountry()) || (locale.getCountry().isEmpty()
                && ("he".equals(locale.getLanguage()) || "iw".equals(locale.getLanguage())));
    }

    private static HebrewCalendar calendar(LocalDate day) {
        HebrewCalendar calendar = new HebrewCalendar(android.icu.util.TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(day.toEpochDay() * 86400000L + 43200000L);
        return calendar;
    }

    static String text(LocalDate day, Locale locale) {
        boolean he = "he".equals(locale.getLanguage()) || "iw".equals(locale.getLanguage());
        SimpleDateFormat format = new SimpleDateFormat(he ? "d בMMMM y" : "d MMMM y",
                he ? new ULocale("he_IL@calendar=hebrew;numbers=hebr")
                        : new ULocale(locale.toString() + "@calendar=hebrew"));
        HebrewCalendar calendar = calendar(day);
        format.setCalendar(calendar);
        String text = format.format(calendar.getTime());
        return he ? withoutMillennium(text) : text;
    }

    /** Omit only a year's thousands prefix; preserve day 5 and Adar I/II markers. */
    static String withoutMillennium(String text) {
        return text.replaceFirst(" [א-ט]׳(?=[א-ת׳״]+$)", " ");
    }

    int greeting(LocalDate day, Locale locale) {
        HebrewCalendar c = calendar(day);
        int month = c.get(HebrewCalendar.MONTH), date = c.get(HebrewCalendar.DAY_OF_MONTH);
        // Kislev may have 29 or 30 days; Hanukkah always lasts eight days.
        c.set(HebrewCalendar.MONTH, HebrewCalendar.KISLEV);
        c.set(HebrewCalendar.DAY_OF_MONTH, 1);
        return holiday(month, date, c.getActualMaximum(HebrewCalendar.DAY_OF_MONTH), israel(locale, region));
    }

    /** ICU month numbers: Tishri=0, Kislev=2, Tevet=3, Adar/Adar II=6, Nisan=7, Sivan=9. */
    static int holiday(int month, int day, int kislevDays, boolean israel) {
        if (month == 0) {
            if (day == 1 || day == 2) return R.string.jewish_rosh_hashanah;
            if (day == 10) return R.string.jewish_yom_kippur;
            if (day >= 15 && day <= 21) return R.string.jewish_happy_holiday;
            if (day == 22) return R.string.jewish_happy_holiday;
            if (day == 23 && !israel) return R.string.jewish_happy_holiday;
        }
        if ((month == 2 && day >= 25 && day <= kislevDays)
                || (month == 3 && day >= 1 && day <= 32 - kislevDays)) return R.string.jewish_hanukkah;
        if (month == 6 && (day == 14 || day == 15)) return R.string.jewish_purim;
        if (month == 7 && day >= 15 && day <= (israel ? 21 : 22)) return R.string.jewish_happy_holiday;
        if (month == 9 && (day == 6 || (day == 7 && !israel))) return R.string.jewish_happy_holiday;
        return 0;
    }
}
