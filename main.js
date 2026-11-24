const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');

const RESORTS_FILE = path.join(__dirname, 'resorts.txt');

function createWindow() {
  const win = new BrowserWindow({
    width: 390,
    height: 720,
    resizable: false,
    titleBarStyle: 'hidden',
    backgroundColor: '#060d14',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
    },
  });
  win.loadFile('index.html');
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('load-resorts', () => {
  const text = fs.readFileSync(RESORTS_FILE, 'utf8');
  return text.split('\n')
    .map(l => l.trim())
    .filter(l => l.length > 0)
    .map(l => {
      const p = l.split(',');
      if (p.length < 5) return null;
      return { name: p[0].trim(), country: p[1].trim(), region: p[2].trim(), description: p[3].trim(), visited: p[4].trim() === 'true' };
    })
    .filter(Boolean);
});

ipcMain.handle('save-resorts', (_e, resorts) => {
  const lines = resorts.map(r => [r.name, r.country, r.region, r.description, r.visited].join(','));
  fs.writeFileSync(RESORTS_FILE, lines.join('\n'), 'utf8');
});
