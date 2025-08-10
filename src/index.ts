export interface Env {
  DB: D1Database;
}

export default {
  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url);

    if (pathname === "/api/v1/tracks") {
      const { results } = await env.DB.prepare(
        "SELECT * FROM tracks;",
      )
        // .bind("Tr Tracks")
        .run();
      return Response.json(results);
    }

    return new Response(
      "Call /api/v1/tracks to see all the tracks.",
    );
  },
} satisfies ExportedHandler<Env>;
