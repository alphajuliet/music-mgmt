# Music Management CLI

A Babashka script to manage my database of tracks and releases. Output is JSON by default 
but also supports EDN, ASCII tables, and plain text. JSON is best for piping into `jq` or 
other processing.

```
Usage: bb -m mmgt.mm [options] command [args...]

Options:
  -h, --help                           Show help information
  -v, --version                        Show version information
  -f, --format FORMAT  json            Output format: json (default), edn, plain, or table
  -d, --db PATH        data/tracks.db  Database file path
      --verbose                        Enable verbose output

Commands:
- help

- tracks
- lookup <title>
- search <field> <value>
- view-track <id>
- add-track <title>
- update-track <id> <field> <value>

- releases
- view-release <release_id>
- add-release <id>
- update-release <id> <field> <value>
- release <track_id> <release_id> <track_number>

- query <sql>
- export-data <filename>
```
