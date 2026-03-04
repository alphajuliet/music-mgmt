# Interactive Music Management Shell

## Overview

Replace the fire-and-forget CLI with a persistent interactive shell using JLine3 (bundled in Babashka 1.12.215+). The shell connects to the cloud REST API and provides tab-completion of track titles and release IDs, eliminating the need to memorize commands or look up numeric IDs.

## Architecture

New entry point: `src/mmgt/shell.clj`

```
┌─────────────────────────────────────────────┐
│  shell.clj (JLine3 REPL)                    │
│  - LineReader with custom Completers        │
│  - Command parser & dispatcher              │
│  - Completion cache (tracks + releases)     │
├─────────────────────────────────────────────┤
│  cloud.clj (existing)                       │
│  - HTTP client functions (api-get, etc.)    │
│  - Command implementations                  │
├─────────────────────────────────────────────┤
│  output.clj (existing)                      │
│  - Table/JSON/EDN/plain formatting          │
└─────────────────────────────────────────────┘
         │
         ▼
   Cloud REST API (Cloudflare Worker)
```

### Dependencies

None additional. JLine3 is compiled into the Babashka binary. The following classes are available:

- `org.jline.terminal.TerminalBuilder`
- `org.jline.reader.LineReaderBuilder`, `Completer`, `Candidate`
- `org.jline.reader.EndOfFileException`, `UserInterruptException`
- `org.jline.reader.LineReader$Option`

**Limitation:** Built-in completer classes (`StringsCompleter`, `ArgumentCompleter`) are NOT in Babashka's class allowlist. All completers must be built via `reify Completer`.

## Completion Cache

On startup, the shell fetches all tracks and releases from the API and builds an in-memory cache:

```clojure
{:tracks   [{:id 1 :title "Neon Lights" :artist "Cyjet" ...} ...]
 :releases [{:ID "CYJET-EP01" :Name "First EP" ...} ...]}
```

This cache is used for:
1. Tab-completing track titles in commands that accept track IDs
2. Tab-completing release IDs/names in commands that accept release IDs
3. Resolving titles back to numeric IDs before calling the API

The cache is refreshed after any mutating command (`add-track`, `update-track`, `add-release`, `update-release`, `release`).

## Tab-Completion Behavior

A single completer dispatches based on argument position and command context:

**Word 0 (command name):**
```
mmgt> vie<TAB>
view-track    view-release
```

**Word 1+ (arguments, context-dependent):**

| Command | Arg 1 completes | Arg 2 completes | Arg 3 completes |
|---------|----------------|----------------|----------------|
| `view-track` | track titles | - | - |
| `view-release` | release IDs | - | - |
| `tracks` | release IDs | - | - |
| `update-track` | track titles | field names | - |
| `update-release` | release IDs | field names | - |
| `release` | track titles | release IDs | - |
| `search` | field names | - | - |
| `lookup` | - | - | - |

Track titles containing spaces are automatically quoted during completion:
```
mmgt> view-track Ne<TAB>
mmgt> view-track "Neon Lights"
```

### Title-to-ID Resolution

When a command receives a title string instead of a numeric ID, the shell:
1. Searches the completion cache for exact matches
2. If exactly one match, uses that track's numeric ID
3. If multiple matches, displays them in a table and asks the user to pick
4. If no match, prints an error

## Commands

Same commands as `cloud.clj`, default output format is `table`:

```
mmgt> help                                    # show commands
mmgt> quit                                    # exit (also Ctrl-D)

mmgt> all-tracks                              # list all tracks
mmgt> lookup <title>                          # search by title
mmgt> search <field> <value>                  # search by field
mmgt> view-track <title-or-id>                # view track details
mmgt> add-track <title>                       # create new track
mmgt> update-track <title-or-id> <field> <val>  # update track field

mmgt> releases                                # list all releases
mmgt> view-release <id>                       # view release + tracks
mmgt> tracks <release-id>                     # list tracks on release
mmgt> add-release <id>                        # create new release
mmgt> update-release <id> <field> <value>     # update release field
mmgt> release <track> <release-id> <track#>   # add track to release

mmgt> query <sql>                             # raw SQL query
mmgt> export-data <filename>                  # export to file
mmgt> linked-data <filename>                  # export as JSON-LD
```

## Startup & Launch

```bash
bb -m mmgt.shell
```

Startup sequence:
1. Create JLine3 Terminal and LineReader
2. Fetch tracks and releases from API to populate completion cache
3. Print welcome banner with track/release counts
4. Enter read-eval-print loop

```
Music Management Shell v0.1.2
Connected to https://music.cyjet.online/api/v1
Loaded 47 tracks, 5 releases
Type 'help' for commands, TAB for completion

mmgt>
```

## Implementation Plan

### Step 1: Minimal shell with command dispatch
- Create `src/mmgt/shell.clj` with JLine3 Terminal + LineReader
- Basic REPL loop: read line, parse into command + args, dispatch to `cloud.clj` functions
- Handle `quit`, `help`, Ctrl-D, Ctrl-C
- No completion yet — just verify the loop works

### Step 2: Command name completion
- Implement a completer via `reify Completer` that completes command names on word index 0
- Register it with the LineReader

### Step 3: Completion cache + track title completion
- On startup, fetch tracks and releases from the API
- For commands that take track IDs (view-track, update-track, release), complete against track titles
- Implement title-to-ID resolution with exact match and disambiguation

### Step 4: Release ID and field name completion
- Complete release IDs for commands that take them
- Complete field names for update-track and update-release
- Complete field names for search

### Step 5: Cache refresh and polish
- Refresh cache after mutating commands
- Command history persistence (JLine3 supports this via a history file)
- Error handling for API connection failures
