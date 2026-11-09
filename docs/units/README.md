# Head units

One page per head unit that someone has actually checked with CleanYourHeadUnit, grouped by the firmware it runs. Brand names on the box vary; the firmware family is what decides which [device profile](../../data/profiles) applies.

Pages only say what was observed on that unit. A unit listed here is not "supported" beyond what its page says. Never put a serial number, MAC address, IP address, radio PIN or QR code on a page.

## FYT (`com.syu.ms`)

| Unit | Chip | Real Android | Checked | Page |
|---|---|---|---|---|
| Fortdows for Toyota Camry 2012–2014 (JBL), 10.1" | SC7731E / UIS8141E | 8.1 (Settings say 13) | App runs, read-only. No ADB (USB port is host-only). | [fortdows-camry-2012-2014](fortdows-camry-2012-2014.md) |

## Sergey Kozlov firmware (declared)

| Unit | Chip | Real Android | Checked | Page |
|---|---|---|---|---|
| Phoenix Radio for Ford F-150 2009–2014 (vertical, "Tesla-style"), owner-reported Sergey Kozlov firmware | not yet read | not yet read | First report pending | none yet |

## MTCD / MTCE, DoFun / TW, ALPS / YT9216, other

No real unit reported yet.

## Adding a unit

The easy way: In the app, **Diagnostics → Report this unit** opens the form in the unit's browser, already filled in with the model, chip, real and claimed Android, firmware, screen and package counts (never serials, MAC or IP addresses, or network and account names); you add the car and what worked, and press Submit. It needs a free GitHub account. Rather not sign in on the car? **QR code** opens the same form on your phone, and **Share** sends the whole report (LocalSend works). Each report becomes a page here.

Or open a **Compatibility report** issue by hand, or a pull request adding a page here. A page needs:

- the retail name and the car it's made for
- what CleanYourHeadUnit's Dashboard shows: model, chip, Android (real and claimed), screen, firmware family
- the Debloater's counts per category
- how you connected (or couldn't): USB debugging, Wireless debugging, USB port role
- what you changed, if anything, and whether it survived a reboot and an ignition cycle
- which apps from the catalog work on it
