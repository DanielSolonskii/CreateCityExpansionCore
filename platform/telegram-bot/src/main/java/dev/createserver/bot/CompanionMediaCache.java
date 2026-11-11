package dev.createserver.bot;
import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.io.IOException;import java.util.*;
final class CompanionMediaCache {
    static void refresh(String directory,BackendClient backend,TelegramClient telegram)throws InterruptedException{
        if(directory==null||directory.isBlank())return;
        try{
            Path root=Path.of(directory).toAbsolutePath();if(!Files.isDirectory(root)||Files.isSymbolicLink(root))throw new IOException("Invalid media directory");
            var rows=backend.deviceRequest("/api/internal/companion/media",null);if(!rows.isArray()||rows.size()>50)throw new IOException("Invalid media list");
            for(var row:rows){UUID id=UUID.fromString(row.path("id").asText());Path file=root.resolve(id+".img");if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                byte[] image=telegram.photo(row.path("fileId").asText());Path temp=Files.createTempFile(root,"photo-",".next");
                try{Files.write(temp,image);if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-r-----"));Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}break;
            }
        }catch(IOException|IllegalArgumentException unavailable){System.err.println("Companion media cache deferred.");}
    }
}
