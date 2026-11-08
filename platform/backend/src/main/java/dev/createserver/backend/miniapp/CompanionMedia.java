package dev.createserver.backend.miniapp;
import java.nio.file.*;import java.io.IOException;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;
@RestController
public class CompanionMedia {
    private final JdbcTemplate db;private final Path root;
    public CompanionMedia(JdbcTemplate db,@Value("${COMPANION_MEDIA_DIR:}")String root){this.db=db;this.root=root.isBlank()?null:Path.of(root).toAbsolutePath().normalize();}
    public record Photo(UUID id,String fileId){}
    @GetMapping("/api/internal/companion/media") public List<Photo> photos(){return db.query("SELECT id,photo_file_id FROM broadcast_post WHERE status IN ('QUEUED','COMPLETED') AND confirmed_at IS NOT NULL AND photo_file_id IS NOT NULL ORDER BY confirmed_at DESC LIMIT 50",(r,n)->new Photo(r.getObject(1,UUID.class),r.getString(2)));}
    @GetMapping("/api/mini-app/media/{id}") public ResponseEntity<byte[]> photo(@PathVariable UUID id)throws IOException{
        if(root==null||db.queryForObject("SELECT COUNT(*) FROM broadcast_post WHERE id=? AND status IN ('QUEUED','COMPLETED') AND confirmed_at IS NOT NULL AND photo_file_id IS NOT NULL",Integer.class,id)==0)return ResponseEntity.notFound().build();
        Path path=root.resolve(id+".img");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>8*1024*1024)return ResponseEntity.notFound().build();
        byte[] bytes=Files.readAllBytes(path);String type=type(bytes);if(type==null)return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(bytes);
    }
    static String type(byte[] b){if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";if(b.length>=8&&b[0]==(byte)137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";return null;}
}
