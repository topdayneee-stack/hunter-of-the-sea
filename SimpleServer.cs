using System;
using System.IO;
using System.Net;
using System.Threading;

class SimpleServer {
    static int port = 3000;
    static string root = Directory.GetCurrentDirectory();

    static void Main(string[] args) {
        int parsed;
        if (args.Length > 0 && int.TryParse(args[0], out parsed)) port = parsed;

        HttpListener listener = new HttpListener();
        listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
        listener.Prefixes.Add("http://localhost:" + port + "/");

        try {
            listener.Start();
        } catch (Exception ex) {
            Console.WriteLine("[ERROR] Cannot bind port " + port + ": " + ex.Message);
            return;
        }

        Console.WriteLine("=========================================================");
        Console.WriteLine("  HUNTER OF THE SEA - WEB SERVER ONLINE (PORT " + port + ")");
        Console.WriteLine("  Root: " + root);
        Console.WriteLine("=========================================================");

        while (true) {
            try {
                HttpListenerContext ctx = listener.GetContext();
                ThreadPool.QueueUserWorkItem(new WaitCallback(ProcessRequest), ctx);
            } catch {
                break;
            }
        }
    }

    static void ProcessRequest(object state) {
        HttpListenerContext ctx = (HttpListenerContext)state;
        try {
            HttpListenerRequest req = ctx.Request;
            HttpListenerResponse res = ctx.Response;

            res.Headers.Add("Access-Control-Allow-Origin", "*");
            res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.Headers.Add("Access-Control-Allow-Headers", "Content-Type");

            if (req.HttpMethod == "OPTIONS") {
                res.StatusCode = 200;
                res.Close();
                return;
            }

            string raw = req.Url.LocalPath.TrimStart('/');
            if (string.IsNullOrEmpty(raw)) raw = "index.html";
            raw = Uri.UnescapeDataString(raw);

            string file = Path.Combine(root, raw);
            if (File.Exists(file)) {
                string ext = Path.GetExtension(file).ToLower();
                string mime = "application/octet-stream";
                switch (ext) {
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
                byte[] bytes = File.ReadAllBytes(file);
                res.ContentLength64 = bytes.Length;
                res.OutputStream.Write(bytes, 0, bytes.Length);
            } else {
                res.StatusCode = 404;
                byte[] notFound = System.Text.Encoding.UTF8.GetBytes("404 Not Found");
                res.OutputStream.Write(notFound, 0, notFound.Length);
            }
            res.Close();
        } catch {}
    }
}
