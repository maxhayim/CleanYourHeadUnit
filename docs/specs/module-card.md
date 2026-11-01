# Module card (automotive extension)

## Today (implemented)

CleanYourHeadUnit's **Modules** page lists every installed activity that declares one of:

- `io.github.maxhayim.cleanyourheadunit.category.MODULE_CARD`
- `com.gathin.tvhome.category.DOCK_CARD` (CleanYourTV's card category, so existing cards appear)

…with `android.intent.action.MAIN`. Tapping the entry opens the activity.

## Planned: live cards on CleanYourHeadUnit Home

*Not implemented yet.* The following is a proposal, open for comment.

Optional `<meta-data>` on the card activity:

| Name | Value | Meaning |
|---|---|---|
| `io.github.maxhayim.cleanyourheadunit.card.SIZE` | `small` \| `wide` | grid footprint on Home |
| `io.github.maxhayim.cleanyourheadunit.card.ORIENTATION` | `landscape` \| `portrait` \| `any` | where the card fits |
| `io.github.maxhayim.cleanyourheadunit.card.DRIVING_SAFE` | `true` \| `false` | the developer states the card shows only glanceable information and needs no typing. Home would hide cards that don't say `true` while a future "driving" state is on |

Rules a card must follow:

- no network access is implied or granted by CleanYourHeadUnit
- no vehicle-control functions: a card may show information, but never send MCU/CAN commands
- the card's app stays independently installed and updated
