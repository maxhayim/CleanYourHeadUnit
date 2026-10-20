package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Uses real Android resources, including an app locale different from the process default. */
public final class LocalizationTest extends Instrumentation {
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        result.putString("class", getClass().getName());
        result.putString("test", "russianResourcesAndFormattingBoundaries");
        result.putInt("numtests", 1);
        result.putInt("current", 1);
        sendStatus(1, result);
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            verifyFormatting(resources("en"), resources("ru"));
            sendStatus(0, result);
            result.putString("stream", "\nPASS: Russian app locale, race rounding, greeting precedence, calendar and storage units\n");
        } catch (Throwable failure) {
            result.putString("stack", Log.getStackTraceString(failure));
            sendStatus(-2, result);
            result.putString("stream", "\nFAIL: " + Log.getStackTraceString(failure));
        } finally {
            Locale.setDefault(original);
        }
        finish(Activity.RESULT_OK, result);
    }

    private Resources resources(String language) {
        Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language));
        return getTargetContext().createConfigurationContext(config).getResources();
    }

    private void verifyFormatting(Resources en, Resources ru) throws Exception {
        for (String name : new String[]{"FP1", "FP2", "FP3", "SPRINT QUALI", "SPRINT", "QUALIFYING", "RACE"}) {
            RaceWeekend.Session session = new RaceWeekend.Session(name, Instant.EPOCH);
            localized(session.title(en), session.title(ru));
            localized(session.shortName(en), session.shortName(ru));
        }
        long[] seconds = {0, 20, 60, 61, 3599, 3600, 86399, 86400, 97900};
        long[][] counts = {{1}, {1}, {1}, {2}, {1, 0}, {1, 0}, {1, 0}, {1, 0}, {1, 3}};
        for (int i = 0; i < seconds.length; i++) {
            Duration until = Duration.ofSeconds(seconds[i]);
            String countdown = RaceWeekend.countdown(ru, until);
            localized(RaceWeekend.countdown(en, until), countdown);
            numbers(countdown, counts[i]);
        }
        for (String name : new String[]{"dussehra", "diwali"}) {
            Festival festival = Festival.parse(name, "", "", "");
            localized(festival.greetingText(en), festival.greetingText(ru));
        }
        String custom = Festival.parse("dussehra", "", "  С праздником!\u202E  ", "").greetingText(ru);
        check(custom.equals("С праздником!"), "Custom greeting must win and remain sanitized: " + custom);
        check(Festival.parse("onam", "", "", "").greetingText(ru).isEmpty(), "Unknown festival must keep the normal greeting");
        localized(Shamsi.today(en), Shamsi.today(ru));

        Method formatGb = MainActivity.class.getDeclaredMethod("formatGb", Resources.class, long.class);
        formatGb.setAccessible(true);
        check(((String) formatGb.invoke(null, ru, 0L)).isEmpty(), "Nonpositive storage must stay hidden");
        long gib = 1024L * 1024 * 1024;
        String fraction = (String) formatGb.invoke(null, ru, 9 * gib + gib / 2);
        localized((String) formatGb.invoke(null, en, 9 * gib + gib / 2), fraction);
        check(fraction.startsWith("9,5 "), "Russian app resources must control the decimal separator: " + fraction);
        String whole = (String) formatGb.invoke(null, ru, 12 * gib);
        numbers(whole, 12);
        localized((String) formatGb.invoke(null, en, 12 * gib), whole);
    }

    private static void localized(String english, String russian) {
        check(!english.equals(russian), "Russian still uses the English output: " + russian);
        check(russian.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC),
                "Russian output lacks Cyrillic: " + russian);
    }

    private static void numbers(String text, long... expected) {
        Matcher matcher = NUMBER.matcher(text);
        for (long value : expected) {
            check(matcher.find() && Long.parseLong(matcher.group()) == value, "Wrong rounded quantity: " + text);
        }
        check(!matcher.find(), "Unexpected extra quantity: " + text);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
