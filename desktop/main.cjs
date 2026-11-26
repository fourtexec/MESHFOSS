/**
 * MESHFOSS Desktop — Electron Main Process (Windows / macOS / Linux)
 *
 * Provides native OS window vibrancy (macOS Tahoe under-window vibrancy,
 * Windows 11 Acrylic backdrop, Linux translucent compositor), CORS-free
 * МЭШ (school.mos.ru) HTTP proxy over Node fetch, OS-encrypted token storage
 * via Electron safeStorage, native mos_cookies.json file import, and system tray.
 */

const { app, BrowserWindow, ipcMain, dialog, shell, safeStorage, Tray, Menu, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');

const MESH_BASE_URL = 'https://school.mos.ru';
const DEV_SERVER_URL = process.env.MESHFOSS_DEV_URL || null;

let mainWindow = null;
let tray = null;

function getSessionFilePath() {
  return path.join(app.getPath('userData'), 'meshfoss-session.enc');
}

function createMainWindow() {
  const isMac = process.platform === 'darwin';
  const isWin = process.platform === 'win32';

  const windowOptions = {
    width: 1360,
    height: 860,
    minWidth: 960,
    minHeight: 640,
    title: 'MESHFOSS',
    backgroundColor: isMac ? '#00000000' : '#0B0F1A',
    show: false,
    frame: !isWin,
    titleBarStyle: isMac ? 'hiddenInset' : 'hidden',
    trafficLightPosition: isMac ? { x: 18, y: 18 } : undefined,
    vibrancy: isMac ? 'under-window' : undefined,
    visualEffectState: isMac ? 'active' : undefined,
    backgroundMaterial: isWin ? 'acrylic' : undefined,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  };

  mainWindow = new BrowserWindow(windowOptions);

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show();
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  if (DEV_SERVER_URL) {
    mainWindow.loadURL(DEV_SERVER_URL);
  } else {
    mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function createTray() {
  try {
    const iconSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect width="32" height="32" rx="9" fill="#0A84FF"/><path d="M8 22V10h3.2l4.8 7.5L20.8 10H24v12h-3v-7.4l-4.2 6.4h-1.6L11 14.6V22H8z" fill="#fff"/></svg>`;
    const icon = nativeImage.createFromDataURL(`data:image/svg+xml;base64,${Buffer.from(iconSvg).toString('base64')}`);
    tray = new Tray(icon.resize({ width: 18, height: 18 }));
    tray.setToolTip('MESHFOSS — МЭШ Desktop');
    tray.setContextMenu(
      Menu.buildFromTemplate([
        { label: 'Открыть MESHFOSS', click: () => mainWindow?.show() },
        { type: 'separator' },
        { label: 'Выход', click: () => app.quit() },
      ])
    );
    tray.on('click', () => {
      if (mainWindow) {
        if (mainWindow.isMinimized()) mainWindow.restore();
        mainWindow.show();
        mainWindow.focus();
      }
    });
  } catch {
    // Headless Linux environments without appindicator gracefully skip tray
  }
}

/* ---------- IPC Handlers ---------- */

ipcMain.handle('desktop:info', () => ({
  isElectron: true,
  platform: process.platform,
  arch: process.arch,
  appVersion: app.getVersion(),
  electronVersion: process.versions.electron,
}));

ipcMain.handle('window:control', (_event, action) => {
  if (!mainWindow) return;
  if (action === 'minimize') mainWindow.minimize();
  if (action === 'maximize') {
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  }
  if (action === 'close') mainWindow.close();
});

ipcMain.handle('mesh:request', async (_event, payload) => {
  const { endpoint, method = 'GET', token, profileId, cookie, body, includeAuthToken = true } = payload || {};
  if (!endpoint || typeof endpoint !== 'string' || !endpoint.startsWith('/')) {
    return { ok: false, status: 400, error: 'Invalid endpoint path' };
  }

  const headers = {
    Accept: 'application/json, text/plain, */*',
    'Accept-Language': 'ru-RU,ru;q=0.9',
    'Content-Type': 'application/json;charset=UTF-8',
    'Profile-Type': 'student',
    'X-Mes-RoleId': '1',
    'X-Mes-Subsystem': 'familyweb',
    'User-Agent': `MESHFOSS-Desktop/${app.getVersion()} (${process.platform})`,
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
    if (includeAuthToken) headers['Auth-Token'] = token;
  }
  if (profileId) headers['Profile-Id'] = String(profileId);
  if (cookie) headers.Cookie = String(cookie);

  try {
    const res = await fetch(`${MESH_BASE_URL}${endpoint}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let data = null;
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
    return { ok: res.ok, status: res.status, data };
  } catch (err) {
    return { ok: false, status: 0, error: err instanceof Error ? err.message : 'Network error' };
  }
});

ipcMain.handle('mesh:pickCookieFile', async () => {
  const result = await dialog.showOpenDialog({
    title: 'Выберите файл mos_cookies.json',
    filters: [{ name: 'JSON Cookies', extensions: ['json', 'txt'] }],
    properties: ['openFile'],
  });
  if (result.canceled || !result.filePaths[0]) return null;
  return fs.readFileSync(result.filePaths[0], 'utf8');
});

ipcMain.handle('storage:saveSecure', (_event, jsonString) => {
  const filePath = getSessionFilePath();
  if (safeStorage.isEncryptionAvailable()) {
    const encrypted = safeStorage.encryptString(String(jsonString));
    fs.writeFileSync(filePath, encrypted);
    return { encrypted: true };
  }
  fs.writeFileSync(filePath, String(jsonString), 'utf8');
  return { encrypted: false };
});

ipcMain.handle('storage:loadSecure', () => {
  const filePath = getSessionFilePath();
  if (!fs.existsSync(filePath)) return null;
  const raw = fs.readFileSync(filePath);
  if (safeStorage.isEncryptionAvailable()) {
    try {
      return safeStorage.decryptString(raw);
    } catch {
      return raw.toString('utf8');
    }
  }
  return raw.toString('utf8');
});

app.whenReady().then(() => {
  createMainWindow();
  createTray();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
