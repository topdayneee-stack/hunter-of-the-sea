using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace HunterOfTheSea
{
    static class Program
    {
        private static HttpListener listener;
        private static string appRoot;
        private static int port = 28765;
        private static volatile bool isRunning = true;
        private static Process browserProcess;

        [STAThread]
        static void Main(string[] args)
        {
            appRoot = AppDomain.CurrentDomain.BaseDirectory;

            // 1. Tìm cổng loopback còn trống
            port = FindAvailablePort(28765, 28800);

            // 2. Khởi chạy HTTP Server cục bộ nhúng sẵn
            listener = new HttpListener();
            listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
            listener.Prefixes.Add("http://localhost:" + port + "/");

            try
            {
                listener.Start();
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Không thể khởi động cổng " + port + ": " + ex.Message + "\nVui lòng thử lại sau.",
                    "Hunter of the Sea - Lỗi Khởi Động",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
                return;
            }

            // Chạy luồng phục vụ file trong nền
            Thread serverThread = new Thread(ServerWorker);
            serverThread.IsBackground = true;
            serverThread.Start();

            // 3. Khởi chạy giao diện game dạng Desktop App Window (Edge / Chrome App Mode)
            string browserPath = FindBrowserPath();
            if (string.IsNullOrEmpty(browserPath))
            {
                // Mở trình duyệt mặc định nếu không tìm thấy Edge/Chrome
                Process.Start("http://127.0.0.1:" + port + "/index.html");
                return;
            }

            string userDataDir = Path.Combine(Path.GetTempPath(), "HunterOfTheSea_Profile");
            string appUrl = "http://127.0.0.1:" + port + "/index.html";
            string arguments = string.Format(
                "--app=\"{0}\" --window-size=1280,760 --user-data-dir=\"{1}\" --no-first-run --no-default-browser-check",
                appUrl,
                userDataDir
            );

            ProcessStartInfo psi = new ProcessStartInfo(browserPath, arguments);
            psi.UseShellExecute = false;

            try
            {
                browserProcess = Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Lỗi khi mở cửa sổ game: " + ex.Message, "Hunter of the Sea", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // Chờ người chơi đóng cửa sổ game
            if (browserProcess != null)
            {
                browserProcess.WaitForExit();
            }

            // Dọn dẹp server
            isRunning = false;
            try
            {
                listener.Stop();
                listener.Close();
            }
            catch { }
        }

        private static int FindAvailablePort(int start, int end)
        {
            for (int p = start; p <= end; p++)
            {
                try
                {
                    TcpListener tcp = new TcpListener(IPAddress.Loopback, p);
                    tcp.Start();
                    tcp.Stop();
                    return p;
                }
                catch { }
            }
            return start;
        }

        private static string FindBrowserPath()
        {
            string[] candidates = new string[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Google\Chrome\Application\chrome.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Google\Chrome\Application\chrome.exe")
            };

            foreach (string path in candidates)
            {
                if (File.Exists(path)) return path;
            }
            return null;
        }

        private static void ServerWorker()
        {
            while (isRunning && listener.IsListening)
            {
                try
                {
                    HttpListenerContext ctx = listener.GetContext();
                    ThreadPool.QueueUserWorkItem(ProcessClientRequest, ctx);
                }
                catch
                {
                    if (!isRunning) break;
                }
            }
        }

        private static void ProcessClientRequest(object state)
        {
            HttpListenerContext ctx = (HttpListenerContext)state;
            try
            {
                HttpListenerRequest req = ctx.Request;
                HttpListenerResponse res = ctx.Response;

                res.Headers.Add("Access-Control-Allow-Origin", "*");
                res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                res.Headers.Add("Access-Control-Allow-Headers", "Content-Type");

                if (req.HttpMethod == "OPTIONS")
                {
                    res.StatusCode = 200;
                    res.Close();
                    return;
                }

                string raw = req.Url.LocalPath.TrimStart('/');
                if (string.IsNullOrEmpty(raw)) raw = "index.html";
                raw = Uri.UnescapeDataString(raw);

                string filePath = Path.Combine(appRoot, raw);

                if (File.Exists(filePath))
                {
                    string ext = Path.GetExtension(filePath).ToLower();
                    string mime = "application/octet-stream";
                    switch (ext)
                    {
                        case ".html": case ".htm": mime = "text/html; charset=utf-8"; break;
                        case ".css": mime = "text/css; charset=utf-8"; break;
                        case ".js": mime = "application/javascript; charset=utf-8"; break;
                        case ".json": mime = "application/json; charset=utf-8"; break;
                        case ".png": mime = "image/png"; break;
                        case ".jpg": case ".jpeg": mime = "image/jpeg"; break;
                        case ".gif": mime = "image/gif"; break;
                        case ".svg": mime = "image/svg+xml"; break;
                        case ".ico": mime = "image/x-icon"; break;
                        case ".mp3": mime = "audio/mpeg"; break;
                        case ".wav": mime = "audio/wav"; break;
                    }

                    res.ContentType = mime;
                    byte[] bytes = File.ReadAllBytes(filePath);
                    res.ContentLength64 = bytes.Length;
                    res.OutputStream.Write(bytes, 0, bytes.Length);
                }
                else
                {
                    res.StatusCode = 404;
                    byte[] notFound = Encoding.UTF8.GetBytes("404 Not Found: " + raw);
                    res.OutputStream.Write(notFound, 0, notFound.Length);
                }
                res.Close();
            }
            catch { }
        }
    }
}
