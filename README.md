# Sports Widget

A personal-use Android app for MLB schedule, scores, and standings, with a
home-screen widget.

- **In the app**: today's scoreboard with date navigation, per-game linescore
  (inning-by-inning, R/H/E, count and outs), probable pitchers, and division
  standings. While a game is in progress and the screen is in front of you, the
  scoreboard refreshes itself every 20 seconds.
- **On the home screen**: a Glance widget showing today's games three across, with
  team logos, scores and status. Tapping a game opens it in the app; Back returns
  to the schedule. Refreshed every 15 minutes plus a manual ⟳ button, and a failed
  refresh says so rather than blanking the scores it already has.

Live games sort to the top of both surfaces; everything else follows first pitch.

MLB first because it's in season. The data layer is structured so other leagues
can be added behind the same domain models.

## Getting a build

The easy path needs no toolchain at all: every push builds a debug APK in CI and
uploads it as an artifact. Open the latest green run under the repo's Actions tab,
download `sports-widget-debug-apk`, unzip, and install the `.apk` on the phone.
Android will ask about installing from an unknown source.

To build locally instead, Android Studio (Ladybug or newer) ships the JDK and SDK
you need.

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
| Team logo | `midfield.mlbstatic.com/v1/team/{teamId}/spots/{size}` (bootstrap only) |

Logos **ship in the APK** as `app/src/main/assets/team-logos/{teamId}.png` — thirty
files, about 124 KB total, keyed by the same team id the schedule returns. They are
not downloaded at runtime. An earlier version fetched them on first use and that is
what made the widget look broken: the refresh worker pulled all thirty serially
*before* publishing the scoreboard, so tapping ⟳ did nothing visible for minutes.

The `Bootstrap team logo assets` step in CI populates them: it asks StatsAPI for the
current club list, downloads anything missing, and commits the result. Once the
files exist the step is a no-op, so the fetch happens exactly once and never on a
user's device. Adding an expansion team means re-running CI, not editing a table.

The network URL survives for two callers only: that bootstrap step, and a runtime
fallback for a team id with no bundled asset (an All-Star roster, say). The app
loads assets through Coil; the widget decodes them directly, because a widget
renders to `RemoteViews` and needs a real `Bitmap` at composition time. A logo that
cannot be found renders as empty space, never a placeholder box.

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
├── domain/Models.kt          Game, Team, Linescore, Scoreboard, StandingsRow,
│                             Boxscore, and the shared ScoreboardOrder
├── ui/                       Compose screens + ScoreboardViewModel
├── widget/                   Glance widget, WorkManager refresh, state persistence
└── util/TimeFormat.kt        UTC → local, everywhere
```

## Status

CI (`.github/workflows/build.yml`) builds a debug APK on every push and uploads
it as a downloadable artifact, so no local Android toolchain is needed to get an
installable build. Two things are verified there on every run:

- **Unit tests** over a fixture payload spanning live, scheduled,
  extra-inning-final and postponed games, plus the degraded cases: unknown
  fields, missing linescores, absent team blocks.
- **A live contract test** against the real `statsapi.mlb.com`, confirming the
  field names in `StatsApiDto` still exist upstream — that team hydration really
  returns abbreviations, that completed games carry a linescore with R/H/E, and
  that standings return six divisions with records and streaks. It runs in its
  own job and is allowed to fail without blocking the build, so an MLB outage or
  the offseason cannot turn CI red.

Confirmed on a device (Samsung, One UI): the scoreboard, standings, game detail
and box score all render against live data, and the widget populates and refreshes
on the home screen.

Still unconfirmed: how the widget behaves over days under an OEM battery manager —
Samsung and Xiaomi in particular throttle background work aggressively, and no
amount of correct WorkManager usage fully prevents that. Also unverified is live
in-game behaviour over a long session: the 20-second foreground poll and the
box score updating inning by inning have not been watched end to end.

## Adding another league later

`Game`, `Team`, `Linescore` and `Scoreboard` in `domain/Models.kt` carry no MLB
specifics beyond innings. For NFL/NBA/NHL, ESPN's undocumented
`site.api.espn.com` endpoints cover all of them with one shape; add a sibling
client and mapper, and give `MlbRepository` a league parameter. The UI reads
domain models only and shouldn't need to change much beyond labels.
