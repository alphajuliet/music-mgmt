# cyjet-tracks

Manage my database of Cyjet tracks and releases.

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

To come:
- add <title>
- update <field> <value>
- release-track <track_id> <release_id> <track_number>
- query <sql>