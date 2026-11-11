package com.example.examplemod.client.map;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;
public final class MinimapPreferences {
 public boolean enabled=true,cities=true,projects=true;
 public int size=124,corner=0;public double zoom=.5;public int opacity=85;
 private final Path file;
 public MinimapPreferences(Path file){this.file=file;try(var in=Files.newInputStream(file)){Properties p=new Properties();p.load(in);enabled=Boolean.parseBoolean(p.getProperty("enabled","true"));cities=Boolean.parseBoolean(p.getProperty("cities","true"));projects=Boolean.parseBoolean(p.getProperty("projects","true"));size=Math.clamp(Integer.parseInt(p.getProperty("size","124")),96,160);corner=Math.clamp(Integer.parseInt(p.getProperty("corner","0")),0,1);zoom=Math.clamp(Double.parseDouble(p.getProperty("zoom","0.5")),.125,2);if(!Double.isFinite(zoom))zoom=.5;opacity=Math.clamp(Integer.parseInt(p.getProperty("opacity","85")),40,100);}catch(IOException|IllegalArgumentException ignored){}}
 public void save(){Properties p=new Properties();p.setProperty("enabled",""+enabled);p.setProperty("cities",""+cities);p.setProperty("projects",""+projects);p.setProperty("size",""+size);p.setProperty("corner",""+corner);p.setProperty("zoom",""+zoom);p.setProperty("opacity",""+opacity);try{Files.createDirectories(file.getParent());try(var out=Files.newOutputStream(file)){p.store(out,"CCEC minimap");}}catch(IOException ignored){}}
}
