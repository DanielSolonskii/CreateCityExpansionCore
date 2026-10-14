package com.example.examplemod.projects;

import java.util.*;

/** Shared CITY/SERVER model and settlement invariants. */
public record Project(UUID id, UUID creationRequest, Scope scope, UUID city, UUID author,
                      String title, String description, Category category, ProjectZone zone,
                      List<Stage> stages, Funding funding, long reward, Status status,
                      List<Applicant> applicants, Applicant executor, long reserved, boolean paid,
                      long revision, String reviewNote) {
    public enum Scope { CITY, SERVER }
    public enum Funding { FREE, PERSONAL, TREASURY, SERVER_ISSUANCE }
    public enum Category { BUILDING, ROAD, RAILWAY, LANDSCAPE, INFRASTRUCTURE, OTHER }
    public enum Status { DRAFT, PENDING_FUNDING, OPEN, BUILDING, REVIEW, REWORK, COMPLETED, CANCELLED;
        public boolean terminal(){return this==COMPLETED||this==CANCELLED;}
    }
    public enum ApplicantKind { PLAYER, CITY }
    public record Applicant(ApplicantKind kind, UUID id, UUID representative) {
        public Applicant { Objects.requireNonNull(kind);Objects.requireNonNull(id);Objects.requireNonNull(representative); }
        public static Applicant player(UUID id){return new Applicant(ApplicantKind.PLAYER,id,id);}
    }
    public record Stage(String title, boolean done) {
        public Stage { if(title==null||title.isBlank()||title.length()>128)throw new IllegalArgumentException("Invalid stage"); }
    }
    public Project {
        Objects.requireNonNull(id);Objects.requireNonNull(creationRequest);Objects.requireNonNull(scope);
        Objects.requireNonNull(author);Objects.requireNonNull(category);Objects.requireNonNull(zone);
        Objects.requireNonNull(funding);Objects.requireNonNull(status);
        if(scope==Scope.CITY&&city==null || title==null||title.isBlank()||title.length()>96
                || description==null||description.length()>2048 || stages==null||stages.isEmpty()||stages.size()>16
                || reward<0||reserved<0||revision<0||reviewNote==null||reviewNote.length()>512)
            throw new IllegalArgumentException("Invalid project");
        stages=List.copyOf(stages);applicants=List.copyOf(applicants);
        if(applicants.size()>128||new HashSet<>(applicants).size()!=applicants.size())throw new IllegalArgumentException("Invalid applicants");
        if(funding==Funding.FREE&&reward!=0 || (funding==Funding.PERSONAL||funding==Funding.TREASURY)&&reward==0)throw new IllegalArgumentException("Invalid funding");
        if(scope==Scope.CITY&&funding==Funding.SERVER_ISSUANCE)throw new IllegalArgumentException("CITY cannot mint CR");
        if(scope==Scope.SERVER&&(city!=null||funding!=Funding.SERVER_ISSUANCE))throw new IllegalArgumentException("Invalid SERVER funding");
        if(reserved!=0&&reserved!=reward || status.terminal()&&reserved!=0 || paid&&status!=Status.COMPLETED)
            throw new IllegalArgumentException("Invalid settlement");
        {
            boolean funded=status==Status.OPEN||status==Status.BUILDING||status==Status.REVIEW||status==Status.REWORK;
            boolean assigned=status==Status.BUILDING||status==Status.REVIEW||status==Status.REWORK||status==Status.COMPLETED;
            if(funded&&reserved!=reward || !funded&&reserved!=0 || assigned&&executor==null
                    || (status==Status.DRAFT||status==Status.PENDING_FUNDING||status==Status.OPEN)&&executor!=null
                    || (status==Status.COMPLETED)!=paid || status==Status.PENDING_FUNDING&&funding!=Funding.TREASURY
                    || status==Status.COMPLETED&&stages.stream().anyMatch(s->!s.done())
                    || applicants.stream().anyMatch(a->scope==Scope.CITY&&a.kind()!=ApplicantKind.PLAYER||a.kind()==ApplicantKind.PLAYER&&!a.id().equals(a.representative()))
                    || executor!=null&&!applicants.contains(executor))throw new IllegalArgumentException("Inconsistent project");
        }
    }
}
