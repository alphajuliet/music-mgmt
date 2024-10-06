# cyjet-tracks

Manage my SQLite3 database of Cyjet tracks and releases.

This is done largely with the cmd script...

```
bb -m tracks.cmd <command> [<args...>]
```
Commands:
- help
- tracks
- releases
- lookup <title>
- view-release <release_id>
- add-track <title>
- update-track <id> <field> <value>
- query <sql>

To do:
- release-track <track_id> <release_id> <track_number>