package com.example.examplemod.client;
import java.nio.file.*;
import java.io.IOException;
/** Separate one-time migration, never an updater replacement of options.txt. */
public final class OneTimeClientDefaults {
 private OneTimeClientDefaults(){}
 public static boolean apply(Path root,Runnable patch)throws IOException{
  Path config=root.resolve("config"),marker=config.resolve("ccec-client-defaults-v1.done");
  if(Files.isRegularFile(marker))return false;Files.createDirectories(config);
  Path options=root.resolve("options.txt"),backup=config.resolve("ccec-options-before-defaults-v1.txt");
  if(Files.isRegularFile(options)&&!Files.exists(backup))Files.copy(options,backup);
  patch.run();Files.writeString(marker,"1\n",StandardOpenOption.CREATE_NEW);return true;
 }
}
