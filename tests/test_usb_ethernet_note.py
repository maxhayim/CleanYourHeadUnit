"""Pins the USB-Ethernet warning in PROMPT.md and docs/TROUBLESHOOTING.md (#737).

The prompt used to ask only what was plugged into the USB ports, which an owner can answer
"nothing" while the network itself runs over a USB Ethernet adapter on that port. On the sets in
#737 the port goes to ADB device mode as soon as debugging is on, so the adapter - and with it the
network ADB session - is gone, and it only comes back with debugging off. These tests keep the
question asked, keep the two dumpsys checks that tell the two interfaces apart, and keep the claim
tied to the list header that first reported it (examples/tcl-65c735) instead of drifting away from it.
Whitespace is flattened before matching because every file here wraps its prose.
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
PROMPT = ROOT / "PROMPT.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
C735 = ROOT / "examples" / "tcl-65c735" / "debloat.txt"


def flat(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


def bullet(text, opening):
    """One flattened markdown bullet, from its opening words to the next top-level bullet."""
    start = text.index(opening)
    end = text.find(" - **", start)
    return text[start:end if end != -1 else len(text)]


class PromptTest(unittest.TestCase):
    def test_the_prompt_names_the_usb_ethernet_case_before_it_starts(self):
        text = flat(PROMPT)
        head = text[:text.index("---")]          # the "Before you start, on the TV" paragraph
        self.assertIn("USB Ethernet", head)
        self.assertIn("falls back to Wi-Fi", head)
        self.assertIn("one sitting", head)

    def test_the_backup_step_asks_about_the_network_not_only_the_ports(self):
        text = flat(PROMPT)
        step = bullet(text, "- Ask me what is plugged into the TV's USB ports")
        self.assertIn("whether the TV's network comes through a USB Ethernet adapter", step)
        self.assertIn('"nothing plugged in" can be true', step)
        self.assertIn("dumpsys usb | grep host_connected", step)
        self.assertIn("dumpsys connectivity | grep -o 'Transports: [A-Z]*'", step)
        # Both greetings are needed: the check is worthless if the agent never learns which it is.
        self.assertIn("WIFI", step)
        self.assertIn("ethernet", step.lower())
        self.assertIn("Wireless debugging", step)

    def test_the_closing_step_brings_the_adapter_back(self):
        text = flat(PROMPT)
        step = bullet(text, "- When you finish, close network ADB")
        self.assertIn("replug the adapter", step)
        self.assertIn("on Ethernet again", step)
        # The port comes back with debugging off, so the check has to stay after that switch.
        self.assertLess(step.index("switch USB debugging"), step.index("replug the adapter"))


class TroubleshootingTest(unittest.TestCase):
    def setUp(self):
        self.text = flat(TROUBLE)
        self.entry = bullet(self.text, "- **USB devices go dead while USB debugging is on**")

    def test_the_entry_covers_the_network_and_not_just_power(self):
        for phrase in (
            "Xiaomi Mi Box S",
            "MiTV-AFMU0",
            "issues/737",
            "USB Ethernet adapter sits on that same port",
            "falls back to Wi-Fi",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, self.entry)

    def test_the_entry_gives_both_dumpsys_signatures(self):
        self.assertIn("dumpsys usb | grep host_connected", self.entry)
        self.assertIn("dumpsys connectivity | grep -o 'Transports: [A-Z]*'", self.entry)
        self.assertIn("host_connected` prints `false", self.entry)

    def test_the_entry_keeps_the_eth0_trap_apart_from_the_adapter(self):
        self.assertIn("NO-CARRIER", self.entry)
        self.assertIn("eth1", self.entry)

    def test_the_two_firmware_timings_stay_separate(self):
        self.assertIn("as soon as debugging is on", self.entry)
        self.assertIn("until a reboot", self.entry)
        self.assertIn("sys.usb.config=adb", self.entry)

    def test_the_entry_does_not_promise_a_shell_way_back(self):
        self.assertIn("no way back from the shell", self.entry)
        self.assertIn("stops `adbd`", self.entry)

    def test_the_entry_links_a_list_that_still_says_the_same_thing(self):
        link = "[that list's header](../examples/tcl-65c735/debloat.txt)"
        self.assertIn(link, self.entry)
        self.assertTrue(C735.is_file())
        header = flat(C735)
        self.assertIn("wired through a USB Ethernet adapter (eth1)", header)
        self.assertIn("sys.usb.config=adb", header)
        self.assertIn("Switch USB debugging off once you are done", header)


if __name__ == "__main__":
    unittest.main()
