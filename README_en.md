# CRN Display Native Extended

English | [中文](https://github.com/Hybrizat/CRNDisplayNext/blob/Neoforge-1.21.1/README.md)

Create: Railways Navigator Display Native Extended is an add-on mod for [Create: Railways Navigator](https://github.com/MisterJulsen/Create-Train-Navigator), providing JR East-style platform departure boards, train destination indicators and in-car VIS displays.

---

## Display Types

| Display | Registry name | Placement | Content |
| --- | --- | --- | --- |
| JRE Concourse Display | `jre_station` | Station | Departure board with a 番線 (platform) column |
| JRE Platform Display | `jre_platform` | Platform | Departure board without the platform column, optional direction arrows |
| JRE Front Destination | `jre_front` | Train front | Train category block + destination / line name |
| JRE Side Destination | `jre_side` | Train side | Next stop / destination / line name, three layouts |
| JRE Passenger VIS | `jre_vis` | Inside the train | Route map (header + station ribbon) |
| Custom Image Display | `jre_graphics` | Anywhere | Shows an image from the internet |

---

## Features

### Through Services (直通区段)

- The destination is resolved across sections, to the **end of the whole through group** rather than stopping at the handover station
- Platform and concourse boards are correct too: CRN only attaches the journey snapshot to displays mounted on a train, so the client asks the server for each train's through destination, caches it per (train, running session), and keeps showing the previous value while it refreshes
- Colours follow the section the train is working, and the VIS ribbon is colour-banded per section

### Destinations

- Resolved from the **section the individual call belongs to**. Every row of a departure board is one call, so a row cannot be labelled from wherever the train happens to be at that moment
- Honours CRN's **station blacklist**: when the terminus is blacklisted, it walks back to the nearest station that may be named
- Cyclic and turn-back timetables no longer mistake the first stop for the terminus

### Departure Boards

- Train text can be the train name, the train category or the line name
- Category / line colour background (optional)
- Time format, showing the same train multiple times, and per-column widths (`-1` = auto, aligned to the widest text on the whole board)
- Optional 経由 (via) stations
- The platform display can show ← / → direction arrows, matching each row's station name or platform number against the left/right filters you configure

### Destination Indicators

- **Front**: the left block shows the train category while the train is standing and the line name while it is running; the B column can show either the destination or the line name, and stays empty when it would repeat the left block
- **Side**: the left block always names the line and never changes when the train comes to a stand
  - Next Stop (2-row): line name on the left, destination top right, 次は ○○ bottom right
  - Destination: line name + destination
  - Line name: the line name fills the whole display

### Passenger VIS

- Upper part: category block, destination, current / next station, time
- Lower part: route ribbon, colour-banded per section; the pointer marks the current station while dwelling, and a green arrow points at the station ahead while running
- Stations already passed are greyed out, and nothing is projected into the next cycle
- Toggles for line colour, do-not-board text, connections, on-board stats and carriage index

### Custom Image Display

- Point it at an http(s) URL
- The server downloads and caches the image; the client never goes to the network itself. "Browse…" lets you preview a URL or pick an image the server has already cached
- The image can be changed at any time, including while the train is assembled

---

## Mod Dependencies

- Create 6.0.10
- Create: Railways Navigator beta 0.10.0 C6
- DragonLib beta 3.0.28

*Only tested with the above mod versions; compatibility with other versions is not guaranteed.*

---

## Installation

- Singleplayer: drop the mod into `mods`
- Dedicated server: **the server needs it as well**. Resolving through-service destinations and downloading custom images both happen server-side; with a client-only install those two features do not work, and everything else displays normally

---

## Upgrading from 0.2.2

0.3.0 requires **CRN 0.10.0-C6**. If you are still on CRN 0.9.x, please stay on 0.2.2.

- Mixins are no longer used; the mod now only calls CRN's public API
- "Hide technical stops" has been removed — use CRN's own pass-through / technical stop settings instead
- The destination is now the end of the current section, and the station blacklist is honoured
- The side display's left block shows the line name instead of the train category

---

## Known Issues

- When performing a turn-back with two timetable segments having different train types, the train type may be incorrectly recognized for a short time after departure
- Issues may occur when the display width is insufficient

---

## Future Plans

- CR, JRW and other style displays
- Line detail displays
- Improve the content displayed on the VIS (e.g. add a loop line display, include information on stations that have already been passed, etc.)
- Door direction indicators

---

## Disclaimer

- Although the currently implemented display style is named after `JRE`, it actually references the display methods of some private railways. Therefore, it can be considered a `JRE-flavored` display.
- This mod makes extensive use of generative AI assistance. If this causes you any discomfort, we sincerely apologize.
