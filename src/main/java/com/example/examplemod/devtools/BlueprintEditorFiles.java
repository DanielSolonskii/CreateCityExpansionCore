package com.example.examplemod.devtools;

import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class BlueprintEditorFiles {
    private static final Path SOURCE = Path.of("src/main/resources").resolve(MineBlueprints.RESOURCE_DIRECTORY);

    private BlueprintEditorFiles() {}

    public record Project(Path root, Map<Tier, String> sources, MineBlueprints.Layout layout) {
        public Project {
            sources = Map.copyOf(sources);
        }
    }

    public static String relativeFile(Tier tier) {
        return SOURCE.resolve(MineBlueprints.fileName(tier)).toString();
    }

    public static Project open(Path selected) throws Exception {
        Path root = RecipeWorkbenchFiles.root(selected);
        Map<Tier, String> sources = new EnumMap<>(Tier.class);
        Map<Tier, Map<BlockPos, BlockState>> tiers = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            Path file = target(root, tier);
            String text = Files.readString(file);
            sources.put(tier, text);
            tiers.put(tier, MineBlueprints.decode(text));
        }
        return new Project(root, sources, new MineBlueprints.Layout(tiers));
    }

    public static Project replace(Project project, Tier tier, BlueprintEditorWorld.Capture capture) throws Exception {
        Project disk = open(project.root());
        if (!disk.sources().equals(project.sources())) {
            throw new IOException("Project blueprints changed on disk. Select the project folder again.");
        }
        MineBlueprints.Layout updated = BlueprintEditorWorld.merge(disk.layout(), tier, capture);
        String text = MineBlueprints.encode(updated.blocks(tier));
        if (MineBlueprints.decode(text).equals(updated.blocks(tier)) == false) {
            throw new IOException("The saved blueprint does not round-trip");
        }

        Path file = target(project.root(), tier);
        Path backupDir = project.root().resolve("build/blueprint-editor-backups");
        requireSafePath(project.root(), backupDir);
        Files.createDirectories(backupDir);
        Path backup = backupDir.resolve(UUID.randomUUID() + "-" + file.getFileName());
        Files.copy(file, backup);

        Path temp = Files.createTempFile(file.getParent(), ".mine-blueprint-", ".tmp");
        try {
            Files.writeString(temp, text);
            if (!Files.readString(file).equals(disk.sources().get(tier))) {
                throw new IOException("The target changed before the write. Select the project folder again.");
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }

        Map<Tier, String> sources = new EnumMap<>(disk.sources());
        sources.put(tier, text);
        MineBlueprints.setLocalOverride(updated);
        return new Project(project.root(), sources, updated);
    }

    private static Path target(Path root, Tier tier) throws IOException {
        Path file = root.resolve(relativeFile(tier));
        requireSafePath(root, file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("This project is missing " + relativeFile(tier));
        }
        return file;
    }

    private static void requireSafePath(Path root, Path target) throws IOException {
        for (Path entry = target; !entry.equals(root); entry = entry.getParent()) {
            if (Files.isSymbolicLink(entry) || (Files.exists(entry, LinkOption.NOFOLLOW_LINKS)
                    && !entry.toRealPath().equals(entry))) {
                throw new IOException("Blueprint paths must stay inside the project: " + entry);
            }
        }
    }
}
