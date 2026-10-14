package dev.createserver.bot;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

public final class BackendClient {
    private final URI base;
    private final String serviceSecret;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    public BackendClient(URI base, String secret, HttpClient http) {
        this.base = base; this.serviceSecret = secret; this.http = http;
    }
    public int confirm(String token, long telegramUserId, String username) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationToken", token);
        body.put("telegramUserId", telegramUserId);
        body.put("telegramUsername", username);
        return send("POST", "/api/internal/telegram/confirm", json.writeValueAsString(body));
    }
    public int linkedUser(long telegramUserId) throws IOException, InterruptedException {
        return send("GET", "/api/internal/telegram/users/" + telegramUserId, null);
    }
    public Account account(long telegramUserId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/internal/telegram/users/" + telegramUserId))
            .timeout(Duration.ofSeconds(10)).header("X-Bot-Service-Secret", serviceSecret).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new IOException("Backend lookup unavailable");
        try {
            var data = json.readTree(response.body());
            UUID.fromString(data.path("platformUserId").asText());
            String status = data.path("status").asText();
            String username = data.path("username").isNull() ? null : data.path("username").asText();
            if (!Set.of("ACTIVE", "DISABLED", "PENDING_VERIFICATION").contains(status) ||
                    (username != null && !username.matches("[a-z][a-z0-9_]{2,31}"))) throw new IllegalArgumentException();
            return new Account(username, status);
        } catch (RuntimeException exception) { throw new IOException("Invalid Backend account response"); }
    }
    public record Account(String username, String status) {
        public boolean active() { return "ACTIVE".equals(status) && username != null; }
    }
    public JsonNode deviceRequest(String path,Object body) throws IOException, InterruptedException {
        HttpRequest.Builder request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
            .header("X-Bot-Service-Secret",serviceSecret).header("Content-Type","application/json");
        if (body==null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200 && response.statusCode()!=204) throw new IOException("Device action unavailable");
        try { return response.statusCode()==204 ? json.readTree("{}") : json.readTree(response.body()); }
        catch(RuntimeException exception) { throw new IOException("Invalid Backend response"); }
    }
    private int send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("X-Bot-Service-Secret", serviceSecret).header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    public Preview prepare(String token, String code, long id) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationToken", token); body.put("verificationCode", code); body.put("telegramUserId", id);
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/internal/telegram/prepare"))
            .timeout(Duration.ofSeconds(10)).header("X-Bot-Service-Secret", serviceSecret)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return new Preview(response.statusCode(), null, null);
        try {
            JsonNode data = json.readTree(response.body());
            String ticket = data.path("confirmationToken").asText(), username = data.path("username").asText();
            if (!ticket.matches("[A-Za-z0-9_-]{43}") || !username.matches("[a-z][a-z0-9_]{2,31}")) throw new IllegalArgumentException();
            return new Preview(200, ticket, username);
        } catch (RuntimeException exception) { throw new IOException("Invalid Backend response"); }
    }
    public int accept(String ticket, long id, String username) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("confirmationToken", ticket); body.put("telegramUserId", id); body.put("telegramUsername", username);
        return send("POST", "/api/internal/telegram/accept", json.writeValueAsString(body));
    }
    public record Preview(int status, String ticket, String username) {
        @Override public String toString() { return "Preview[redacted]"; }
    }
}
