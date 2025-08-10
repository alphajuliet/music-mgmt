# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This project has a dual architecture:
1. **Primary**: Babashka-based CLI application (`src/mmgt/`) for local music database management
2. **Secondary**: Cloudflare Worker (`src/index.ts`) providing REST API access to the same database schema

Both components work with SQLite databases tracking tracks, releases, and their relationships through an instances table that maps tracks to releases with track numbers.

## Core Architecture

### CLI Application (Primary)
- **Main entry point**: `src/mmgt/mm.clj` - Contains the CLI interface and all database operations
- **Output formatting**: `src/mmgt/output.clj` - Handles multiple output formats (JSON, EDN, plain text, ASCII tables)
- **Configuration**: `bb.edn` - Babashka project config with SQLite pod dependency

### Cloudflare Worker (Secondary)
- **Worker script**: `src/index.ts` - TypeScript worker providing REST API endpoints
- **Configuration**: `wrangler.toml` - Cloudflare Worker config with D1 database binding
- **Current endpoint**: `/api/v1/tracks` - Returns all tracks as JSON

### Database Schema
- **Local database**: SQLite at `data/tracks.db`
- **Cloud database**: Cloudflare D1 (configured in wrangler.toml)
- **Schema**: `data/schema.sql` defines three main tables:
  - `tracks` - Individual tracks with metadata (artist, title, type, year, length, ISRC)
  - `releases` - Albums/EPs/singles with status tracking  
  - `instances` - Junction table linking tracks to releases with track numbers
  - `released` - View joining all three tables for complete track-release information

## Running the Application

### CLI Commands (Babashka)
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

### Cloudflare Worker Development
```bash
# Local development server
npx wrangler dev

# Deploy to Cloudflare
npx wrangler deploy

# Manage D1 database
npx wrangler d1 execute cyjet-music --file=data/schema.sql
npx wrangler d1 execute cyjet-music --command "SELECT * FROM tracks LIMIT 5"
```

## Database Schema Notes

- Default artist is "Cyjet" and default type is "Original" for new tracks
- Track lengths are stored as "mm:ss" strings
- Releases have a status field (defaults to "WIP")
- ISRC codes are supported for tracks
- The view-release command calculates total duration from track lengths

## Development Notes

- **CLI**: Uses Babashka pods for SQLite access (`org.babashka/go-sqlite3`)
- **Worker**: TypeScript with Cloudflare Workers runtime and D1 database binding
- **Testing**: No traditional build/test commands - CLI is script-based, Worker uses wrangler dev
- **Database Management**: 
  - Local backups are automatically timestamped and stored in `data/backup/`
  - CLI queries use prepared statements for safety (except the `query` command)
  - Schema is defined in `data/schema.sql` and should be kept in sync between local SQLite and D1
- **Version**: Current version is "0.1.2" (defined in `src/mmgt/mm.clj:9`)