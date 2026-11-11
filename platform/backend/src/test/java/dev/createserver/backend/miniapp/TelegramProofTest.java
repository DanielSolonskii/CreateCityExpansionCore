package dev.createserver.backend.miniapp;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class TelegramProofTest {
    static final String TOKEN="test-only-miniapp-key";
    static String proof(long id,long date){return sign(new TreeMap<>(Map.of("user","{\"id\":"+id+",\"first_name\":\"Test\"}","auth_date",Long.toString(date),"query_id","test-query")));}
    static String sign(TreeMap<String,String> fields){
        try{
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] secret=mac.doFinal(TOKEN.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));String data=String.join("\n",fields.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());
            String hash=HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            return String.join("&",fields.entrySet().stream().map(e->e.getKey()+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).toList())+"&hash="+hash;
        }catch(Exception failure){throw new RuntimeException(failure);}
    }
    final long now=Instant.parse("2026-10-16T12:00:00Z").getEpochSecond();
    TelegramProof validator(){return new TelegramProof(TOKEN,Clock.fixed(Instant.ofEpochSecond(now),ZoneOffset.UTC));}
    @Test void validNumericIdentityAndUnicode(){assertThat(validator().verify(proof(9000000001L,now))).isEqualTo(9000000001L);}
    @Test void tamperingDuplicateFieldsMalformedHashAndOversizeFailClosed(){
        var valid=proof(111,now);
        for(String raw:List.of(valid.replace("111","222"),valid+"&user=x",valid+"&hash=x",valid.replace("hash=","hash=X"),"%xx=x", "x".repeat(8193)))
            assertThatThrownBy(()->validator().verify(raw)).isInstanceOf(ResponseStatusException.class).hasMessageNotContaining(TOKEN);
    }
    @Test void expiredFutureAndBotIdentityRejected(){
        for(long date:new long[]{now-601,now+31,Long.MIN_VALUE,Long.MAX_VALUE})assertThatThrownBy(()->validator().verify(proof(111,date))).isInstanceOf(ResponseStatusException.class);
        for(String user:List.of("{\"id\":111,\"is_bot\":true}","{\"id\":\"111\"}","{\"id\":1.5}","{\"id\":0}","{\"id\":-1}","{}"))
            assertThatThrownBy(()->validator().verify(sign(new TreeMap<>(Map.of("auth_date",Long.toString(now),"user",user))))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void missingConfigurationCannotAuthenticate(){assertThatThrownBy(()->new TelegramProof("",Clock.systemUTC()).verify(proof(111,now))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));}
}
