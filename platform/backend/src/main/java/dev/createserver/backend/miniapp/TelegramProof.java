package dev.createserver.backend.miniapp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Telegram initData is a short-lived read-only bearer proof, never a platform/game credential. */
@Component
public final class TelegramProof {
    private final String token;
    private final Clock clock;
    public TelegramProof(@Value("${MINI_APP_BOT_TOKEN:}") String token, Clock clock) {this.token=token;this.clock=clock;}
    public long verify(String raw) {
        if(token==null || token.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MINI_APP_NOT_CONFIGURED");
        try {
            if(raw==null || raw.isBlank() || raw.length()>8192)throw new IllegalArgumentException();
            var fields=new TreeMap<String,String>();
            for(String pair:raw.split("&",-1)) {
                int separator=pair.indexOf('=');if(separator<1)throw new IllegalArgumentException();
                String key=URLDecoder.decode(pair.substring(0,separator),StandardCharsets.UTF_8);
                String value=URLDecoder.decode(pair.substring(separator+1),StandardCharsets.UTF_8);
                if(!key.matches("[a-z_]{1,64}") || value.contains("\n") || value.contains("\r") || fields.putIfAbsent(key,value)!=null)throw new IllegalArgumentException();
            }
            String hash=fields.remove("hash");
            if(hash==null || !hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            String data=fields.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("\n"));
            byte[] secret=hmac("WebAppData".getBytes(StandardCharsets.UTF_8),token);
            if(!MessageDigest.isEqual(HexFormat.of().parseHex(hash),hmac(secret,data)))throw new IllegalArgumentException();
            long date=Long.parseLong(fields.get("auth_date")),now=clock.instant().getEpochSecond();
            if(date<now-600 || date>now+30)throw new IllegalArgumentException();
            var user=JsonMapper.builder().build().readTree(fields.get("user"));
            if(!user.path("id").isIntegralNumber() || !user.path("id").canConvertToLong() || user.path("is_bot").asBoolean())throw new IllegalArgumentException();
            long id=user.path("id").asLong();if(id<=0 || id>4503599627370495L)throw new IllegalArgumentException();
            return id;
        } catch(Exception invalid) {throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"MINI_APP_UNAUTHORIZED");}
    }
    private static byte[] hmac(byte[] key,String value) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
    @Override public String toString(){return "TelegramProof[redacted]";}
}
