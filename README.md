# Sports Widget

A personal-use Android app for MLB schedule, scores, and standings, with a
home-screen widget.

- **In the app**: today's scoreboard with date navigation, per-game linescore
  (inning-by-inning, R/H/E, count and outs), probable pitchers, and division
  standings. While a game is in progress and the screen is in front of you, the
  scoreboard refreshes itself every 20 seconds.
- **On the home screen**: a Glance widget listing today's games with scores and
  status, refreshed every 15 minutes plus a manual ⟳ button.

MLB first because it's in season. The data layer is structured so other leagues
can be added behind the same domain models.

## Build

Requires Android Studio (Ladybug or newer) — it ships the JDK and SDK you need.

```
git clone <this repo>
cd Sports-Widget
./gradlew assembleDebug          # or open in Android Studio and Run
./gradlew installDebug           # to a connected device/emulator
./gradlew test                   # data-layer unit tests
```

No API keys, no accounts, no config files. Nothing to fill in before first run.

To add the widget: long-press the home screen → Widgets → Sports Widget.

## Where the data comes from

MLB's own public StatsAPI at `statsapi.mlb.com` — the same backend MLB's site
uses. No key and no auth, but it is **undocumented and unversioned**, which
drives two decisions in this codebase:

1. Every field in `StatsApiDto.kt` is nullable with a default, and JSON parsing
   ignores unknown keys. A field that gets renamed or dropped upstream shows as
   missing data in one row rather than an exception that blanks the screen.
2. Nothing above `StatsApiMapper.kt` knows the wire format. If a value stops
   appearing, open the endpoint in a browser, compare the field names, and fix
   them in that one file.

Endpoints used:

| What | URL |
| --- | --- |
| Day's games | `/api/v1/schedule?sportId=1&date=YYYY-MM-DD&hydrate=team,linescore,probablePitcher,venue` |
| Standings | `/api/v1/standings?leagueId=103,104&season=YYYY&standingsTypes=regularSeason&hydrate=team,division` |
| Game linescore | `/api/v1/game/{gamePk}/linescore` |

Because it's someone else's server, requests are cached on disk by OkHttp and the
repository puts a floor under refresh frequency: 15s while a game is live, 5
minutes otherwise, 30 minutes for standings. The 20-second foreground poll is
therefore a ceiling on *checking*, not on requests.

## Why the widget is 15 minutes and the app isn't

WorkManager's minimum period for repeating work is 15 minutes; the platform
clamps anything shorter. Doze can stretch it further when the phone is idle.
That's a platform limit, not a tuning choice — so the widget shows the age of its
numbers ("4m ago") rather than implying they're current, and live tracking lives
in the app where a foreground coroutine can poll fast and stop the moment the
screen is no longer resumed.

The widget renders entirely from a snapshot persisted in its Glance state. The
launcher can recreate a widget process at any time with no chance to make a
network call, so a failed refresh leaves the last known scores on screen instead
of a spinner.

## Layout

```
app/src/main/java/com/kartus/sportswidget/
├── data/
│   ├── StatsApiDto.kt        wire models — the only file that knows StatsAPI's shape
│   ├── StatsApiMapper.kt     DTO → domain, with fallbacks for absent data
│   ├── StatsApiClient.kt     OkHttp + kotlinx.serialization
│   ├── MlbRepository.kt      caching and refresh floors
│   └── ServiceLocator.kt     the whole dependency graph
├── domain/Models.kt          Game, Team, Linescore, Scoreboard, StandingsRow
├── ui/                       Compose screens + ScoreboardViewModel
├── widget/                   Glance widget, WorkManager refresh, state persistence
└── util/TimeFormat.kt        UTC → local, everywhere
```

## Status

The data layer is covered by unit tests (`./gradlew test`) over a fixture payload
spanning live, scheduled, extra-inning-final and postponed games, plus the
degraded cases: unknown fields, missing linescores, absent team blocks.

The UI and widget have **not** been run on a device yet — they were written in an
environment without the Android SDK. Expect to fix a compile error or two on
first build, most likely an import in the Compose or Glance layers.

The DTO field names were also written without a live response to check against,
since the environment couldn't reach `statsapi.mlb.com`. If a screen renders but
a specific value is blank, that's the first place to look — see note 2 above.

## Adding another league later

`Game`, `Team`, `Linescore` and `Scoreboard` in `domain/Models.kt` carry no MLB
specifics beyond innings. For NFL/NBA/NHL, ESPN's undocumented
`site.api.espn.com` endpoints cover all of them with one shape; add a sibling
client and mapper, and give `MlbRepository` a league parameter. The UI reads
domain models only and shouldn't need to change much beyond labels.
