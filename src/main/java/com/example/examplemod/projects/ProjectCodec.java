package com.example.examplemod.projects;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.example.examplemod.projects.Project.*;

/** Additive economy subsection. Corrupt funded records fail closed rather than silently losing CR. */
public final class ProjectCodec {
    private ProjectCodec() {}
    public static CompoundTag save(List<Project> projects) {
        CompoundTag root=new CompoundTag();root.putInt("Version",2);ListTag list=new ListTag();
        for(Project p:projects)list.add(write(p));root.put("Projects",list);return root;
    }
    public static List<Project> load(CompoundTag root) {
        if(root.isEmpty())return List.of();
        if(root.getInt("Version")!=1&&root.getInt("Version")!=2)throw new IllegalStateException("Unsupported Projects format");
        List<Project> result=new ArrayList<>();for(Tag raw:root.getList("Projects",Tag.TAG_COMPOUND))result.add(read((CompoundTag)raw));
        return List.copyOf(result);
    }
    public static CompoundTag write(Project p) {
        CompoundTag t=new CompoundTag();t.putUUID("Id",p.id());t.putUUID("Request",p.creationRequest());t.putString("Scope",p.scope().name());
        if(p.city()!=null)t.putUUID("City",p.city());t.putUUID("Author",p.author());t.putString("Title",p.title());t.putString("Description",p.description());
        t.putString("Category",p.category().name());t.putString("Funding",p.funding().name());t.putString("Status",p.status().name());
        t.putLong("Reward",p.reward());t.putLong("Reserved",p.reserved());t.putBoolean("Paid",p.paid());t.putLong("Revision",p.revision());t.putString("Note",p.reviewNote());
        var z=p.zone();CompoundTag zone=new CompoundTag();zone.putString("Dimension",z.dimension().toString());zone.putString("Shape",z.shape().name());
        zone.putInt("X1",z.x1());zone.putInt("Z1",z.z1());zone.putInt("X2",z.x2());zone.putInt("Z2",z.z2());t.put("Zone",zone);
        ListTag stages=new ListTag();for(Stage stage:p.stages()){CompoundTag s=new CompoundTag();s.putString("Title",stage.title());s.putBoolean("Done",stage.done());stages.add(s);}t.put("Stages",stages);
        ListTag applicants=new ListTag();for(Applicant a:p.applicants())applicants.add(writeApplicant(a));t.put("Applicants",applicants);
        if(p.executor()!=null)t.put("Executor",writeApplicant(p.executor()));return t;
    }
    private static CompoundTag writeApplicant(Applicant a) {CompoundTag t=new CompoundTag();t.putString("Kind",a.kind().name());t.putUUID("Id",a.id());t.putUUID("Representative",a.representative());return t;}
    private static Applicant readApplicant(CompoundTag t){return new Applicant(ApplicantKind.valueOf(t.getString("Kind")),t.getUUID("Id"),t.getUUID("Representative"));}
    public static Project read(CompoundTag t) {
        CompoundTag z=t.getCompound("Zone");ProjectZone zone=new ProjectZone(ResourceLocation.parse(z.getString("Dimension")),ProjectZone.Shape.valueOf(z.getString("Shape")),z.getInt("X1"),z.getInt("Z1"),z.getInt("X2"),z.getInt("Z2"));
        List<Stage> stages=new ArrayList<>();for(Tag raw:t.getList("Stages",Tag.TAG_COMPOUND)){CompoundTag s=(CompoundTag)raw;stages.add(new Stage(s.getString("Title"),s.getBoolean("Done")));}
        List<Applicant> applicants=new ArrayList<>();for(Tag raw:t.getList("Applicants",Tag.TAG_COMPOUND))applicants.add(readApplicant((CompoundTag)raw));
        return new Project(t.getUUID("Id"),t.getUUID("Request"),Scope.valueOf(t.getString("Scope")),t.hasUUID("City")?t.getUUID("City"):null,t.getUUID("Author"),
                t.getString("Title"),t.getString("Description"),Category.valueOf(t.getString("Category")),zone,stages,Funding.valueOf(t.getString("Funding")),t.getLong("Reward"),Status.valueOf(t.getString("Status")),
                applicants,t.contains("Executor")?readApplicant(t.getCompound("Executor")):null,t.getLong("Reserved"),t.getBoolean("Paid"),t.getLong("Revision"),t.getString("Note"));
    }
}
