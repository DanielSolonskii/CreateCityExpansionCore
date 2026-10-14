package dev.createserver.backend.api;

import dev.createserver.backend.account.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-100)
public class AccountGuard extends OncePerRequestFilter {
    private final AccountSettings settings;
    private final RateLimits limits;
    private final dev.createserver.backend.game.GameSettings game;
    public AccountGuard(AccountSettings settings, RateLimits limits, dev.createserver.backend.game.GameSettings game) { this.settings = settings; this.limits = limits; this.game=game; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.startsWith("/api/internal/game/")) {
            if (!game.configured()) { reject(response,503,"GAME_AUTH_NOT_CONFIGURED"); return; }
            if (!game.permits(request.getHeader("X-Game-Service-Secret")) ||
                    Secrets.equal(settings.botBackendSecret()==null ? "" : settings.botBackendSecret(), request.getHeader("X-Game-Service-Secret"))) {
                reject(response,401,"UNAUTHORIZED"); return;
            }
        } else if (path.startsWith("/api/internal/") || path.startsWith("/api/users") ||
                path.startsWith("/api/identities/")) {
            String secret = settings.botBackendSecret();
            if (secret == null || secret.length() < 32) { reject(response, 503, "INTERNAL_API_NOT_CONFIGURED"); return; }
            if (!Secrets.equal(secret, request.getHeader("X-Bot-Service-Secret"))) {
                reject(response, 401, "UNAUTHORIZED"); return;
            }
        }
        int limit = 0;
        long seconds = 60;
        if (path.equals("/api/registrations") && request.getMethod().equals("POST")) { limit = 5; seconds = 900; }
        else if (path.equals("/api/auth/login") || path.equals("/api/auth/devices")) { limit = 20; seconds = 300; }
        else if (path.startsWith("/api/auth/devices/")) { limit = 120; }
        else if (path.startsWith("/api/registrations/")) { limit = 120; }
        else if (path.startsWith("/api/internal/") || path.equals("/api/game-sessions") || path.startsWith("/api/launcher/")) { limit = 120; }
        if (limit > 0 && !limits.allow("ip:" + path.replaceAll("/api/registrations/.*", "/api/registrations/status")
                .replaceAll("/api/auth/devices/.*", "/api/auth/devices/status")
                + ":" + request.getRemoteAddr(), limit, Duration.ofSeconds(seconds))) {
            response.setHeader("Retry-After", Long.toString(seconds));
            reject(response, 429, "RATE_LIMITED"); return;
        }
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
