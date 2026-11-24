const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');

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

// Resort catalog ships with the app; your progress lives in the per-user data folder
const CATALOG_FILE = path.join(__dirname, 'data', 'resorts.json');
const DEFAULT_PROGRESS_FILE = path.join(__dirname, 'data', 'progress.default.json');
const progressFile = () => path.join(app.getPath('userData'), 'progress.json');

ipcMain.handle('load-catalog', () => JSON.parse(fs.readFileSync(CATALOG_FILE, 'utf8')));

ipcMain.handle('load-progress', () => {
  const file = fs.existsSync(progressFile()) ? progressFile() : DEFAULT_PROGRESS_FILE;
  return JSON.parse(fs.readFileSync(file, 'utf8'));
});

ipcMain.handle('save-progress', (_e, progress) => {
  const clean = {
    visited: (progress.visited || []).filter(id => typeof id === 'string'),
    wishlist: (progress.wishlist || []).filter(id => typeof id === 'string'),
  };
  fs.writeFileSync(progressFile(), JSON.stringify(clean, null, 2), 'utf8');
});
