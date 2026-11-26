import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 3000);
const MESH_BASE_URL = 'https://school.mos.ru';

const ALLOWED_MESH_PREFIXES = [
  '/api/family/',
  '/api/ej/',
  '/api/gamification/',
  '/api/lrs/',
  '/api/food/',
  '/v2/token/',
];

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '2mb' }));

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true, app: 'MESHFOSS Desktop', version: '2.5.0' });
  });

  // Server-side proxy to school.mos.ru for live МЭШ authentication & diary sync without browser CORS issues
  app.post('/api/mesh/proxy', async (req, res) => {
    const { endpoint, method = 'GET', token, profileId, cookie, body, includeAuthToken = true } = req.body ?? {};
    if (
      typeof endpoint !== 'string' ||
      !endpoint.startsWith('/') ||
      !ALLOWED_MESH_PREFIXES.some((prefix) => endpoint.startsWith(prefix))
    ) {
      res.status(400).json({ ok: false, error: 'Unsupported or invalid МЭШ API endpoint.' });
      return;
    }

    const upperMethod = String(method).toUpperCase();
    if (!['GET', 'POST', 'DELETE'].includes(upperMethod)) {
      res.status(405).json({ ok: false, error: 'Method not allowed.' });
      return;
    }

    const headers: Record<string, string> = {
      Accept: 'application/json, text/plain, */*',
      'Accept-Language': 'ru-RU,ru;q=0.9',
      'Content-Type': 'application/json;charset=UTF-8',
      'Profile-Type': 'student',
      'X-Mes-RoleId': '1',
      'X-Mes-Subsystem': 'familyweb',
      'User-Agent': 'MESHFOSS-Desktop/2.5.0',
    };

    if (typeof token === 'string' && token.trim().length > 0) {
      headers.Authorization = `Bearer ${token.trim()}`;
      if (includeAuthToken) {
        headers['Auth-Token'] = token.trim();
      }
    }
    if (typeof profileId === 'string' && profileId.trim().length > 0) {
      headers['Profile-Id'] = profileId.trim();
    }
    if (typeof cookie === 'string' && cookie.trim().length > 0) {
      headers.Cookie = cookie.trim();
    }

    try {
      const upstream = await fetch(`${MESH_BASE_URL}${endpoint}`, {
        method: upperMethod,
        headers,
        body: upperMethod === 'POST' && body !== undefined ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(12000),
      });
      const text = await upstream.text();
      let data: unknown = text;
      try {
        data = JSON.parse(text);
      } catch {
        // Non-JSON response (e.g. token refresh HTML/plain text)
      }
      res.status(upstream.status).json({ ok: upstream.ok, status: upstream.status, data });
    } catch (error) {
      res.status(502).json({
        ok: false,
        status: 502,
        error: error instanceof Error ? error.message : 'Unable to reach school.mos.ru',
      });
    }
  });

  if (process.env.NODE_ENV === 'production') {
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  } else {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`MESHFOSS server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
