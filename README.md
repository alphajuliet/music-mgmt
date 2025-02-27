# cyjet-tracks

Manage my SQLite3 database of tracks and releases with the `mm` Babashka script. Output is generally in JSON, for piping into `jq` or other processing.

Usage:
```
bb -m mmgt.mm <command> [ <args...> ]
```

```
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
