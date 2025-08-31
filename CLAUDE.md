# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This project has a tri-architecture music database management system:
1. **Primary**: Babashka-based CLI application (`src/mmgt/mm.clj`) for local music database management
2. **Secondary**: Babashka-based cloud CLI (`src/mmgt/cloud.clj`) that connects to the REST API
3. **REST API**: Cloudflare Worker (`src/index.ts`) providing HTTP REST access to the same database schema
4. **Web Frontend**: Simple HTML interface (`src/index.html`) using HTMX for API interaction

All components work with SQLite/D1 databases tracking tracks, releases, and their relationships through an instances table that maps tracks to releases with track numbers.

## Core Architecture

### Local CLI Application (Primary)
- **Main entry point**: `src/mmgt/mm.clj` - Contains the CLI interface and all local database operations
- **Output formatting**: `src/mmgt/output.clj` - Handles multiple output formats (JSON, EDN, plain text, ASCII tables)
- **Configuration**: `bb.edn` - Babashka project config with SQLite pod and HTTP client dependencies
- **Database**: Local SQLite at `data/tracks.db`

### Cloud CLI Application (Secondary)
- **Main entry point**: `src/mmgt/cloud.clj` - CLI interface that makes HTTP requests to the REST API
- **Uses**: Same output formatting as local CLI but connects to remote API
- **Default format**: Table output (vs JSON for local CLI)
- **API URL**: Configurable via `--api-url` flag or `MUSIC_API_URL` environment variable

### Cloudflare Worker REST API
- **Worker script**: `src/api/index.ts` - TypeScript worker providing comprehensive REST API endpoints
- **Configuration**: `wrangler.toml` - Cloudflare Worker config with D1 database binding
- **Database**: Cloudflare D1 database named "cyjet-music"
- **API Version**: 1.0.0 with comprehensive CRUD operations for tracks and releases

### Web Frontend
- **HTML interface**: `src/index.html` - Simple HTMX-powered interface
- **API Integration**: Directly calls the REST API at `https://music.cyjet.online/api/v1/`
- **Features**: Basic buttons to list releases and tracks

### Database Schema
- **Local database**: SQLite at `data/tracks.db`
- **Cloud database**: Cloudflare D1 (configured in wrangler.toml)
- **Schema**: `data/schema.sql` defines three main tables:
  - `tracks` - Individual tracks with metadata (artist, title, type, year, length, ISRC, genre, etc.)
  - `releases` - Albums/EPs/singles with status tracking (ID, Name, Status, UPC, etc.)
  - `instances` - Junction table linking tracks to releases with track numbers
  - `released` - View joining all three tables for complete track-release information

## Running the Application

### Local CLI Commands (Babashka)
```bash
# Show help and available commands
bb -m mmgt.mm --help

# Basic track operations
bb -m mmgt.mm all-tracks
bb -m mmgt.mm add-track "Track Title"
bb -m mmgt.mm view-track 123
bb -m mmgt.mm update-track 123 length "3:45"
bb -m mmgt.mm lookup "song title"
bb -m mmgt.mm search artist "Cyjet"

# Release operations
bb -m mmgt.mm releases
bb -m mmgt.mm add-release "ALBUM001"
bb -m mmgt.mm view-release "ALBUM001"
bb -m mmgt.mm tracks "ALBUM001"  # List tracks in release
bb -m mmgt.mm release 123 "ALBUM001" 1  # Add track 123 to release as track #1
bb -m mmgt.mm update-release "ALBUM001" Name "Album Title"

# Output formats (default: json)
bb -m mmgt.mm all-tracks --format table
bb -m mmgt.mm all-tracks --format edn
bb -m mmgt.mm all-tracks --format plain

# Database operations
bb -m mmgt.mm query "SELECT * FROM tracks WHERE year = 2024"
bb -m mmgt.mm backup
bb -m mmgt.mm export-data export.json
```

