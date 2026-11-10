package io.github.maxhayim.cleanyourheadunit.adb;

import java.util.function.Function;

/** For tests outside this package: an AdbConnection to a simulated adbd with the given shell. */
public final class TestAdbd {
    private TestAdbd() {}

    public static AdbConnection connect(Function<String, String> shell) throws Exception {
        FakeAdbd d = new FakeAdbd();
        d.shell = shell;
        return AdbConnection.connect(d, AdbKey.generate(), "test@unit", null);
    }
}
