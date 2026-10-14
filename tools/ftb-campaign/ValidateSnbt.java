import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Uses the installed FTB Library parser, not a substitute JSON/SNBT parser. */
public class ValidateSnbt {
    public static void main(String[] args) throws Exception {
        Class<?> snbt = Class.forName("dev.ftb.mods.ftblibrary.snbt.SNBT");
        Method parse = snbt.getMethod("readLines", List.class);
        int count = 0;
        try (var paths = Files.walk(Path.of(args[0]))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".snbt")).sorted().toList()) {
                Object tag = parse.invoke(null, Files.readAllLines(path));
                if (tag == null) throw new IllegalStateException("Parser returned null: " + path);
                System.out.println("PARSED " + path.getFileName());
                count++;
            }
        }
        System.out.println("FTB_SNBT_OK files=" + count);
    }
}
