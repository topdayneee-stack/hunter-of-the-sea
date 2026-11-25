/**
 * =========================================================================
 * HUNTER OF THE SEA - ELECTRON MAIN PROCESS (DESKTOP APP RUNNER)
 * =========================================================================
 * Mở game trong cửa sổ ứng dụng Desktop riêng biệt, tự động cấu hình
 * máy chủ loopback cục bộ để loại bỏ hoàn toàn hạn chế bảo mật của file:///.
 */

const { app, BrowserWindow, Menu } = require('electron');
const path = require('path');
const http = require('http');
const fs = require('fs');

let mainWindow = null;
let localServer = null;
const PORT = 28766;

function startLocalHttpServer(callback) {
    localServer = http.createServer((req, res) => {
        let reqPath = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '');
        if (!reqPath) reqPath = 'index.html';

        const filePath = path.join(__dirname, reqPath);

        const mimeTypes = {
            '.html': 'text/html; charset=utf-8',
            '.css': 'text/css; charset=utf-8',
            '.js': 'application/javascript; charset=utf-8',
            '.json': 'application/json; charset=utf-8',
            '.png': 'image/png',
            '.jpg': 'image/jpeg',
            '.jpeg': 'image/jpeg',
            '.gif': 'image/gif',
            '.svg': 'image/svg+xml',
            '.ico': 'image/x-icon',
            '.mp3': 'audio/mpeg',
            '.wav': 'audio/wav'
        };

        fs.readFile(filePath, (err, data) => {
            if (err) {
                res.writeHead(404, { 'Content-Type': 'text/plain' });
                res.end('404 Not Found');
                return;
            }
            const ext = path.extname(filePath).toLowerCase();
            res.writeHead(200, {
                'Content-Type': mimeTypes[ext] || 'application/octet-stream',
                'Access-Control-Allow-Origin': '*'
            });
            res.end(data);
        });
    });

    localServer.listen(PORT, '127.0.0.1', () => {
        console.log(`[Electron] Local loopback server running at http://127.0.0.1:${PORT}`);
        if (callback) callback();
    });
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 760,
        minWidth: 1024,
        minHeight: 600,
        title: 'Hunter of the Sea - Thợ Săn Biển Cả (v2.6.0)',
        backgroundColor: '#030d22',
        autoHideMenuBar: true,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            sandbox: false
        }
    });

    Menu.setApplicationMenu(null);

    mainWindow.loadURL(`http://127.0.0.1:${PORT}/index.html`);

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

app.whenReady().then(() => {
    startLocalHttpServer(() => {
        createWindow();
    });

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('window-all-closed', () => {
    if (localServer) {
        try { localServer.close(); } catch (e) {}
    }
    if (process.platform !== 'darwin') {
        app.quit();
    }
});
