package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Builds every screen of the real app against the simulated units in tests/fixtures, in landscape
 * and portrait, and checks what it must never show. With -Pscreenshots it also writes PNGs to
 * docs/screenshots. These are renders of the app's own code with simulated data, not photos of a unit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreensTest {

    @Before
    public void setUp() {
        MainActivity.background = Runnable::run;
        Theme.reset();
    }

    MainActivity open(String fixture, String qualifiers) throws Exception {
        RuntimeEnvironment.setQualifiers(qualifiers);
        Context c = RuntimeEnvironment.getApplication();
        SimUnit.install(c, fixture);
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ac.get();
        // Use the fixture's own properties, as a scan of that unit would read them.
        JSONObject props = SimUnit.fixture(fixture).getJSONObject("props");
        Iterator<String> it = props.keys();
        while (it.hasNext()) {
            String k = it.next();
            a.unit.props.put(k, props.getString(k));
        }
        a.unit.matchProfiles(a, Prefs.declaredFirmware(a));
        a.show("dashboard");
        ShadowLooper.idleMainLooper();
        return a;
    }

    static List<String> texts(View v) {
        List<String> out = new ArrayList<>();
        if (v instanceof TextView) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(texts(((ViewGroup) v).getChildAt(i)));
        return out;
    }

    static String all(MainActivity a) {
        return String.join("\n", texts(a.getWindow().getDecorView()));
    }

    static void shot(MainActivity a, String name) throws Exception {
        String dir = System.getProperty("screenshots");
        ShadowLooper.idleMainLooper();
        View root = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        assertTrue("screen is blank", b.getPixel(w / 2, h / 2) != 0 || b.getPixel(10, 10) != 0);
        if (dir == null) return;
        new File(dir).mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void fytLandscape1280x720() throws Exception {
        MainActivity a = open("fyt-android10-1280x720.json", "w1280dp-h720dp-land-mdpi");
        String t = all(a);
        assertTrue(t, t.contains("HU-7862 (simulated)"));
        assertTrue(t.contains("Android 10"));
        assertTrue(t.contains("Unverified profile") || t.contains("Experimental profile") || t.contains("Generic rules only"));
        assertFalse("serial shown", t.contains("FAKE1234567890"));
        shot(a, "dashboard-1280x720");

        a.show("debloater");
        t = all(a);
        assertTrue(t, t.contains("Protected"));
        assertTrue(t.contains("Unknown"));
        shot(a, "debloater-1280x720");

        Screens.Debloater.filter = Rules.PROTECTED;
        a.show("debloater");
        t = all(a);
        assertTrue(t, t.contains("com.syu.ms"));
        assertFalse("protected package offered", t.contains("Add to plan"));
        Screens.Debloater.filter = Rules.OPTIONAL;

        a.show("privacy");
        t = all(a);
        assertTrue(t, t.contains("Privacy Shield"));
        assertTrue(t.contains("Off"));                      // the firewall never starts by itself
        shot(a, "privacy-1280x720");
        a.push("Telemetry", PrivacyScreen.telemetry(a));
        t = all(a);
        assertTrue(t, t.contains("com.adups.fota"));
        assertTrue(t.contains("Source:"));
        shot(a, "telemetry-1280x720");
        a.back();

        a.show("launchers");
        t = all(a);
        assertTrue(t, t.contains("KITT Launcher.2"));
        assertTrue(t.contains("by @tdater"));
        shot(a, "launchers-1280x720");

        a.show("hub");
        t = all(a);
        assertTrue(t, t.contains("@Gathin23") || t.contains("@gathin23"));
        assertTrue(t.contains("Sergey Kozlov"));
        assertFalse("invented username", t.contains("@sergey"));
        assertTrue("official page button", t.contains("Website"));
        shot(a, "community-1280x720");

        a.show("modules");
        t = all(a);
        assertTrue(t, t.contains("RetroDeck"));
        assertTrue(t.contains("Automotive support planned"));
        shot(a, "modules-1280x720");

        a.show("diagnostics");
        assertFalse(all(a).contains("02:00:00:aa:bb:cc"));
        String report = Report.build(a);
        assertFalse(report, report.contains("FAKE1234567890"));
        assertFalse(report.contains("02:00:00:aa:bb:cc"));
        shot(a, "diagnostics-1280x720");

        a.show("backup");
        shot(a, "backup-1280x720");
        a.show("themes");
        shot(a, "themes-1280x720");
        a.show("settings");
        shot(a, "settings-1280x720");
    }

    @Test
    public void alpsLandscape1024x600() throws Exception {
        MainActivity a = open("alps-android8-1024x600.json", "w1024dp-h600dp-land-mdpi");
        assertTrue(all(a).contains("Android 8"));
        shot(a, "dashboard-1024x600");
        a.show("debloater");
        shot(a, "debloater-1024x600");
    }

    @Test
    public void kitkat800x480() throws Exception {
        MainActivity a = open("kitkat-android44-800x480.json", "w800dp-h480dp-land-mdpi");
        assertTrue(all(a).contains("Android 4.4"));
        shot(a, "dashboard-800x480");
    }

    @Test
    public void dofunPortrait1080x1920() throws Exception {
        MainActivity a = open("dofun-android12-portrait.json", "w720dp-h1280dp-port-hdpi");
        assertTrue(a.portrait());
        shot(a, "dashboard-portrait-1080x1920");
        a.push("Telemetry", PrivacyScreen.telemetry(a));
        String t = all(a);
        assertTrue(t, t.contains("com.tw.jar1"));
        assertTrue(t.contains("Malware indicator"));
        shot(a, "telemetry-portrait-1080x1920");
        a.back();
        a.show("launchers");
        shot(a, "launchers-portrait-1080x1920");
    }

    @Test
    public void wide1920x720AndThemes() throws Exception {
        MainActivity a = open("fyt-android10-1280x720.json", "w1920dp-h720dp-land-mdpi");
        shot(a, "dashboard-1920x720");
        for (String id : Theme.BUILTIN) {
            Prefs.setTheme(a, id);
            a.refresh();
            ShadowLooper.idleMainLooper();
            assertEquals(id, Ui.t(a).id);
            shot(a, "theme-" + id);
        }
        Prefs.setTheme(a, "midnight");
    }

    /** assets/logo.png for the README, drawn from the app's own launcher icon. */
    @Test
    public void logo() throws Exception {
        String dir = System.getProperty("screenshots");
        android.graphics.drawable.Drawable d = RuntimeEnvironment.getApplication().getResources().getDrawable(R.drawable.ic_launcher, null);
        Bitmap b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 512, 512);
        d.draw(new Canvas(b));
        assertTrue(b.getPixel(256, 256) != 0 || b.getPixel(256, 200) != 0);
        if (dir == null) return;
        File out = new File(System.getProperty("repo.root"), "assets/logo.png");
        out.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(out)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        for (String id : Theme.BUILTIN) {
            File src = new File(dir, "theme-" + id + ".png");
            if (src.exists()) java.nio.file.Files.copy(src.toPath(), new File(System.getProperty("repo.root"), "themes/" + id + "/preview.png").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    public void homeLauncherRenders() throws Exception {
        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
        SimUnit.install(RuntimeEnvironment.getApplication(), "fyt-android10-1280x720.json");
        assertFalse("CYHU Home is off until the owner turns it on", HomeActivity.enabled(RuntimeEnvironment.getApplication()));
        HomeActivity h = Robolectric.buildActivity(HomeActivity.class).setup().get();
        String t = String.join("\n", texts(h.getWindow().getDecorView()));
        assertTrue(t, t.contains("Change Home"));
        assertNotNull(h.getWindow().getDecorView());
    }
}
