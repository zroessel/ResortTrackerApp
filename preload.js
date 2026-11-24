const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  loadCatalog: () => ipcRenderer.invoke('load-catalog'),
  loadProgress: () => ipcRenderer.invoke('load-progress'),
  saveProgress: (progress) => ipcRenderer.invoke('save-progress', progress),
});
