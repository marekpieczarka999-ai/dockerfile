import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Aver Client server: who is online (icon next to nick) + cape storage. Usage: java AverServer.java [port] */
public class AverServer {
    record User(String name, String hash, String model, long seen) { }
    static final Map<String, User> users = new ConcurrentHashMap<>();
    static final Path CAPES = Paths.get("capes"), MODELS = Paths.get("models");

    public static void main(String[] a) throws Exception {
        int port = a.length > 0 ? Integer.parseInt(a[0]) : System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
        Files.createDirectories(CAPES); Files.createDirectories(MODELS);
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/ping", ex -> {
            try {
                String[] b = new String(ex.getRequestBody().readNBytes(1024), StandardCharsets.UTF_8).split("\n", -1);
                String name = b.length > 0 ? b[0].trim() : "", hash = b.length > 1 ? b[1].trim() : "", model = b.length > 2 ? b[2].trim() : "";
                if (!name.matches("[A-Za-z0-9_]{1,16}") || !(hash.isEmpty() || hash.matches("[a-f0-9]{64}")) || !(model.isEmpty() || model.matches("[a-f0-9]{64}"))) { send(ex, 400, ""); return; }
                long now = System.currentTimeMillis();
                users.put(name.toLowerCase(), new User(name, hash, model, now));
                users.values().removeIf(u -> now - u.seen() > 60_000);
                StringBuilder sb = new StringBuilder();
                for (User u : users.values()) sb.append(u.name()).append('\t').append(u.hash()).append('\t').append(u.model()).append('\n');
                send(ex, 200, sb.toString());
            } catch (Exception e) { send(ex, 500, ""); }
        });
        s.createContext("/cape/", ex -> {
            try {
                String h = ex.getRequestURI().getPath().substring("/cape/".length());
                if (!h.matches("[a-f0-9]{64}")) { send(ex, 400, ""); return; }
                Path f = CAPES.resolve(h);
                if (ex.getRequestMethod().equals("POST")) {
                    byte[] d = ex.getRequestBody().readNBytes(4_000_001);
                    if (d.length > 4_000_000) { send(ex, 413, ""); return; }
                    StringBuilder sb = new StringBuilder();
                    for (byte x : MessageDigest.getInstance("SHA-256").digest(d)) sb.append(String.format("%02x", x));
                    if (!sb.toString().equals(h)) { send(ex, 400, ""); return; }
                    Files.write(f, d);
                    send(ex, 200, "ok");
                } else if (Files.exists(f)) {
                    byte[] d = Files.readAllBytes(f);
                    ex.sendResponseHeaders(200, d.length);
                    try (OutputStream o = ex.getResponseBody()) { o.write(d); }
                } else send(ex, 404, "");
            } catch (Exception e) { send(ex, 500, ""); }
        });
        s.createContext("/model/", ex -> {
            try {
                String h = ex.getRequestURI().getPath().substring("/model/".length());
                if (!h.matches("[a-f0-9]{64}")) { send(ex, 400, ""); return; }
                Path f = MODELS.resolve(h);
                if (ex.getRequestMethod().equals("POST")) {
                    byte[] d = ex.getRequestBody().readNBytes(25_000_001);
                    if (d.length > 25_000_000) { send(ex, 413, ""); return; }
                    StringBuilder sb = new StringBuilder();
                    for (byte x : MessageDigest.getInstance("SHA-256").digest(d)) sb.append(String.format("%02x", x));
                    if (!sb.toString().equals(h)) { send(ex, 400, ""); return; }
                    Files.write(f, d);
                    send(ex, 200, "ok");
                } else if (Files.exists(f)) {
                    byte[] d = Files.readAllBytes(f);
                    ex.sendResponseHeaders(200, d.length);
                    try (OutputStream o = ex.getResponseBody()) { o.write(d); }
                } else send(ex, 404, "");
            } catch (Exception e) { send(ex, 500, ""); }
        });
        s.start();
        System.out.println("Aver server on port " + port);
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] d = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, d.length == 0 ? -1 : d.length);
        if (d.length > 0) try (OutputStream o = ex.getResponseBody()) { o.write(d); }
        ex.close();
    }
}
