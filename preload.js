const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  loadResorts: () => ipcRenderer.invoke('load-resorts'),
  saveResorts: (resorts) => ipcRenderer.invoke('save-resorts', resorts),
});
