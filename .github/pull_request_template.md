## What this changes

## How it was tested

- [ ] `python3 -m unittest tests.test_cyhu`
- [ ] `python3 tools/cyhu.py catalog validate`
- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` (app changes)
- [ ] On a simulated unit (`--demo …`)
- [ ] On a real head unit: which one, which checklist steps

## Checks

- [ ] Nothing here can disable a Protected or Unknown package automatically
- [ ] Every change to a unit stays reversible and recorded
- [ ] Developers are credited by their real GitHub username; links are official sources only
- [ ] Screenshots are real, or labelled as renders/mockups, and show no personal information
- [ ] No analytics, ads, tracking or accounts
