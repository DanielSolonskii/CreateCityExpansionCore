package dev.createserver.backend.broadcast;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
abstract class LauncherNewsContract {
    @Autowired JdbcTemplate db;
    @LocalServerPort int port;
    List<UUID> ids=new ArrayList<>();
    UUID post(String status,String text,Instant published) {
        UUID id=UUID.randomUUID(); ids.add(id);
        db.update("INSERT INTO broadcast_post(id,creator_admin,text,photo_file_id,status,created_at,confirmed_at) VALUES(?,111,?,'PRIVATE_FILE_ID',?,?,?)",
            id,text,status,Timestamp.from(Instant.now()),published==null?null:Timestamp.from(published)); return id;
    }
    @AfterEach void cleanup() { for(UUID id:ids) db.update("DELETE FROM broadcast_post WHERE id=?",id); }
    @Test void publicFeedOnlyConfirmedPostsAndNoPrivateFields() throws Exception {
        post("DRAFT","PRIVATE_DRAFT",null); post("CANCELLED","PRIVATE_CANCELLED",null);
        post("COMPLETED","PRIVATE_UNCONFIRMED",null);
        var latest=post("QUEUED","#update 1.0.18\nИсправления <b>текст</b>",Instant.parse("2099-01-01T00:00:00Z"));
        post("COMPLETED","Обычная новость",Instant.parse("2098-01-01T00:00:00Z"));
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/launcher/news")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(latest.toString(),"1.0.18","Обычная новость","hasPhoto");
        assertThat(response.body()).doesNotContain("PRIVATE_","creatorAdmin","telegramUserId","photoFileId","attemptToken","eligible","password");
        assertThat(response.body().indexOf(latest.toString())).isLessThan(response.body().indexOf("Обычная новость"));
    }
    @Test void feedIsBoundedAndDraftsAreNeverPublishedByReading() {
        for(int i=0;i<55;i++)post("COMPLETED","Новости "+i,Instant.parse("2090-01-01T00:00:00Z").plusSeconds(i));
        var news=new LauncherNews(db).list(); assertThat(news).hasSize(50);
        var id=post("DRAFT","Не отправлять",null);new LauncherNews(db).list();
        assertThat(db.queryForObject("SELECT status FROM broadcast_post WHERE id=?",String.class,id)).isEqualTo("DRAFT");
    }
}
