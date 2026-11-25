using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Collections.Generic;

class FastServer {
    static int port = 3000;
    static string root = Directory.GetCurrentDirectory();

    // In-memory room store for PvP matchmaking
    static Dictionary<string, RoomData> rooms = new Dictionary<string, RoomData>();
    static object roomLock = new object();

    class RoomData {
        public string roomCode;
        public string status; // WAITING, IN_GAME, CLOSED
        public string host;
        public int hostCharacter;
        public string guest;
        public int guestCharacter;
        public long lastActiveAt;
    }

    static void Main(string[] args) {
        int p;
        if (args.Length > 0 && int.TryParse(args[0], out p)) port = p;

        TcpListener server = new TcpListener(IPAddress.Any, port);
        try {
            server.Start();
        } catch (Exception ex) {
            Console.WriteLine("[ERROR] " + ex.Message);
            return;
        }

        Console.WriteLine("=========================================================");
        Console.WriteLine("  HUNTER OF THE SEA - WEB + API SERVER (PORT " + port + ")");
        Console.WriteLine("  Root: " + root);
        Console.WriteLine("=========================================================");

        while (true) {
            try {
                TcpClient client = server.AcceptTcpClient();
                ThreadPool.QueueUserWorkItem(ProcessClient, client);
            } catch {
                break;
            }
        }
    }

    static void ProcessClient(object state) {
        TcpClient client = (TcpClient)state;
        try {
            using (NetworkStream stream = client.GetStream()) {
                byte[] buffer = new byte[16384];
                int bytesRead = stream.Read(buffer, 0, buffer.Length);
                if (bytesRead <= 0) return;

                string request = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                int headEnd = request.IndexOf("\r\n\r\n");
                string headerPart = (headEnd != -1) ? request.Substring(0, headEnd) : request;
                string bodyPart = "";
                if (headEnd != -1 && headEnd + 4 <= request.Length) {
                    bodyPart = request.Substring(headEnd + 4);
                }

                // Doc du Content-Length neu body con nam tren stream
                int clIdx = headerPart.IndexOf("Content-Length:", StringComparison.OrdinalIgnoreCase);
                if (clIdx != -1) {
                    int clEnd = headerPart.IndexOf("\r\n", clIdx);
                    if (clEnd != -1) {
                        int expectedLen;
                        if (int.TryParse(headerPart.Substring(clIdx + 15, clEnd - (clIdx + 15)).Trim(), out expectedLen)) {
                            int currentLen = Encoding.UTF8.GetByteCount(bodyPart);
                            while (currentLen < expectedLen && stream.DataAvailable) {
                                int r = stream.Read(buffer, 0, Math.Min(buffer.Length, expectedLen - currentLen));
                                if (r <= 0) break;
                                bodyPart += Encoding.UTF8.GetString(buffer, 0, r);
                                currentLen += r;
                            }
                        }
                    }
                }

                string[] lines = headerPart.Split(new string[] { "\r\n" }, StringSplitOptions.None);
                if (lines.Length == 0) return;

                string[] reqParts = lines[0].Split(' ');
                if (reqParts.Length < 2) return;

                string method = reqParts[0];
                string fullUrl = reqParts[1];
                string path = fullUrl.Split('?')[0].TrimStart('/');

                // CORS Pre-flight
                if (method == "OPTIONS") {
                    string optHeaders = "HTTP/1.1 200 OK\r\n" +
                                        "Access-Control-Allow-Origin: *\r\n" +
                                        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                                        "Access-Control-Allow-Headers: Content-Type, Authorization, Accept\r\n" +
                                        "Content-Length: 0\r\n\r\n";
                    byte[] optBytes = Encoding.ASCII.GetBytes(optHeaders);
                    stream.Write(optBytes, 0, optBytes.Length);
                    return;
                }

                // API ROUTING: /api/configs, /api/rooms/*
                if (path.StartsWith("api/")) {
                    HandleApi(method, path, bodyPart, stream);
                    return;
                }

                // STATIC FILE ROUTING
                if (string.IsNullOrEmpty(path)) path = "index.html";
                path = Uri.UnescapeDataString(path);
                string filePath = Path.Combine(root, path.Replace('/', Path.DirectorySeparatorChar));

                if (File.Exists(filePath)) {
                    byte[] fileBytes = File.ReadAllBytes(filePath);
                    string ext = Path.GetExtension(filePath).ToLower();
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

                    string head = "HTTP/1.1 200 OK\r\n" +
                                  "Content-Type: " + mime + "\r\n" +
                                  "Content-Length: " + fileBytes.Length + "\r\n" +
                                  "Access-Control-Allow-Origin: *\r\n" +
                                  "Connection: close\r\n\r\n";
                    byte[] headBytes = Encoding.ASCII.GetBytes(head);
                    stream.Write(headBytes, 0, headBytes.Length);
                    if (method != "HEAD") {
                        stream.Write(fileBytes, 0, fileBytes.Length);
                    }
                } else {
                    byte[] notFound = Encoding.UTF8.GetBytes("404 Not Found: " + path);
                    string head = "HTTP/1.1 404 Not Found\r\n" +
                                  "Content-Type: text/plain; charset=utf-8\r\n" +
                                  "Content-Length: " + notFound.Length + "\r\n" +
                                  "Access-Control-Allow-Origin: *\r\n" +
                                  "Connection: close\r\n\r\n";
                    byte[] headBytes = Encoding.ASCII.GetBytes(head);
                    stream.Write(headBytes, 0, headBytes.Length);
                    stream.Write(notFound, 0, notFound.Length);
                }
            }
        } catch {}
        finally {
            try { client.Close(); } catch {}
        }
    }

