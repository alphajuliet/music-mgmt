# Music Management System

A multi-interface music database management system with interactive shell, CLI, REST API, and web frontend.

## Components

### 1. Interactive Shell (Primary)
A JLine3-based interactive REPL for managing your cloud music database with tab-completion, command history, and smart title-to-ID resolution.

**Launch:**
```bash
bb shell
# or
bb -m mmgt.shell
```

**Features:**
- **Tab-completion** for commands, track titles, release IDs, and field names
- **Title-to-ID resolution** - use track titles instead of numeric IDs (e.g., `view-track "Neon Lights"` instead of `view-track 47`)
- **Command history** - up-arrow recalls previous commands across sessions (`~/.mmgt_history`)
- **Table output** by default (vs JSON)
- **Custom API URL** - specify via CLI argument or `MUSIC_API_URL` environment variable

For more information, run `help` inside the shell.

### 2. Local CLI Application (Babashka)
A Babashka script to manage your local SQLite database. Output is JSON by default
but also supports EDN, ASCII tables, and plain text. JSON is best for piping into `jq` or
other processing.

For CLI usage information, run:
```bash
bb -m mmgt.mm --help
```

### 3. Cloud CLI Application (Babashka)
A non-interactive CLI that connects to the REST API. Same commands as the local CLI but operates on the cloud database.

```bash
bb -m mmgt.cloud all-tracks
bb -m mmgt.cloud --api-url "https://custom-api.com/api/v1" all-tracks
```

### 4. REST API (Cloudflare Worker)
A TypeScript-based Cloudflare Worker providing HTTP REST access to the database schema.

### 5. Web Frontend
A simple HTMX-powered HTML interface (`src/index.html`) for browsing releases and tracks.

## Interactive Shell Usage

### Launch the Shell
```bash
bb shell                    # Uses MUSIC_API_URL env var or default
bb -m mmgt.shell "https://custom-api.com/api/v1"  # With custom API URL
```

### Available Commands

**Track Management:**
- `all-tracks` - List all tracks
- `lookup <title>` - Search tracks by title
- `search <field> <value>` - Search tracks by field (artist, type, title, year)
- `view-track <title-or-id>` - View a track (use title or numeric ID)
- `add-track <title>` - Create a new track
- `update-track <title-or-id> <field> <value>` - Update track information

**Release Management:**
- `releases` - List all releases
- `view-release <id>` - View a release with its tracks and duration
- `tracks <release-id>` - List tracks in a release
- `add-release <id>` - Create a new release
- `update-release <id> <field> <value>` - Update release information
- `release <track> <release-id> <track#>` - Add a track to a release

**Database & Utilities:**
- `query <sql>` - Execute raw SQL query
- `export-data <filename>` - Export all data to JSON file
- `linked-data <filename>` - Export data as JSON-LD (schema.org format)
- `refresh` - Reload track/release data from API
- `help` - Show command help
- `quit` - Exit the shell (also Ctrl-D)

### Tab Completion Examples
- Type `vi` + TAB → autocomplete to `view-track`
- Type `view-track "` + TAB → list available track titles
- Type `view-release ` + TAB → list available release IDs
- Type `update-track 123 ` + TAB → list available field names

### Examples
```
mmgt> all-tracks
mmgt> lookup "neon lights"
mmgt> view-track "My Song"  # Uses title-to-ID resolution
mmgt> search artist "Cyjet"
mmgt> add-track "New Track"
mmgt> update-track "My Song" length "3:45"
mmgt> releases
mmgt> view-release ALBUM001
mmgt> release 42 ALBUM001 1  # Add track 42 to ALBUM001 as track #1
mmgt> export-data backup.json
mmgt> quit
```

## REST API Documentation

### Base URL
```
https://your-worker.your-subdomain.workers.dev/api/v1
```

### Authentication
Currently no authentication required. Consider implementing API keys for production use.

### Response Format
All endpoints return JSON with consistent structure:
```json
{
  "success": true|false,
  "message": "Optional message",
  "data": "Response data (for successful requests)"
}
```

### Endpoints

#### System Endpoints

**GET /api/v1/health**
- **Description**: Health check
- **Response**: `{ "status": "healthy", "timestamp": "2024-01-01T00:00:00.000Z" }`

