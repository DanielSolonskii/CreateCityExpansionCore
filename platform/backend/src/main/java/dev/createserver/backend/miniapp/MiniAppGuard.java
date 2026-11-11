package dev.createserver.backend.miniapp;
import dev.createserver.backend.account.RateLimits;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
@Component @Order(-90)
public final class MiniAppGuard extends OncePerRequestFilter {
    public static final String ACTOR="miniAppTelegramId";
    private final TelegramProof proof;private final RateLimits limits;
    public MiniAppGuard(TelegramProof proof,RateLimits limits){this.proof=proof;this.limits=limits;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getServletPath().startsWith("/api/mini-app/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        if(!limits.allow("mini-ip:"+request.getRemoteAddr(),600,Duration.ofMinutes(1))){reject(response,429,"RATE_LIMITED");return;}
        String header=request.getHeader("Authorization");long id;
        try{id=proof.verify(header!=null && header.startsWith("tma ") ? header.substring(4) : null);}
        catch(ResponseStatusException invalid){reject(response,invalid.getStatusCode().value(),invalid.getStatusCode().value()==503?"MINI_APP_NOT_CONFIGURED":"MINI_APP_UNAUTHORIZED");return;}
        if(!limits.allow("mini-user:"+id,60,Duration.ofMinutes(1))){reject(response,429,"RATE_LIMITED");return;}
        request.setAttribute(ACTOR,id);chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");if(status==429)response.setHeader("Retry-After","60");
        response.getWriter().write("{\"code\":\""+code+"\"}");
    }
}
