package com.example.examplemod.profile;

import com.example.examplemod.projects.Project;
import java.util.*;
import static com.example.examplemod.profile.PlayerProfile.ProjectEntry.*;

/** Uses only facts retained in the existing project save; never counts applications or city membership. */
public final class ProfileHistory {
    private ProfileHistory() {}
    public static List<PlayerProfile.ProjectEntry> completed(Collection<Project> projects, UUID player) {
        List<PlayerProfile.ProjectEntry> result = new ArrayList<>();
        for (Project project : projects) {
            if (project.status() != Project.Status.COMPLETED) continue;
            int roles = project.author().equals(player) ? AUTHOR : 0;
            var executor = project.executor();
            if (executor != null && executor.kind() == Project.ApplicantKind.PLAYER && executor.id().equals(player)) roles |= EXECUTOR;
            if (executor != null && executor.kind() == Project.ApplicantKind.CITY && executor.representative().equals(player)) roles |= REPRESENTATIVE;
            if (roles != 0) result.add(new PlayerProfile.ProjectEntry(project.id(), project.title(), project.scope() == Project.Scope.SERVER, roles));
        }
        result.sort(Comparator.comparing(PlayerProfile.ProjectEntry::title, String.CASE_INSENSITIVE_ORDER).thenComparing(PlayerProfile.ProjectEntry::id));
        return List.copyOf(result);
    }
}