    static void HandleApi(string method, string path, string body, NetworkStream stream) {
        string json = "{}";
        int status = 200;

        if (path == "api/configs") {
            json = "[{\"configKey\":\"SERVER_STATUS\",\"configValue\":\"ONLINE\"}]";
        } else if (path == "api/users/login") {
            json = "{\"userId\":1,\"username\":\"Player\"}";
        } else if (path == "api/rooms/create" && method == "POST") {
            string code = new Random().Next(1000, 9999).ToString();
            string uname = ExtractJson(body, "username");
            if (string.IsNullOrEmpty(uname)) uname = "Player1";
            int cid = 2;
            int.TryParse(ExtractJson(body, "characterId"), out cid);
            if (cid == 0) cid = 2;

            RoomData r = new RoomData();
            r.roomCode = code;
            r.status = "WAITING";
            r.host = uname;
            r.hostCharacter = cid;
            r.lastActiveAt = DateTime.UtcNow.Ticks;

            lock (roomLock) {
                rooms[code] = r;
            }

            json = FormatRoomJson(r);
        } else if (path == "api/rooms/join" && method == "POST") {
            string code = ExtractJson(body, "roomCode");
            string uname = ExtractJson(body, "username");
            if (string.IsNullOrEmpty(uname)) uname = "Player2";
            int cid = 1;
            int.TryParse(ExtractJson(body, "characterId"), out cid);
            if (cid == 0) cid = 1;

            lock (roomLock) {
                if (rooms.ContainsKey(code)) {
                    RoomData r = rooms[code];
                    if (r.status == "WAITING" || r.status == "READY") {
                        r.guest = uname;
                        r.guestCharacter = cid;
                        r.status = "READY";
                        r.lastActiveAt = DateTime.UtcNow.Ticks;
                        json = FormatRoomJson(r);
                    } else {
                        status = 400;
                        json = "{\"error\":\"Phòng đã đủ người hoặc đang thi đấu!\"}";
                    }
                } else {
                    status = 404;
                    json = "{\"error\":\"Mã phòng không tồn tại hoặc phòng đã bị đóng!\",\"debugCode\":\"" + code + "\",\"rawBody\":\"" + body.Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "") + "\"}";
                }
            }
        } else if (path.StartsWith("api/rooms/")) {
            string sub = path.Substring(10);
            if (sub.Contains("/")) {
                string[] parts = sub.Split('/');
                string code = parts[0];
                string action = parts[1];
                lock (roomLock) {
                    if (rooms.ContainsKey(code)) {
                        RoomData r = rooms[code];
                        r.lastActiveAt = DateTime.UtcNow.Ticks;
                        if (action == "leave") {
                            rooms.Remove(code);
                        } else if (action == "start") {
                            r.status = "IN_GAME";
                        }
                        json = FormatRoomJson(r);
                    } else {
                        status = 404;
                        json = "{\"error\":\"Phòng không tồn tại\"}";
                    }
                }
            } else {
                string code = sub;
                lock (roomLock) {
                    if (rooms.ContainsKey(code)) {
                        json = FormatRoomJson(rooms[code]);
                    } else {
                        status = 404;
                        json = "{\"error\":\"Phòng không tồn tại\"}";
                    }
                }
            }
        }

        byte[] jsonBytes = Encoding.UTF8.GetBytes(json);
        string head = "HTTP/1.1 " + status + " OK\r\n" +
                      "Content-Type: application/json; charset=utf-8\r\n" +
                      "Content-Length: " + jsonBytes.Length + "\r\n" +
                      "Access-Control-Allow-Origin: *\r\n" +
                      "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                      "Connection: close\r\n\r\n";
        byte[] headBytes = Encoding.ASCII.GetBytes(head);
        stream.Write(headBytes, 0, headBytes.Length);
        stream.Write(jsonBytes, 0, jsonBytes.Length);
    }

    static string ExtractJson(string json, string key) {
        if (string.IsNullOrEmpty(json)) return "";
        int idx = json.IndexOf(key, StringComparison.OrdinalIgnoreCase);
        if (idx == -1) return "";
        int colon = json.IndexOf(':', idx);
        if (colon == -1) return "";
        string rest = json.Substring(colon + 1).TrimStart(' ', '\t', '\r', '\n');
        if (rest.StartsWith("\"")) {
            int close = rest.IndexOf('"', 1);
            if (close != -1) return rest.Substring(1, close - 1);
        } else {
            int end = rest.IndexOfAny(new char[] { ',', '}', ' ', '\r', '\n', '"' });
            if (end != -1) return rest.Substring(0, end).Trim('"', ' ');
            return rest.Trim('"', ' ');
        }
        return "";
    }

    static string FormatRoomJson(RoomData r) {
        string guestJson = (r.guest != null) ? "{\"userId\":2,\"username\":\"" + r.guest + "\"}" : "null";
        string guestChar = (r.guest != null) ? "{\"characterId\":" + r.guestCharacter + "}" : "null";
        return "{" +
            "\"roomCode\":\"" + r.roomCode + "\"," +
            "\"status\":\"" + r.status + "\"," +
            "\"host\":{\"userId\":1,\"username\":\"" + r.host + "\"}," +
            "\"hostCharacter\":{\"characterId\":" + r.hostCharacter + "}," +
            "\"guest\":" + guestJson + "," +
            "\"guestCharacter\":" + guestChar + "," +
            "\"lastActiveAt\":" + r.lastActiveAt +
        "}";
    }
}
