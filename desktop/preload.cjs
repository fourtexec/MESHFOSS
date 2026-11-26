const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('meshfossDesktop', {
  getInfo: () => ipcRenderer.invoke('desktop:info'),
  windowControl: (action) => ipcRenderer.invoke('window:control', action),
  meshRequest: (payload) => ipcRenderer.invoke('mesh:request', payload),
  pickCookieFile: () => ipcRenderer.invoke('mesh:pickCookieFile'),
  saveSecureSession: (json) => ipcRenderer.invoke('storage:saveSecure', json),
  loadSecureSession: () => ipcRenderer.invoke('storage:loadSecure'),
});
