package dev.createserver.backend.miniapp;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class CompanionMediaTest {
    @TempDir Path root;
    @Test void servesOnlyApprovedRasterWithoutTokenOrFilePaths()throws Exception{
        var db=mock(JdbcTemplate.class);UUID id=UUID.randomUUID();when(db.queryForObject(anyString(),eq(Integer.class),eq(id))).thenReturn(1);
        var media=new CompanionMedia(db,root.toString());Files.write(root.resolve(id+".img"),new byte[]{(byte)255,(byte)216,(byte)255,0});
        assertThat(media.photo(id).getStatusCode().value()).isEqualTo(200);assertThat(media.photo(id).getHeaders().getFirst("Content-Type")).isEqualTo("image/jpeg");
        when(db.queryForObject(anyString(),eq(Integer.class),eq(id))).thenReturn(0);assertThat(media.photo(id).getStatusCode().value()).isEqualTo(404);
        when(db.queryForObject(anyString(),eq(Integer.class),eq(id))).thenReturn(1);Files.writeString(root.resolve(id+".img"),"<svg onload='bad'/>");assertThat(media.photo(id).getStatusCode().value()).isEqualTo(404);
    }
}
