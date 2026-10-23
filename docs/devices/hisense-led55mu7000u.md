# Hisense LED55MU7000U: device and advertising notes

This note records one owner's TV. It is not a tested debloat list.

| Field | Reported value |
|---|---|
| Retail model | Hisense LED55MU7000U |
| Market | China |
| Android / SDK | Android 5.1.1 / 22 |
| Firmware | `01.02.40.01` |
| TV UI version | `JUUI 8.4.02.630` |

## Advertising behavior

The boot ad remained after the TV's advertising preference was changed. The owner restored that preference to its original value (`0`). On this firmware, the attempted `pm disable-user` request for the advertising service was rejected, so it remains enabled. This is a single-TV report; do not retry the same request on this build.

## Debloat status

No package list has been validated for this TV, and the stock launcher has not been disabled.
