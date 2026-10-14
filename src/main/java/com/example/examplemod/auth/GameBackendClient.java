package com.example.examplemod.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Server-only calls, bounded body/deadline, no redirects and no exception/body logging. */
final class GameBackendClient {
    static final class InvalidTelemetry extends IllegalStateException {
        InvalidTelemetry() { super("Telemetry batch rejected by validation"); }
    }
    private final URI base;
    private final String credential;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    GameBackendClient(String url, String credential) {
        URI uri = URI.create(url.endsWith("/") ? url : url + "/");
        boolean local = "http".equals(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()) || "[::1]".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !"/".equals(uri.getPath())
                || credential == null || credential.length() < 32 || credential.length() > 512 || credential.contains("\n") || credential.contains("\r"))
            throw new IllegalArgumentException("Game backend is not configured safely");
        base=uri; this.credential=credential;
    }
    JsonObject post(String path, Object body) throws Exception {
        var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5))
                .header("X-Game-Service-Secret",credential).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try (var stream=response.body()) {
            byte[] bytes=stream.readNBytes(32769);
            if (response.statusCode()==400 && bytes.length<=32768 && "api/internal/game/telemetry".equals(path)) {
                try {
                    var error=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                    if(error.has("code") && "INVALID_TELEMETRY".equals(error.get("code").getAsString())) throw new InvalidTelemetry();
                } catch (InvalidTelemetry rejected) { throw rejected; }
                catch (RuntimeException malformed) { /* Unknown errors retain normal retry behavior. */ }
            }
            if (response.statusCode()!=200 || bytes.length>32768) throw new IllegalStateException("Game admission unavailable");
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