**GET /api/v1/version**
- **Description**: Get version information
- **Response**: `{ "version": "0.1.2", "api_version": "1.0.0" }`

#### Track Endpoints

**GET /api/v1/tracks**
- **Description**: List all tracks
- **CLI Equivalent**: `bb -m mmgt.mm all-tracks`
- **Response**: Array of track objects

**GET /api/v1/tracks/search**
- **Description**: Search tracks by title or field
- **CLI Equivalent**: `bb -m mmgt.mm lookup <title>` or `bb -m mmgt.mm search <field> <value>`
- **Query Parameters**:
  - `q` (string): Search term for title lookup
  - `field` (string): Field to search (artist, type, title, year)
  - `value` (string): Value to search for in specified field
- **Examples**:
  ```bash
  GET /api/v1/tracks/search?q=love
  GET /api/v1/tracks/search?field=artist&value=cyjet
  ```

**GET /api/v1/tracks/{id}**
- **Description**: Get specific track by ID
- **CLI Equivalent**: `bb -m mmgt.mm view-track {id}`
- **Response**: Single track object

**POST /api/v1/tracks**
- **Description**: Create new track
- **CLI Equivalent**: `bb -m mmgt.mm add-track <title>`
- **Request Body**:
  ```json
  {
    "title": "Track Title",
    "artist": "Artist Name",
    "type": "Original",
    "year": 2024,
    "length": "3:45",
    "bpm": 120,
    "ISRC": "USUM71234567",
    "Genre": "Electronic"
  }
  ```
- **Required**: `title`
- **Defaults**: `artist="Cyjet"`, `type="Original"`, `year=current_year`, `length="00:00"`

**PUT /api/v1/tracks/{id}**
- **Description**: Update track information
- **CLI Equivalent**: `bb -m mmgt.mm update-track {id} <field> <value>`
- **Request Body**: Partial track object with fields to update
- **Example**:
  ```json
  {
    "length": "4:20",
    "bpm": 128
  }
  ```

#### Release Endpoints

**GET /api/v1/releases**
- **Description**: List all releases
- **CLI Equivalent**: `bb -m mmgt.mm releases`
- **Response**: Array of release objects

**GET /api/v1/releases/{id}**
- **Description**: Get specific release with tracks and calculated duration
- **CLI Equivalent**: `bb -m mmgt.mm view-release {id}`
- **Response**: Release object with embedded tracks array and total duration

**GET /api/v1/releases/{id}/tracks**
- **Description**: Get tracks for a specific release
- **CLI Equivalent**: `bb -m mmgt.mm tracks {id}`
- **Response**: Array of track objects with track numbers

**POST /api/v1/releases**
- **Description**: Create new release
- **CLI Equivalent**: `bb -m mmgt.mm add-release {id}`
- **Request Body**:
  ```json
  {
    "id": "ALBUM001",
    "Name": "Album Title",
    "Status": "WIP"
  }
  ```
- **Required**: `id`
- **Default**: `Status="WIP"`

**PUT /api/v1/releases/{id}**
- **Description**: Update release information
- **CLI Equivalent**: `bb -m mmgt.mm update-release {id} <field> <value>`
- **Request Body**: Partial release object
- **Example**:
  ```json
  {
    "Name": "Updated Album Name",
    "Status": "Released",
    "ReleaseDate": "2024-01-01"
  }
  ```

**POST /api/v1/releases/{id}/tracks**
- **Description**: Add track to release
- **CLI Equivalent**: `bb -m mmgt.mm release <track_id> {id} <track_number>`
- **Request Body**:
  ```json
  {
    "track_id": 123,
    "track_number": 1
  }
  ```

#### Database Operations

**POST /api/v1/query**
- **Description**: Execute raw SQL query
- **CLI Equivalent**: `bb -m mmgt.mm query "<sql>"`
- **Request Body**:
  ```json
  {
    "query": "SELECT * FROM tracks WHERE year = 2024"
  }
  ```
- **⚠️ Use with caution**: No input sanitization beyond prepared statements

