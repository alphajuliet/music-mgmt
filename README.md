# Music Management CLI

A Babashka script to manage my database of tracks and releases. Output is JSON by default 
but also supports EDN, ASCII tables, and plain text. JSON is best for piping into `jq` or 
other processing.

For usage information, run:
```bash
bb -m mmgt.mm --help
```
