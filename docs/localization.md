# Localization and content sources

Status — what is translated, what deliberately is not — lives in `PipBoy_Roadmap.md`. This file is
the part that does not change with status.

## Where the text actually is

- `app/src/main/res/values/strings.xml` (English) and `values-ru/strings.xml` (Russian).
- **`Data.kt` holds the real perk list**: `val perks = listOf(...)`, ~130 entries with a name and a
  description each, Fallout NV lore (V.A.T.S., the companions Boone/Cass/Veronica/Raul/ED-E/Rex,
  chems). The screen is built in code — `StatsController.setupStatsPerks()` over the shared
  `SidebarMenuAdapter` — not from XML. Names and descriptions resolve at runtime through
  `getIdentifier("perk_<id>_name")`, which no static analysis can see.
- The seven `stats_perks_perk1-7` strings in `strings.xml` are dead code: `layout_tab_stats_perks.xml`,
  where they were used, is never shown (`visibility` always `GONE`). They were translated before this
  was discovered.
- `Data.kt` also holds `ringtoneTracks` (`RingtoneTrack`, alarm melodies) — a live list, the source
  for Clock/Melody in `ClockController`. Nothing else is in the file: `val dmiscs` (five fake
  fruit/nut/bird entries) was removed in the first cleanup wave.

## The translation table

`localization/strings_ru.csv`, edited by the user directly in the IDE — re-read it from disk rather
than assuming the copy you saw earlier is current.

**CSV loses spaces at the edges of values.** Spreadsheet editors trim leading and trailing spaces on
a round trip. When a string has meaningful edge spaces (e.g. `" - Level "`), they have to be restored
by hand after copying into `values-ru/strings.xml`, and the value must be wrapped in `"..."` —
Android trims edge whitespace inside `<string>` on its own, which is a second, independent cause of
the same loss.

**Verifying a round of translation.** Compare `source_text` in the CSV against the actual value in
`values/strings.xml`, allowing for the escaping differences between CSV and Android syntax (`\n`,
`…`, apostrophes). That comparison has caught strings where a Russian text had been left in
`values/strings.xml` with no English version. It was done ad hoc — there is no committed script for
it, so it has to be redone each time, and it is worth writing one rather than trusting memory about
what was already caught by hand.

## Interface language

Switched through `MainActivity.attachBaseContext()`, **not** `AppCompatDelegate.setApplicationLocales()`
— the latter needs AppCompat 1.6.0+ and the project is on 1.3.1, which is not being upgraded (the
toolchain is pinned tightly, see [environment.md](environment.md)).

If no language is chosen explicitly (`appLanguage_SPKey`), the context is not substituted at all and
the system keeps control through the ordinary `values-ru` fallback; if one is chosen, the `Locale` is
substituted through `createConfigurationContext()`. `Configuration` in that method is
`android.content.res` — `org.osmdroid.config.Configuration` is already imported under the same name
in the file, which is why the fully qualified name is used instead of a second import.

## The font

`Monofonto` (Typodermic, based on Bell Centennial) had no Cyrillic at all, so it was replaced with
**IBM Plex Mono Bold** (SIL OFL 1.1, licence in `licenses/OFL-IBM-Plex-Mono.txt`) — visually the
closest monospace with full Cyrillic coverage. Four candidates were compared by rendering a status
line in Cyrillic: PT Mono (similar angularity but serifed, rejected), IBM Plex Mono, JetBrains Mono
and Noto Sans Mono (the last two softer and rounder, further from the techno style).

The file exists twice on purpose: `res/font/` for XML, and an `assets/fonts/` copy for
`Typeface.createFromAsset()` in `TypefaceCache`, because `ResourcesCompat.getFont()` / `R.font.*`
cannot be used to draw on a `Canvas` by hand. Both are named **`pipboy_mono.ttf`** — deliberately not
tied to the actual typeface, so the next font swap does not require another rename. All ~26
references were updated: `styles.xml` (13 styles), `layout_tab_mode_select.xml`,
`layout_pipboy2000_wizard.xml` (9 places), and in `MainActivity.kt` `R.font.pipboy_mono`, the asset
path, `pipboyTypeface`, `getPipboyTypeface()`.
