"""docs/REMOTE.md closes ADB the way the README does, and checks it while the tunnel is still up.

The README FAQ "Isn't ADB access a security hole?" closes ADB in two steps: USB debugging, Network/ADB
debugging and Wireless debugging off, the USB debugging authorisations revoked, the Wireless debugging
port checked before the reboot, then a reboot and `tools/adb-closed.py <tv ip>`. PROMPT.md and AGENTS.md
("Close ADB afterwards") say the same. REMOTE.md's "Afterwards" only switched off USB debugging and
Wireless debugging and then removed the Tailscale access. Yet its own Quick start link has the owner turn
on a Network/ADB debugging toggle where there is one, and its Connecting section opens port 5555 with
`adb -s 100.x.y.z:<connect port> tcpip 5555`. So that toggle stayed on, nothing was revoked, nothing
checked the port, and once the access is gone nobody outside that home can check it any more.

The checks below read the section's numbered steps with the README test's reader: every toggle and the
revoke in the first step, with the Wireless debugging port checked before the reboot; the reboot and
`adb-closed.py <tv ip>` in the second, once the TV is back on the tunnel (adb-closed.py reads a TV that
does not answer as closed, and on an XGIMI Horizon S Pro Tailscale did not come back after a reboot by
itself); and the access removed last, after every check.

The Wireless debugging port is read off the screen again at close-up, just before it goes off, as the
README notes it before switching it off. The connect port from Connecting is out of date by then:
Wireless debugging takes a new port each time it starts, and on a TCL 65Q7D Pro the `tcpip 5555` that
Connecting runs straight after `adb connect` restarted adbd and moved it. adb-closed.py reads a port
nothing listens on as closed, so checking the old number passed while the new one could still be open.
mDNS doesn't cross the tunnel, so the screen is the only place to read it. Step 2 keeps the README's
remedy: if Wireless debugging came back after the reboot, switch it off and check its new port.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_accessibility_flag_note import section  # noqa: E402
from test_adb_closed import SCRIPT  # noqa: E402
from test_readme_wireless_debugging import HEADING, README, SUMMARY, flat, steps  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
REMOTE = (ROOT / "docs" / "REMOTE.md").read_text(encoding="utf-8")
CHECK = "`python3 tools/adb-closed.py <tv ip>` must say the port is closed"
PORT_CHECK = "`python3 tools/adb-closed.py <tv ip> <port>` must say that port is closed"
READ_AGAIN = ("have them read you the port on the Wireless debugging screen again just before they"
              " switch it off")
TCL = ROOT / "examples" / "tcl-q7c-google-tv" / "README.md"


def numbered(text, phrase):
    """The numbers of the steps that contain `phrase`, in order."""
    return [n for n, step in sorted(steps(text).items()) if phrase in step]


class AfterwardsTest(unittest.TestCase):
    def setUp(self):
        self.text = section(REMOTE, "## Afterwards")
        self.steps = steps(self.text)

    def step(self, n):
        self.assertIn(n, self.steps, f"Afterwards has no step {n}: {sorted(self.steps)}")
        return self.steps[n]

    def test_step_one_switches_every_debugging_toggle_off_and_revokes(self):
        first = self.step(1)
        for toggle in ("**USB debugging**", "**Network/ADB debugging**", "**Wireless debugging**"):
            self.assertIn(toggle, first)
        self.assertIn("revoke the USB debugging authorisations", first)

    def test_the_wireless_debugging_port_is_checked_before_the_reboot(self):
        # After a reboot the old number reads closed either way (the README's step 1 says why).
        first = self.step(1)
        self.assertIn(PORT_CHECK, first)
        self.assertIn("before the reboot", first)
        self.assertEqual(numbered(self.text, "reboot the TV"), [2])

    def test_the_port_checked_is_read_again_at_close_up(self):
        # Checking the connect port from Connecting passes whatever the live port does: it moved
        # when `tcpip 5555` restarted adbd, and a port nothing listens on reads closed.
        first = self.step(1)
        self.assertIn(READ_AGAIN, first)
        self.assertLess(first.index(READ_AGAIN), first.index(PORT_CHECK))
        self.assertIn("mDNS doesn't cross the tunnel, so only they can see it", first)
        self.assertIn("Don't reuse the connect port from Connecting", first)
        self.assertIn("Wireless debugging takes a new port each time it starts", first)
        self.assertIn("Wireless debugging takes a new port each time it starts", flat(README))
        self.assertIn("adb -s 100.x.y.z:<connect port> tcpip 5555", section(REMOTE, "## Connecting"))

    def test_the_tv_it_cites_saw_the_port_move_after_tcpip(self):
        first = self.step(1)
        self.assertIn("it changed as soon as `tcpip 5555` restarted adbd", first)
        self.assertIn(
            "(../examples/tcl-q7c-google-tv/README.md#connecting-wireless-debugging-only-and-its-port-moves)",
            first)
        tcl = TCL.read_text(encoding="utf-8")
        self.assertIn("\n### Connecting: Wireless debugging only, and its port moves\n", tcl)
        self.assertIn("## Second unit: 65Q7D Pro", tcl)
        self.assertIn("`adb tcpip 5555` restarts adbd, so the connect port shown on the TV changes"
                      " afterwards", flat(tcl))

    def test_the_port_is_checked_after_the_reboot(self):
        second = self.step(2)
        self.assertTrue(second.startswith("They reboot the TV."), second)
        self.assertIn(CHECK, second)
        self.assertIn("Wireless debugging must still be off in Developer options", second)
        # The README's remedy when it is back after the reboot, on a port nobody has read yet.
        self.assertIn("If it came back, they switch it off and you check its new port as in step 1", second)
        self.assertIn("if it came back, switch it off and check its new port as in step 1", flat(README))

    def test_a_tv_that_is_not_back_on_the_tunnel_is_not_read_as_closed(self):
        # adb-closed.py exits 0 on a timeout too ("closed, unless the TV is off or not at this
        # address"), and with option A Tailscale on the TV may not be up again after the reboot.
        second = self.step(2)
        self.assertIn("Once it is back on the tunnel", second)
        self.assertIn('"did not answer" proves nothing', second)
        self.assertIn("did not answer in", SCRIPT.read_text(encoding="utf-8"),
                      "adb-closed.py no longer says 'did not answer': the step quotes it")
        self.assertIn("(../examples/xgimi-horizon-s-pro/debloat.txt)", second)
        xgimi = (ROOT / "examples" / "xgimi-horizon-s-pro" / "debloat.txt").read_text(encoding="utf-8")
        self.assertIn("Tailscale does not come back after a reboot by itself", xgimi)

    def test_the_port_is_checked_before_the_access_goes(self):
        # Once the share, the TV or the helper is gone, nobody outside that home can run the check.
        checks = numbered(self.text, "adb-closed.py")
        removal = numbered(self.text, "remove the access")
        self.assertNotEqual(checks, [], "Afterwards never runs tools/adb-closed.py")
        self.assertEqual(len(removal), 1, removal)
        self.assertLess(max(checks), removal[0])

    def test_the_access_goes_last(self):
        self.assertEqual(sorted(self.steps), [1, 2, 3])
        self.assertTrue(self.step(3).startswith("Then remove the access:"), self.step(3))

    def test_the_placeholders_are_ones_the_page_gives_the_reader(self):
        intro = flat(self.text.split("\n1. ", 1)[0])
        self.assertIn("`<tv ip>` is the address you connected to: `100.x.y.z`", intro)
        self.assertIn("option B", intro)
        # `<port>` is the one step 1 has them read off the Wireless debugging screen at close-up, not
        # the connect port from Connecting, which is out of date by then.
        first = self.step(1)
        self.assertIn(READ_AGAIN, first)
        self.assertLess(first.index(READ_AGAIN), first.index("<port>"))
        self.assertNotIn("<connect port>", self.text)

    def test_it_names_the_readme_faq_it_follows(self):
        self.assertEqual(README.count(SUMMARY), 1, f"README.md no longer has {SUMMARY!r}")
        self.assertIn(f'"{HEADING}"', flat(self.text))
        self.assertIn("(../README.md)", self.text)


class ConnectingStillOpensThePortTest(unittest.TestCase):
    def test_connecting_opens_5555_that_the_close_up_checks(self):
        # Why the close-up needs the check at all: Connecting leaves port 5555 listening.
        self.assertIn("tcpip 5555", section(REMOTE, "## Connecting"))


class NumberedReaderTest(unittest.TestCase):
    def test_it_finds_a_phrase_in_wrapped_steps(self):
        text = "Intro.\n\n1. One\n   reboot.\n2. Two.\n3. Three\n   reboot too.\n"
        self.assertEqual(numbered(text, "reboot"), [1, 3])


if __name__ == "__main__":
    unittest.main()
