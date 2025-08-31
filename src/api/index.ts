export interface Env {
  DB: D1Database;
}

// Type definitions
interface Track {
  id: number;
  title: string;
  type: string;
  artist: string;
  year: number;
  length: string;
  bpm?: number;
  ISRC?: string;
  Genre?: string;
  song_fname?: string;
}

interface Release {
  ID: string;
  Name?: string;
  Status: string;
  UPC?: number;
  Catalogue?: string;
  ReleaseDate?: string;
  PromoLink?: string;
  Bandcamp?: string;
}

interface CreateTrackRequest {
  title: string;
  artist?: string;
  type?: string;
  year?: number;
  length?: string;
  bpm?: number;
  ISRC?: string;
  Genre?: string;
}

interface AddTrackToReleaseRequest {
  track_id: number;
  track_number: number;
}

// Utility functions
function mmssToSeconds(mmss: string): number {
  const [m, s] = mmss.split(':');
  return parseInt(m) * 60 + parseInt(s);
}

function secondsToMmss(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function getCurrentYear(): number {
  return new Date().getFullYear();
}

// Error handling
function createErrorResponse(message: string, status = 400) {
  return new Response(JSON.stringify({ success: false, message }), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

function createSuccessResponse(data?: any, message?: string) {
  return new Response(JSON.stringify({ success: true, ...data, message }), {
    headers: { 'Content-Type': 'application/json' }
  });
}

// Route handlers
class MusicAPI {
  constructor(private db: D1Database) {}

  async getAllTracks(): Promise<Response> {
    try {
      const { results } = await this.db.prepare("SELECT * FROM tracks").all();
      return new Response(JSON.stringify(results), {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (error) {
      return createErrorResponse(`Failed to fetch tracks: ${error}`, 500);
    }
  }

  async searchTracks(url: URL): Promise<Response> {
    try {
      const q = url.searchParams.get('q');
      const field = url.searchParams.get('field');
      const value = url.searchParams.get('value');

      if (q) {
        // Title lookup
        const { results } = await this.db.prepare(
          "SELECT * FROM tracks WHERE title LIKE ?"
        ).bind(`%${q}%`).all();
        return new Response(JSON.stringify(results), {
          headers: { 'Content-Type': 'application/json' }
        });
      } else if (field && value) {
        // Field search
        const validFields = new Set(['artist', 'type', 'title', 'year']);
        if (!validFields.has(field)) {
          return createErrorResponse(`Invalid field. Valid fields are: ${Array.from(validFields).join(', ')}`);
        }
        
        const { results } = await this.db.prepare(
          `SELECT * FROM tracks WHERE ${field} LIKE ?`
        ).bind(`%${value}%`).all();
        return new Response(JSON.stringify(results), {
          headers: { 'Content-Type': 'application/json' }
        });
      } else {
        return createErrorResponse('Either q parameter or both field and value parameters are required');
      }
    } catch (error) {
      return createErrorResponse(`Search failed: ${error}`, 500);
    }
  }

  async getTrack(id: string): Promise<Response> {
    try {
      const { results } = await this.db.prepare(
        "SELECT * FROM tracks WHERE id = ?"
      ).bind(id).all();
      
      if (results.length === 0) {
        return createErrorResponse('Track not found', 404);
      }
      
      return new Response(JSON.stringify(results[0]), {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (error) {
      return createErrorResponse(`Failed to fetch track: ${error}`, 500);
    }
  }

  async createTrack(request: Request): Promise<Response> {
    try {
      const body: CreateTrackRequest = await request.json();
      
      if (!body.title) {
        return createErrorResponse('Title is required');
      }

      const trackData = {
        title: body.title,
        artist: body.artist || 'Cyjet',
        type: body.type || 'Original',
        year: body.year || getCurrentYear(),
        length: body.length || '00:00',
        bpm: body.bpm || null,
        ISRC: body.ISRC || null,
        Genre: body.Genre || null
      };

      const result = await this.db.prepare(
        "INSERT INTO tracks (title, artist, type, year, length, bpm, ISRC, Genre) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      ).bind(
        trackData.title,
        trackData.artist,
        trackData.type,
        trackData.year,
        trackData.length,
        trackData.bpm,
        trackData.ISRC,
        trackData.Genre
      ).run();

      return createSuccessResponse({ id: result.meta.last_row_id }, 'Track created successfully');
    } catch (error) {
      return createErrorResponse(`Failed to create track: ${error}`, 500);
    }
  }

  async updateTrack(id: string, request: Request): Promise<Response> {
    try {
      const updates: Partial<Track> = await request.json();
      const validFields = new Set(['title', 'type', 'artist', 'year', 'length', 'bpm', 'ISRC', 'Genre', 'song_fname']);
      
      const updateFields = Object.keys(updates).filter(field => validFields.has(field));
      
      if (updateFields.length === 0) {
        return createErrorResponse(`No valid fields to update. Valid fields are: ${Array.from(validFields).join(', ')}`);
      }

      const setClause = updateFields.map(field => `${field} = ?`).join(', ');
      const values = updateFields.map(field => updates[field as keyof Track]);

      await this.db.prepare(
        `UPDATE tracks SET ${setClause} WHERE id = ?`
      ).bind(...values, id).run();

      return createSuccessResponse(undefined, 'Track updated successfully');
    } catch (error) {
      return createErrorResponse(`Failed to update track: ${error}`, 500);
    }
  }

  async getAllReleases(): Promise<Response> {
    try {
      const { results } = await this.db.prepare("SELECT * FROM releases ORDER BY ID").all();
      return new Response(JSON.stringify(results), {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (error) {
      return createErrorResponse(`Failed to fetch releases: ${error}`, 500);
    }
  }

  async getRelease(id: string): Promise<Response> {
    try {
      // Get release info
      const { results: releaseResults } = await this.db.prepare(
        "SELECT * FROM releases WHERE ID = ?"
      ).bind(id).all();
      
      if (releaseResults.length === 0) {
        return createErrorResponse('Release not found', 404);
      }

      // Get tracks for the release
      const { results: trackResults } = await this.db.prepare(`
        SELECT title, track_number, tracks.id, ISRC, length 
        FROM releases
        LEFT JOIN instances ON instances.release = releases.ID
        LEFT JOIN tracks ON instances.id = tracks.id
        WHERE releases.ID = ?
        ORDER BY track_number
      `).bind(id).all();

      // Calculate total duration
      const totalSeconds = trackResults.reduce((total: number, track: any) => {
        if (track.length) {
          return total + mmssToSeconds(track.length);
        }
        return total;
      }, 0);

      const release = {
        ...releaseResults[0],
        tracks: trackResults,
        duration: secondsToMmss(totalSeconds)
      };

      return new Response(JSON.stringify(release), {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (error) {
      return createErrorResponse(`Failed to fetch release: ${error}`, 500);
    }
  }

  async getReleaseTracks(id: string): Promise<Response> {
    try {
      const { results } = await this.db.prepare(`
        SELECT title, track_number, tracks.id, length
        FROM releases
        LEFT JOIN instances ON instances.release = releases.ID
        LEFT JOIN tracks ON instances.id = tracks.id
        WHERE releases.ID = ?
        ORDER BY track_number
      `).bind(id).all();

      return new Response(JSON.stringify(results), {
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (error) {
      return createErrorResponse(`Failed to fetch release tracks: ${error}`, 500);
    }
  }

  async createRelease(request: Request): Promise<Response> {
    try {
      const body: { id: string, [key: string]: any } = await request.json();
      
      if (!body.id) {
        return createErrorResponse('Release ID is required');
      }

      await this.db.prepare(
        "INSERT INTO releases (ID, Status) VALUES (?, 'WIP')"
      ).bind(body.id).run();

      return createSuccessResponse(undefined, 'Release created successfully');
    } catch (error) {
      return createErrorResponse(`Failed to create release: ${error}`, 500);
    }
  }

  async updateRelease(id: string, request: Request): Promise<Response> {
    try {
      const updates: Partial<Release> = await request.json();
      const validFields = new Set(['Name', 'Status', 'UPC', 'Catalogue', 'ReleaseDate', 'PromoLink', 'Bandcamp']);
      
      const updateFields = Object.keys(updates).filter(field => validFields.has(field));
      
      if (updateFields.length === 0) {
        return createErrorResponse(`No valid fields to update. Valid fields are: ${Array.from(validFields).join(', ')}`);
      }

      const setClause = updateFields.map(field => `${field} = ?`).join(', ');
      const values = updateFields.map(field => updates[field as keyof Release]);

      await this.db.prepare(
        `UPDATE releases SET ${setClause} WHERE ID = ?`
      ).bind(...values, id).run();

      return createSuccessResponse(undefined, 'Release updated successfully');
    } catch (error) {
      return createErrorResponse(`Failed to update release: ${error}`, 500);
    }
  }

  async addTrackToRelease(id: string, request: Request): Promise<Response> {
    try {
      const body: AddTrackToReleaseRequest = await request.json();
      
      if (!body.track_id || !body.track_number) {
        return createErrorResponse('track_id and track_number are required');
      }

      await this.db.prepare(
        "INSERT INTO instances (id, release, track_number) VALUES (?, ?, ?)"
      ).bind(body.track_id, id, body.track_number).run();

      return createSuccessResponse(undefined, 'Track added to release successfully');
    } catch (error) {
      return createErrorResponse(`Failed to add track to release: ${error}`, 500);
    }
  }

  async executeQuery(request: Request): Promise<Response> {
    try {
      const body: { query: string } = await request.json();
      
      if (!body.query) {
        return createErrorResponse('Query is required');
      }

      const { results } = await this.db.prepare(body.query).all();
      return createSuccessResponse({ results });
    } catch (error) {
      return createErrorResponse(`Query failed: ${error}`, 500);
    }
  }

  async exportData(): Promise<Response> {
    try {
      const [tracksResult, releasesResult, instancesResult] = await Promise.all([
        this.db.prepare("SELECT * FROM tracks").all(),
        this.db.prepare("SELECT * FROM releases").all(),
        this.db.prepare("SELECT * FROM instances").all()
      ]);

      const exportData = {
        tracks: tracksResult.results,
        releases: releasesResult.results,
        instances: instancesResult.results
      };

      return new Response(JSON.stringify(exportData, null, 2), {
        headers: { 
          'Content-Type': 'application/json',
          'Content-Disposition': 'attachment; filename="music-export.json"'
        }
      });
    } catch (error) {
      return createErrorResponse(`Export failed: ${error}`, 500);
    }
  }

  async getLinkedData(): Promise<Response> {
    try {
      const { results: releasesData } = await this.db.prepare("SELECT * FROM releases").all();
      
      const albums = [];
      
      for (const release of releasesData) {
        const { results: tracks } = await this.db.prepare(`
          SELECT tracks.*, instances.track_number 
          FROM tracks 
          JOIN instances ON tracks.id = instances.id 
          WHERE instances.release = ? 
          ORDER BY instances.track_number
        `).bind(release.ID).all();
        
        const totalSeconds = tracks.reduce((total: number, track: any) => {
          if (track.length) {
            return total + mmssToSeconds(track.length);
          }
          return total;
        }, 0);
        
        const albumData = {
          "@type": "MusicAlbum",
          "@id": `album:${release.ID}`,
          "name": release.Name,
          "albumReleaseType": release.Status === "Released" ? "AlbumRelease" : "AlbumRelease",
          "datePublished": release.ReleaseDate,
          "catalogNumber": release.Catalogue,
          "gtin13": release.UPC,
          "url": release.Bandcamp,
          "duration": tracks.length > 0 ? `PT${secondsToMmss(totalSeconds)}` : undefined,
          "track": tracks.map((track: any) => ({
            "@type": "MusicRecording",
            "@id": `track:${track.id}`,
            "name": track.title,
            "byArtist": {
              "@type": "MusicGroup",
              "name": track.artist
            },
            "position": track.track_number,
            "duration": track.length ? `PT${track.length}` : undefined,
            "datePublished": track.year?.toString(),
            "recordingOf": {
              "@type": "MusicComposition",
              "name": track.title,
              "composer": {
                "@type": "Person",
                "name": track.artist
              }
            },
            "isrc": track.ISRC,
            "genre": track.Genre,
            "tempoMarking": track.bpm ? `${track.bpm} BPM` : undefined,
            "recordingType": track.type === "Original" ? "StudioRecording" :
                           track.type === "Remix" ? "RemixRecording" :
                           track.type === "Live" ? "LiveRecording" : "StudioRecording"
          }))
        };
        
        // Remove undefined fields
        Object.keys(albumData).forEach(key => {
          if (albumData[key] === undefined) {
            delete albumData[key];
          }
        });
        
        albums.push(albumData);
      }
      
      const jsonldData = {
        "@context": "https://schema.org",
        "@type": "MusicGroup",
        "name": "Cyjet",
        "album": albums
      };

      return createSuccessResponse(jsonldData);
    } catch (error) {
      return createErrorResponse(`Linked data export failed: ${error}`, 500);
    }
  }

  async getVersion(): Promise<Response> {
    return new Response(JSON.stringify({
      version: '0.1.2',
      api_version: '1.0.0'
    }), {
      headers: { 'Content-Type': 'application/json' }
    });
  }

  async getHealth(): Promise<Response> {
    return new Response(JSON.stringify({
      status: 'healthy',
      timestamp: new Date().toISOString()
    }), {
      headers: { 'Content-Type': 'application/json' }
    });
  }
}

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    const { pathname, method } = { pathname: url.pathname, method: request.method };
    const api = new MusicAPI(env.DB);

    // Add CORS headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    };

    // Handle preflight requests
    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    try {
      // System endpoints
      if (pathname === '/api/v1/health') {
        const response = await api.getHealth();
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }
      
      if (pathname === '/api/v1/version') {
        const response = await api.getVersion();
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      // Tracks endpoints
      if (pathname === '/api/v1/tracks') {
        let response;
        if (method === 'GET') {
          response = await api.getAllTracks();
        } else if (method === 'POST') {
          response = await api.createTrack(request);
        } else {
          response = createErrorResponse('Method not allowed', 405);
        }
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      if (pathname === '/api/v1/tracks/search') {
        if (method !== 'GET') {
          const response = createErrorResponse('Method not allowed', 405);
          Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
          return response;
        }
        const response = await api.searchTracks(url);
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      // Individual track endpoint
      const trackMatch = pathname.match(/^\/api\/v1\/tracks\/(.+)$/);
      if (trackMatch) {
        const trackId = trackMatch[1];
        let response;
        if (method === 'GET') {
          response = await api.getTrack(trackId);
        } else if (method === 'PUT') {
          response = await api.updateTrack(trackId, request);
        } else {
          response = createErrorResponse('Method not allowed', 405);
        }
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      // Releases endpoints
      if (pathname === '/api/v1/releases') {
        let response;
        if (method === 'GET') {
          response = await api.getAllReleases();
        } else if (method === 'POST') {
          response = await api.createRelease(request);
        } else {
          response = createErrorResponse('Method not allowed', 405);
        }
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      // Individual release endpoint
      const releaseMatch = pathname.match(/^\/api\/v1\/releases\/(.+)$/);
      if (releaseMatch) {
        const releaseId = releaseMatch[1];
        
        // Check for tracks sub-endpoint
        const tracksMatch = pathname.match(/^\/api\/v1\/releases\/(.+)\/tracks$/);
        if (tracksMatch) {
          let response;
          if (method === 'GET') {
            response = await api.getReleaseTracks(releaseId);
          } else if (method === 'POST') {
            response = await api.addTrackToRelease(releaseId, request);
          } else {
            response = createErrorResponse('Method not allowed', 405);
          }
          Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
          return response;
        }
        
        // Main release endpoint
        let response;
        if (method === 'GET') {
          response = await api.getRelease(releaseId);
        } else if (method === 'PUT') {
          response = await api.updateRelease(releaseId, request);
        } else {
          response = createErrorResponse('Method not allowed', 405);
        }
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      // Database operations
      if (pathname === '/api/v1/query') {
        if (method !== 'POST') {
          const response = createErrorResponse('Method not allowed', 405);
          Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
          return response;
        }
        const response = await api.executeQuery(request);
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      if (pathname === '/api/v1/export') {
        if (method !== 'GET') {
          const response = createErrorResponse('Method not allowed', 405);
          Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
          return response;
        }
        const response = await api.exportData();
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      if (pathname === '/api/v1/linked-data') {
        if (method !== 'GET') {
          const response = createErrorResponse('Method not allowed', 405);
          Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
          return response;
        }
        const response = await api.getLinkedData();
        Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
        return response;
      }

      // Default response for unmatched routes
      const response = new Response(JSON.stringify({
        message: 'Music Management API v1.0.0',
        endpoints: [
          'GET /api/v1/health',
          'GET /api/v1/version',
          'GET /api/v1/tracks',
          'GET /api/v1/tracks/search',
          'GET /api/v1/tracks/{id}',
          'POST /api/v1/tracks',
          'PUT /api/v1/tracks/{id}',
          'GET /api/v1/releases',
          'GET /api/v1/releases/{id}',
          'GET /api/v1/releases/{id}/tracks',
          'POST /api/v1/releases',
          'PUT /api/v1/releases/{id}',
          'POST /api/v1/releases/{id}/tracks',
          'POST /api/v1/query',
          'GET /api/v1/export',
          'GET /api/v1/linked-data'
        ]
      }, null, 2), {
        headers: { 'Content-Type': 'application/json' }
      });
      Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
      return response;

    } catch (error) {
      const response = createErrorResponse(`Internal server error: ${error}`, 500);
      Object.entries(corsHeaders).forEach(([key, value]) => response.headers.set(key, value));
      return response;
    }
  },
} satisfies ExportedHandler<Env>;
