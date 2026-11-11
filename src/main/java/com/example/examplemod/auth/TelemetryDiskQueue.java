package com.example.examplemod.auth;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Worker-thread only immutable batches. Bounded disk use; no credentials in payloads. */
final class TelemetryDiskQueue {
 private final Path directory;
 TelemetryDiskQueue(Path directory){this.directory=directory;}
 record Head(Path file,JsonObject body){}
 private List<Path> files()throws Exception{Files.createDirectories(directory);try(var stream=Files.list(directory)){return stream.filter(p->p.getFileName().toString().matches("[0-9]{20}-[0-9]{20}-[a-f0-9-]{36}\\.json")&&!Files.isSymbolicLink(p)).sorted().limit(257).toList();}}
 boolean put(Map<String,Object> value,long start,long revision)throws Exception{
  var files=files();if(files.size()>=256)return false;
  String json=new Gson().toJson(value);if(json.length()>131072)throw new IllegalStateException("Telemetry batch too large");
  Path file=directory.resolve(String.format(java.util.Locale.ROOT,"%020d-%020d-%s.json",start,revision,value.get("id"))),temp=directory.resolve(file.getFileName()+".tmp");
  Files.writeString(temp,json);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}return true;
 }
 Head first()throws Exception{
  for(Path file:files()){
   try{if(Files.size(file)>131072)throw new IllegalStateException();var body=JsonParser.parseString(Files.readString(file)).getAsJsonObject();UUID.fromString(body.get("id").getAsString());return new Head(file,body);}
   catch(Exception invalid){Files.move(file,file.resolveSibling(file.getFileName()+".invalid"),StandardCopyOption.REPLACE_EXISTING);}
  }return null;
 }
 void remove(Head head)throws Exception{Files.delete(head.file());}
}