**GET /api/v1/export**
- **Description**: Export all database data
- **CLI Equivalent**: `bb -m mmgt.mm export-data <filename>`
- **Response**: Complete database export with all tables

### Data Models

#### Track Object
```json
{
  "id": 123,
  "title": "Track Title",
  "artist": "Artist Name",
  "type": "Original",
  "year": 2024,
  "length": "3:45",
  "bpm": 120,
  "ISRC": "USUM71234567",
  "Genre": "Electronic",
  "song_fname": "track.wav"
}
```

#### Release Object
```json
{
  "ID": "ALBUM001",
  "Name": "Album Title",
  "Status": "WIP",
  "UPC": 123456789012,
  "Catalogue": "CAT001",
  "ReleaseDate": "2024-01-01",
  "PromoLink": "https://example.com",
  "Bandcamp": "https://bandcamp.com/album"
}
```

#### Release with Tracks
```json
{
  "ID": "ALBUM001",
  "Name": "Album Title",
  "Status": "Released",
  "tracks": [
    {
      "id": 123,
      "title": "Track 1",
      "track_number": 1,
      "length": "3:45",
      "ISRC": "USUM71234567"
    }
  ],
  "duration": "15:30"
}
```

### Error Responses
```json
{
  "success": false,
  "message": "Error description"
}
```

Common HTTP status codes:
- `200`: Success
- `400`: Bad Request (validation errors)
- `404`: Not Found
- `405`: Method Not Allowed
- `500`: Internal Server Error

## Local CLI Application (Babashka)

The local CLI manages a SQLite database at `data/tracks.db` and outputs in JSON format by default.

### Basic Commands
```bash
# Show help and available commands
bb -m mmgt.mm --help

# Track operations
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
bb -m mmgt.mm tracks "ALBUM001"
bb -m mmgt.mm release 123 "ALBUM001" 1  # Add track 123 as track #1

# Update operations
bb -m mmgt.mm update-release "ALBUM001" Name "Album Title"
bb -m mmgt.mm update-track 123 artist "New Artist"

# Output formats (defaults to JSON)
bb -m mmgt.mm all-tracks --format table
bb -m mmgt.mm all-tracks --format edn
bb -m mmgt.mm all-tracks --format plain

# Database operations
bb -m mmgt.mm query "SELECT * FROM tracks WHERE year = 2024"
bb -m mmgt.mm backup
bb -m mmgt.mm export-data export.json
```

## Cloud CLI Application (Babashka)

The cloud CLI connects to the REST API and uses table format by default.

### Basic Commands
```bash
# Show help
bb -m mmgt.cloud --help

# Same commands as local CLI but connects to the REST API
bb -m mmgt.cloud all-tracks
bb -m mmgt.cloud add-track "Track Title"
bb -m mmgt.cloud releases --format json

# Custom API URL
bb -m mmgt.cloud --api-url "https://custom-api.com/api/v1" all-tracks

# Query and export
bb -m mmgt.cloud query "SELECT * FROM tracks WHERE year = 2024"
bb -m mmgt.cloud export-data cloud-export.json
bb -m mmgt.cloud linked-data export.jsonld
```

### Development and Deployment

#### Local Development
```bash
# Start local development server
npx wrangler dev

# Test API endpoints
curl http://localhost:8787/api/v1/tracks
```

#### Deploy to Cloudflare
```bash
# Deploy to production
npx wrangler deploy

# Manage D1 database
npx wrangler d1 execute cyjet-music --file=data/schema.sql
npx wrangler d1 execute cyjet-music --command "SELECT * FROM tracks LIMIT 5"
```

#### Example Usage with curl
```bash
# Get all tracks
curl https://your-worker.your-subdomain.workers.dev/api/v1/tracks

# Create new track
curl -X POST https://your-worker.your-subdomain.workers.dev/api/v1/tracks \
  -H "Content-Type: application/json" \
  -d '{"title": "New Song", "artist": "Artist"}'

# Search tracks
curl "https://your-worker.your-subdomain.workers.dev/api/v1/tracks/search?q=love"

# Update track
curl -X PUT https://your-worker.your-subdomain.workers.dev/api/v1/tracks/123 \
  -H "Content-Type: application/json" \
  -d '{"length": "4:20"}'
```