### Cloud CLI Commands (Babashka)
```bash
# Show help (note: uses table format by default)
bb -m mmgt.cloud --help

# Same commands as local CLI but connects to API
bb -m mmgt.cloud all-tracks
bb -m mmgt.cloud add-track "Track Title"
bb -m mmgt.cloud releases --format json

# Can specify custom API URL
bb -m mmgt.cloud --api-url "https://custom-api.com/api/v1" all-tracks

# Query cloud database
bb -m mmgt.cloud query "SELECT * FROM tracks WHERE year = 2024"
bb -m mmgt.cloud export-data cloud-export.json
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

# List D1 databases
npx wrangler d1 list
```

### Web Interface
- Open `src/index.html` in a browser, or
- Serve it locally and access the REST API endpoints
- Uses HTMX to dynamically load data from the API

## REST API Endpoints

The Cloudflare Worker provides a comprehensive REST API at `/api/v1/`:

### System Endpoints
- `GET /api/v1/health` - Health check
- `GET /api/v1/version` - Version information

### Track Endpoints
- `GET /api/v1/tracks` - List all tracks
- `GET /api/v1/tracks/search?q=title` or `?field=artist&value=Cyjet` - Search tracks
- `GET /api/v1/tracks/{id}` - Get specific track
- `POST /api/v1/tracks` - Create new track
- `PUT /api/v1/tracks/{id}` - Update track

### Release Endpoints
- `GET /api/v1/releases` - List all releases
- `GET /api/v1/releases/{id}` - Get specific release with tracks and duration
- `GET /api/v1/releases/{id}/tracks` - Get tracks for a release
- `POST /api/v1/releases` - Create new release
- `PUT /api/v1/releases/{id}` - Update release
- `POST /api/v1/releases/{id}/tracks` - Add track to release

### Database Operations
- `POST /api/v1/query` - Execute raw SQL query
- `GET /api/v1/export` - Export all data as JSON

## Database Schema Notes

- Default artist is "Cyjet" and default type is "Original" for new tracks
- Track lengths are stored as "mm:ss" strings
- Releases have a status field (defaults to "WIP")
- ISRC codes and BPM values are supported for tracks
- The view-release command calculates total duration from track lengths
- Schema is defined in `data/schema.sql` and should be kept in sync between local SQLite and D1

## Development Notes

- **Local CLI**: Uses Babashka pods for SQLite access (`org.babashka/go-sqlite3`)
- **Cloud CLI**: Uses Babashka HTTP client to connect to REST API
- **Worker**: TypeScript with Cloudflare Workers runtime and D1 database binding
- **No Build Process**: CLI applications are script-based, Worker uses `npx wrangler dev/deploy`
- **Database Management**: 
  - Local backups are automatically timestamped and stored in `data/backup/`
  - Both CLI variants use prepared statements for safety (except the `query` command)
  - Schema is defined in `data/schema.sql` and should be kept in sync between local SQLite and D1
- **Output Formats**: 
  - Local CLI defaults to JSON format
  - Cloud CLI defaults to table format
  - Both support json, edn, plain, and table formats
- **Version**: Current version is "0.1.2" (defined in both CLI main files)
- **API Version**: REST API is version "1.0.0"

## Configuration Files

- `bb.edn` - Babashka project configuration with dependencies
- `wrangler.toml` - Cloudflare Worker configuration with D1 database binding
- `package.json` - Minimal Node.js config for Wrangler CLI tool
- `data/schema.sql` - Database schema definition for both local SQLite and cloud D1

## Key File Locations

- **Local CLI**: `src/mmgt/mm.clj`
- **Cloud CLI**: `src/mmgt/cloud.clj`
- **Output Formatting**: `src/mmgt/output.clj`
- **REST API Worker**: `src/api/index.ts`
- **Web Frontend**: `src/index.html`
- **Database Schema**: `data/schema.sql`
- **Local Database**: `data/tracks.db`
- **Backup Directory**: `data/backup/`