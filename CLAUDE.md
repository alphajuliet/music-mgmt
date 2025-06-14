# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Babashka-based CLI application for managing a personal music database stored in SQLite. The application tracks tracks, releases, and their relationships through an instances table that maps tracks to releases with track numbers.

## Core Architecture

- **Main entry point**: `src/mmgt/mm.clj` - Contains the CLI interface and all database operations
- **Output formatting**: `src/mmgt/output.clj` - Handles multiple output formats (JSON, EDN, plain text, ASCII tables)
- **Database**: SQLite database at `data/tracks.db` with three main tables:
  - `tracks` - Individual tracks with metadata (artist, title, type, year, length, ISRC)
  - `releases` - Albums/EPs/singles with status tracking
  - `instances` - Junction table linking tracks to releases with track numbers

## Running the Application

```bash
# Show help and available commands
bb -m mmgt.mm --help

# Basic track operations
bb -m mmgt.mm all-tracks
bb -m mmgt.mm add-track "Track Title"
bb -m mmgt.mm view-track 123
bb -m mmgt.mm update-track 123 length "3:45"

# Release operations
bb -m mmgt.mm releases
bb -m mmgt.mm add-release "ALBUM001"
bb -m mmgt.mm view-release "ALBUM001"
bb -m mmgt.mm release 123 "ALBUM001" 1  # Add track 123 to release as track #1

# Output formats
bb -m mmgt.mm all-tracks --format table
bb -m mmgt.mm all-tracks --format edn
bb -m mmgt.mm all-tracks --format plain

# Database operations
bb -m mmgt.mm query "SELECT * FROM tracks WHERE year = 2024"
bb -m mmgt.mm backup
bb -m mmgt.mm export-data export.json
```

## Database Schema Notes

- Default artist is "Cyjet" and default type is "Original" for new tracks
- Track lengths are stored as "mm:ss" strings
- Releases have a status field (defaults to "WIP")
- ISRC codes are supported for tracks
- The view-release command calculates total duration from track lengths

## Development Notes

- Uses Babashka pods for SQLite access (`org.babashka/go-sqlite3`)
- No traditional build/test commands - this is a script-based application
- Database backups are automatically timestamped and stored in `data/backup/`
- All database queries use prepared statements for safety except the `query` command