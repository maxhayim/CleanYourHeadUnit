package io.github.maxhayim.cleanyourheadunit;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.util.List;

/** Firmware compatibility: every device profile, what matched here, and how sure we are. */
final class Firmware {
    private Firmware() {}

    static View page(MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Firmware compatibility", "Profiles are matched by what the unit runs (system services, build strings), never by brand or screen size."));
        LinearLayout here = Ui.card(a);
        here.addView(Ui.text(a, "This unit", 17, th.text, true));
        Ui.add(here, Ui.kv(a, "Matched", Screens.Diagnostics.profileNames(a.unit)), 8);
        here.addView(Ui.kv(a, "Build", a.unit.props.get("ro.build.display.id")));
        here.addView(Ui.kv(a, "Platform", a.unit.chipset()));
        Ui.add(l, here, 14);
        for (JSONObject p : Data.profiles(a)) {
            boolean matched = a.unit.profiles.contains(p);
            LinearLayout c = Ui.card(a);
            LinearLayout top = Ui.hbox(a);
            top.addView(Ui.text(a, p.optString("name"), 16, th.text, true), Ui.weight(1));
            if (matched) Ui.add(top, Ui.pill(a, "This unit", th.accent), 6);
            Ui.add(top, Ui.pill(a, Screens.cap(p.optString("status")), Screens.statusColor(a, p.optString("status"))), 6);
            c.addView(top);
            Ui.add(c, Ui.dim(a, p.optString("about")), 6);
            List<String> chips = Data.strings(p.optJSONArray("chipsets"));
            if (!chips.isEmpty()) Ui.add(c, Ui.kv(a, "Chipsets", TextUtils.join(", ", chips)), 6);
            JSONObject dev = p.optJSONObject("developer");
            if (dev != null) c.addView(Ui.kv(a, "Developer", dev.isNull("github") ? dev.optString("name") : "@" + dev.optString("github")));
            for (String lim : Data.strings(p.optJSONArray("limitations"))) Ui.add(c, Ui.text(a, "•  " + lim, 13, th.dim, false), 4);
            Ui.add(l, c, 10);
        }
        Ui.add(l, Ui.dim(a, "CleanYourHeadUnit never flashes, downloads or redistributes firmware, and never talks to the MCU or CAN bus."), 12);
        return l;
    }
}
