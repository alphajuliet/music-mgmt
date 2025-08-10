# Music Management System

A dual-architecture music database management system with both CLI and REST API interfaces.

## Components

### 1. CLI Application (Babashka)
A Babashka script to manage your database of tracks and releases locally. Output is JSON by default 
but also supports EDN, ASCII tables, and plain text. JSON is best for piping into `jq` or 
other processing.

For CLI usage information, run:
```bash
bb -m mmgt.mm --help
```

### 2. REST API (Cloudflare Worker)
A TypeScript-based Cloudflare Worker providing HTTP REST access to the same database schema.

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
