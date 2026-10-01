package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class City {

    private final UUID id;
    private final String name;
    private final UUID owner;
    private BlockPos pylonPos;
    private ResourceLocation pylonDimension;
    private boolean pylonActive;

    private int level;
    private int tradeReputation;

    private final List<CityMember> members;
    private final List<CityInvite> invites;
    private final List<CityProject> projects;
    private final Map<ResourceLocation, Long> developmentContributions;
    private ResourceLocation developmentPrimaryResource;

    /*
     * =========================================
     * ÃÂ¡ÃÅ¾Ãâ€”Ãâ€ÃÂÃÂÃËœÃâ€¢ Ãâ€œÃÅ¾ÃÂ ÃÅ¾Ãâ€ÃÂ
     * =========================================
     */

    public City(
            UUID id,
            String name,
            UUID owner,
            BlockPos pylonPos
    ) {
        this(
                id,
                name,
                owner,
                pylonPos,
                1,
                new ArrayList<>(),
                true,
                new ArrayList<>(),
                null,
                new HashMap<>()
        );
    }

    public City(
            UUID id,
            String name,
            UUID owner,
            BlockPos pylonPos,
            int level
    ) {
        this(
                id,
                name,
                owner,
                pylonPos,
                level,
                new ArrayList<>(),
                true,
                new ArrayList<>(),
                null,
                new HashMap<>()
        );
    }

    /*
     * =========================================
     * ÃÅ¾ÃÂ¡ÃÂÃÅ¾Ãâ€™ÃÂÃÅ¾Ãâ„¢ ÃÅ¡ÃÅ¾ÃÂÃÂ¡ÃÂ¢ÃÂ ÃÂ£ÃÅ¡ÃÂ¢ÃÅ¾ÃÂ 
     * =========================================
     */

    public City(
            UUID id,
            String name,
            UUID owner,
            BlockPos pylonPos,
            int level,
            List<CityMember> members
    ) {
        this(id, name, owner, pylonPos, level, members, true,
                new ArrayList<>(), null, new HashMap<>());
    }

    public City(
            UUID id,
            String name,
            UUID owner,
            BlockPos pylonPos,
            int level,
            List<CityMember> members,
            boolean pylonActive,
            List<CityProject> projects,
            ResourceLocation developmentPrimaryResource,
            Map<ResourceLocation, Long> developmentContributions
    ) {
        this(id, name, owner, pylonPos, level, members, pylonActive, projects,
                developmentPrimaryResource, developmentContributions,
                net.minecraft.world.level.Level.OVERWORLD.location());
    }

    public City(
            UUID id,
            String name,
            UUID owner,
            BlockPos pylonPos,
            int level,
            List<CityMember> members,
            boolean pylonActive,
            List<CityProject> projects,
            ResourceLocation developmentPrimaryResource,
            Map<ResourceLocation, Long> developmentContributions,
            ResourceLocation pylonDimension
    ) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.pylonPos = pylonPos.immutable();
        this.pylonDimension = java.util.Objects.requireNonNull(pylonDimension, "pylonDimension");

        this.level = Math.max(1, Math.min(level, 2));
        this.pylonActive = pylonActive;

        this.members = new ArrayList<>(members);

        /*
         * ÃÅ¸Ã‘â‚¬ÃÂ¸ Ã‘ÂÃÂ¾ÃÂ·ÃÂ´ÃÂ°ÃÂ½ÃÂ¸ÃÂ¸ ÃÂ³ÃÂ¾Ã‘â‚¬ÃÂ¾ÃÂ´ÃÂ° Ã‘ÂÃÂ¿ÃÂ¸Ã‘ÂÃÂ¾ÃÂº ÃÂ¿Ã‘â‚¬ÃÂ¸ÃÂ³ÃÂ»ÃÂ°Ã‘Ë†ÃÂµÃÂ½ÃÂ¸ÃÂ¹
         * ÃÂ¿ÃÂ¾ÃÂºÃÂ° ÃÂ¿Ã‘Æ’Ã‘ÂÃ‘â€šÃÂ¾ÃÂ¹.
         */
        this.invites = new ArrayList<>();
        this.projects = new ArrayList<>(projects);
        this.developmentPrimaryResource = developmentPrimaryResource;
        this.developmentContributions = new HashMap<>();
        developmentContributions.forEach((item, amount) -> {
            if (item != null && amount != null && amount > 0L) {
                this.developmentContributions.put(item, amount);
            }
        });

        /*
         * Ãâ€™ÃÂ»ÃÂ°ÃÂ´ÃÂµÃÂ»ÃÂµÃ‘â€  ÃÂ²Ã‘ÂÃÂµÃÂ³ÃÂ´ÃÂ° ÃÂ´ÃÂ¾ÃÂ»ÃÂ¶ÃÂµÃÂ½ ÃÂ±Ã‘â€¹Ã‘â€šÃ‘Å’
         * Ã‘Æ’Ã‘â€¡ÃÂ°Ã‘ÂÃ‘â€šÃÂ½ÃÂ¸ÃÂºÃÂ¾ÃÂ¼ ÃÂ³ÃÂ¾Ã‘â‚¬ÃÂ¾ÃÂ´ÃÂ°.
         */
        if (!hasMember(owner)) {

            this.members.add(
                    new CityMember(
                            owner,
                            CityMember.Role.OWNER
                    )
            );
        }
    }

    /*
     * =========================================
     * ÃÅ¾ÃÂ¡ÃÂÃÅ¾Ãâ€™ÃÂÃÂÃÂ¯ ÃËœÃÂÃÂ¤ÃÅ¾ÃÂ ÃÅ“ÃÂÃÂ¦ÃËœÃÂ¯
     * =========================================
     */

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public BlockPos getPylonPos() {
        return pylonPos;
    }

    public ResourceLocation getPylonDimension() {
        return pylonDimension;
    }

    void rebindPylon(ResourceLocation dimension, BlockPos pos) {
        this.pylonDimension = java.util.Objects.requireNonNull(dimension, "dimension");
        this.pylonPos = java.util.Objects.requireNonNull(pos, "pos").immutable();
        this.pylonActive = true;
    }

    /*
     * =========================================
     * ÃÂ£ÃÂ ÃÅ¾Ãâ€™Ãâ€¢ÃÂÃÂ¬ Ãâ€œÃÅ¾ÃÂ ÃÅ¾Ãâ€ÃÂ
     * =========================================
     */

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {

        this.level = Math.max(1, Math.min(level, 2));
    }

    public int getTradeReputation() { return tradeReputation; }

    public void adjustTradeReputation(int delta) {
        tradeReputation = Math.max(0, Math.min(10_000, tradeReputation + delta));
    }

    void restoreTradeReputation(int value) {
        tradeReputation = Math.max(0, Math.min(10_000, value));
    }

    /*
     * =========================================
     * ÃÂ ÃÂÃâ€”ÃÅ“Ãâ€¢ÃÂ  ÃÂ¢Ãâ€¢ÃÂ ÃÂ ÃËœÃÂ¢ÃÅ¾ÃÂ ÃËœÃËœ
     * =========================================
     */

    public int getTerritorySize() {

        return switch (level) {

            case 2 -> 150;
            default -> 100;
        };
    }

    public int getProjectCapacity() {
        return level >= 2 ? 2 : 1;
    }

    public boolean isPylonActive() {
        return pylonActive;
    }

    public void setPylonActive(boolean active) {
        this.pylonActive = active;
    }

    public List<CityProject> getProjects() {
        return projects;
    }

    public CityProject getProject(UUID projectId) {
        return projects.stream()
                .filter(project -> project.projectId().equals(projectId))
                .findFirst()
                .orElse(null);
    }

    public boolean addProject(CityProject project) {
        if (!project.cityId().equals(id)
                || projects.size() >= getProjectCapacity()
                || getProject(project.projectId()) != null) {
            return false;
        }
        projects.add(project);
        if (developmentPrimaryResource == null) {
            developmentPrimaryResource = project.resourceId();
        }
        return true;
    }

    public CityProject removeProject(UUID projectId) {
        CityProject project = getProject(projectId);
        if (project != null) {
            projects.remove(project);
        }
        return project;
    }

    boolean replaceProject(CityProject replacement) {
        for (int index = 0; index < projects.size(); index++) {
            if (projects.get(index).projectId().equals(replacement.projectId())) {
                projects.set(index, replacement);
                return true;
            }
        }
        return false;
    }

    public ResourceLocation getDevelopmentPrimaryResource() {
        return developmentPrimaryResource;
    }

    void restoreDevelopmentPrimaryResource(ResourceLocation resourceId) {
        if (resourceId != null) {
            this.developmentPrimaryResource = resourceId;
        }
    }

    public Map<ResourceLocation, Long> getDevelopmentContributions() {
        return Map.copyOf(developmentContributions);
    }

    public long getDevelopmentContribution(ResourceLocation itemId) {
        return developmentContributions.getOrDefault(itemId, 0L);
    }

    public long contribute(ResourceLocation itemId, long requested, long required) {
        if (requested <= 0L || required <= 0L) {
            return 0L;
        }
        long current = getDevelopmentContribution(itemId);
        long accepted = Math.min(requested, Math.max(0L, required - current));
        if (accepted > 0L) {
            developmentContributions.put(itemId, current + accepted);
        }
        return accepted;
    }

    public boolean canCompleteLevelTwo() {
        if (level != 1 || developmentPrimaryResource == null
                || projects.stream().noneMatch(project ->
                project.projectType() == CityProjectType.INDUSTRIAL_MINE
                        && project.resourceId().equals(developmentPrimaryResource))) {
            return false;
        }
        List<CityDevelopmentRequirements.Requirement> requirements =
                CityDevelopmentRequirements.forLevelTwo(developmentPrimaryResource);
        return !requirements.isEmpty() && requirements.stream().allMatch(
                requirement -> getDevelopmentContribution(requirement.itemId()) >= requirement.amount()
        );
    }

    /*
     * =========================================
     * ÃÂ£ÃÂ§ÃÂÃÂ¡ÃÂ¢ÃÂÃËœÃÅ¡ÃËœ
     * =========================================
     */

    public List<CityMember> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean hasMember(UUID player) {

        return members.stream()
                .anyMatch(member ->
                        member.getPlayerId().equals(player)
                );
    }

    public CityMember getMember(UUID player) {

        return members.stream()
                .filter(member ->
                        member.getPlayerId().equals(player)
                )
                .findFirst()
                .orElse(null);
    }

    /*
     * =========================================
     * Ãâ€ÃÅ¾Ãâ€˜ÃÂÃâ€™Ãâ€ºÃâ€¢ÃÂÃËœÃâ€¢ ÃÂ£ÃÂ§ÃÂÃÂ¡ÃÂ¢ÃÂÃËœÃÅ¡ÃÂ
     * =========================================
     */

    public void addMember(UUID player) {

        if (!hasMember(player)) {

            members.add(
                    new CityMember(
                            player,
                            CityMember.Role.RESIDENT
                    )
            );
        }

        /*
         * Ãâ€¢Ã‘ÂÃÂ»ÃÂ¸ Ã‘Æ’ ÃÂ¸ÃÂ³Ã‘â‚¬ÃÂ¾ÃÂºÃÂ° ÃÂ±Ã‘â€¹ÃÂ»ÃÂ¾ ÃÂ¿Ã‘â‚¬ÃÂ¸ÃÂ³ÃÂ»ÃÂ°Ã‘Ë†ÃÂµÃÂ½ÃÂ¸ÃÂµ,
         * ÃÂ¾ÃÂ½ÃÂ¾ ÃÂ±ÃÂ¾ÃÂ»Ã‘Å’Ã‘Ë†ÃÂµ ÃÂ½ÃÂµ ÃÂ½Ã‘Æ’ÃÂ¶ÃÂ½ÃÂ¾.
         */
        removeInvite(player);
    }

    public void addMember(
            UUID player,
            CityMember.Role role
    ) {

        if (!hasMember(player)) {

            /*
             * ÃÂÃÂµÃÂ»Ã‘Å’ÃÂ·Ã‘Â ÃÂ½ÃÂ°ÃÂ·ÃÂ½ÃÂ°Ã‘â€¡ÃÂ¸Ã‘â€šÃ‘Å’ OWNER Ã‘â€¡ÃÂµÃ‘â‚¬ÃÂµÃÂ·
             * ÃÂ¾ÃÂ±Ã‘â€¹Ã‘â€¡ÃÂ½ÃÂ¾ÃÂµ ÃÂ´ÃÂ¾ÃÂ±ÃÂ°ÃÂ²ÃÂ»ÃÂµÃÂ½ÃÂ¸ÃÂµ Ã‘Æ’Ã‘â€¡ÃÂ°Ã‘ÂÃ‘â€šÃÂ½ÃÂ¸ÃÂºÃÂ°.
             *
             * ÃÂÃÂ°Ã‘ÂÃ‘â€šÃÂ¾Ã‘ÂÃ‘â€°ÃÂ¸ÃÂ¹ ÃÂ²ÃÂ»ÃÂ°ÃÂ´ÃÂµÃÂ»ÃÂµÃ‘â€  ÃÂ¾ÃÂ¿Ã‘â‚¬ÃÂµÃÂ´ÃÂµÃÂ»Ã‘ÂÃÂµÃ‘â€šÃ‘ÂÃ‘Â
             * ÃÂ¿ÃÂ¾ÃÂ»ÃÂµÃÂ¼ owner.
             */
            if (role == CityMember.Role.OWNER
                    && !player.equals(owner)) {

                role = CityMember.Role.RESIDENT;
            }

            members.add(
                    new CityMember(
                            player,
                            role
                    )
            );
        }

        removeInvite(player);
    }

    /*
     * =========================================
     * ÃÂ£Ãâ€ÃÂÃâ€ºÃâ€¢ÃÂÃËœÃâ€¢ ÃÂ£ÃÂ§ÃÂÃÂ¡ÃÂ¢ÃÂÃËœÃÅ¡ÃÂ
     * =========================================
     */

    public void removeMember(UUID player) {

        /*
         * Ãâ€™ÃÂ»ÃÂ°ÃÂ´ÃÂµÃÂ»ÃÂµÃ‘â€  ÃÂ½ÃÂµ ÃÂ¼ÃÂ¾ÃÂ¶ÃÂµÃ‘â€š ÃÂ±Ã‘â€¹Ã‘â€šÃ‘Å’ Ã‘Æ’ÃÂ´ÃÂ°ÃÂ»Ã‘â€˜ÃÂ½
         * ÃÂ¸ÃÂ· Ã‘ÂÃÂ¾ÃÂ±Ã‘ÂÃ‘â€šÃÂ²ÃÂµÃÂ½ÃÂ½ÃÂ¾ÃÂ³ÃÂ¾ ÃÂ³ÃÂ¾Ã‘â‚¬ÃÂ¾ÃÂ´ÃÂ°.
         */
        if (player.equals(owner)) {
            return;
        }

        members.removeIf(
                member ->
                        member.getPlayerId().equals(player)
        );
    }

    /*
     * =========================================
     * ÃËœÃâ€”ÃÅ“Ãâ€¢ÃÂÃâ€¢ÃÂÃËœÃâ€¢ ÃÂ ÃÅ¾Ãâ€ºÃËœ
     * =========================================
     */

    public void setMemberRole(
            UUID player,
            CityMember.Role role
    ) {

        /*
         * Ãâ€™ÃÂ»ÃÂ°ÃÂ´ÃÂµÃÂ»Ã‘Å’Ã‘â€ ÃÂ° ÃÂ½ÃÂµÃÂ»Ã‘Å’ÃÂ·Ã‘Â ÃÂ¿Ã‘â‚¬ÃÂµÃÂ²Ã‘â‚¬ÃÂ°Ã‘â€šÃÂ¸Ã‘â€šÃ‘Å’
         * ÃÂ² ÃÂ´Ã‘â‚¬Ã‘Æ’ÃÂ³Ã‘Æ’Ã‘Å½ Ã‘â‚¬ÃÂ¾ÃÂ»Ã‘Å’.
         */
        if (player.equals(owner)) {
            return;
        }

        /*
         * OWNER ÃÂ½ÃÂµÃÂ»Ã‘Å’ÃÂ·Ã‘Â ÃÂ½ÃÂ°ÃÂ·ÃÂ½ÃÂ°Ã‘â€¡ÃÂ¸Ã‘â€šÃ‘Å’ Ã‘â€¡ÃÂµÃ‘â‚¬ÃÂµÃÂ·
         * Ã‘Æ’ÃÂ¿Ã‘â‚¬ÃÂ°ÃÂ²ÃÂ»ÃÂµÃÂ½ÃÂ¸ÃÂµ Ã‘Æ’Ã‘â€¡ÃÂ°Ã‘ÂÃ‘â€šÃÂ½ÃÂ¸ÃÂºÃÂ°ÃÂ¼ÃÂ¸.
         */
        if (role == CityMember.Role.OWNER) {
            return;
        }

        CityMember member =
                getMember(player);

        if (member != null) {
            member.setRole(role);
        }
    }

    /*
     * =========================================
     * ÃÅ¸ÃÂ ÃÅ¾Ãâ€™Ãâ€¢ÃÂ ÃÅ¡ÃÂ Ãâ€™Ãâ€ºÃÂÃâ€Ãâ€¢Ãâ€ºÃÂ¬ÃÂ¦ÃÂ
     * =========================================
     */

    public boolean isOwner(UUID player) {
        return player.equals(owner);
    }

    /*
     * =========================================
     * ÃÅ¸ÃÂ ÃÅ¾Ãâ€™Ãâ€¢ÃÂ ÃÅ¡ÃÂ ÃÂÃâ€ÃÅ“ÃËœÃÂÃËœÃÂ¡ÃÂ¢ÃÂ ÃÂÃÂ¢ÃÅ¾ÃÂ ÃÂ
     * =========================================
     */

    public boolean isAdmin(UUID player) {

        CityMember member =
                getMember(player);

        return member != null
                && (
                member.isOwner()
                        || member.isAdmin()
        );
    }

    /*
     * =========================================
     * ÃÅ¸ÃÂ ÃÅ¾Ãâ€™Ãâ€¢ÃÂ ÃÅ¡ÃÂ ÃÅ¸ÃÂ ÃÂÃâ€™ÃÂ ÃÂ¡ÃÂ¢ÃÂ ÃÅ¾ÃËœÃÂ¢ÃÂ¬
     * =========================================
     */

    public boolean canBuild(UUID player) {

        CityMember member =
                getMember(player);

        return member != null
                && (
                member.isOwner()
                        || member.isAdmin()
                        || member.isBuilder()
        );
    }

    /*
     * =========================================
     * ÃÅ¸ÃÂ ÃËœÃâ€œÃâ€ºÃÂÃÂ¨Ãâ€¢ÃÂÃËœÃÂ¯
     * =========================================
     */

    public List<CityInvite> getInvites() {
        return invites;
    }

    public boolean hasInvite(UUID player) {

        return invites.stream()
                .anyMatch(invite ->
                        invite.getPlayerId().equals(player)
                );
    }

    /*
     * =========================================
     * ÃÂ¡ÃÅ¾Ãâ€”Ãâ€ÃÂÃÂ¢ÃÂ¬ ÃÅ¸ÃÂ ÃËœÃâ€œÃâ€ºÃÂÃÂ¨Ãâ€¢ÃÂÃËœÃâ€¢
     * =========================================
     */
    /**
     * Why an invite did or did not go through. The client used to learn nothing at all: every refusal
     * was a silent return, so the inviter got neither feedback nor a reason. This is the
     * server-authoritative answer the UI renders.
     */
    public enum InviteResult {

        SENT,

        ALREADY_INVITED,

        ALREADY_MEMBER,

        IS_OWNER
    }

    /**
     * Creates the invite and reports what actually happened. Idempotent by construction: a second
     * invite for the same player never duplicates anything and reports ALREADY_INVITED instead of
     * pretending to send again.
     */
    public InviteResult addInvite(UUID player) {
        return addInvite(player, null);
    }

    /**
     * Creates the invite on behalf of {@code inviter}. Passing the inviter is what lets the sender be
     * told afterwards that the invite was accepted or declined.
     */
    public InviteResult addInvite(UUID player, UUID inviter) {

        /*
         * Cannot invite the owner.
         */
        if (player.equals(owner)) {
            return InviteResult.IS_OWNER;
        }

        /*
         * Cannot invite somebody who already belongs to the city.
         */
        if (hasMember(player)) {
            return InviteResult.ALREADY_MEMBER;
        }

        /*
         * Cannot create a second identical invite.
         */
        if (hasInvite(player)) {
            return InviteResult.ALREADY_INVITED;
        }

        invites.add(
                new CityInvite(player, inviter)
        );

        return InviteResult.SENT;
    }

    /*
     * =========================================
     * ÃÂ£Ãâ€ÃÂÃâ€ºÃËœÃÂ¢ÃÂ¬ ÃÅ¸ÃÂ ÃËœÃâ€œÃâ€ºÃÂÃÂ¨Ãâ€¢ÃÂÃËœÃâ€¢
     * =========================================
     */

    /** The pending invite for a player, or null. Read before accepting or declining. */
    public CityInvite getInvite(UUID player) {
        return invites.stream()
                .filter(invite ->
                        invite.getPlayerId().equals(player)
                )
                .findFirst()
                .orElse(null);
    }

    public void removeInvite(UUID player) {

        invites.removeIf(
                invite ->
                        invite.getPlayerId().equals(player)
        );
    }

    /*
     * =========================================
     * ÃÅ¸ÃÂ ÃËœÃÂÃÂ¯ÃÂ¢ÃÂ¬ ÃÅ¸ÃÂ ÃËœÃâ€œÃâ€ºÃÂÃÂ¨Ãâ€¢ÃÂÃËœÃâ€¢
     * =========================================
     */

    public void acceptInvite(UUID player) {

        /*
         * ÃÅ¸Ã‘â‚¬ÃÂ¸ÃÂ³ÃÂ»ÃÂ°Ã‘Ë†ÃÂµÃÂ½ÃÂ¸Ã‘Â ÃÂ½ÃÂµÃ‘â€š Ã¢â‚¬â€
         * ÃÂ½ÃÂ¸Ã‘â€¡ÃÂµÃÂ³ÃÂ¾ ÃÂ½ÃÂµ ÃÂ´ÃÂµÃÂ»ÃÂ°ÃÂµÃÂ¼.
         */
        if (!hasInvite(player)) {
            return;
        }

        /*
         * Ãâ€ÃÂ¾ÃÂ±ÃÂ°ÃÂ²ÃÂ»Ã‘ÂÃÂµÃÂ¼ ÃÂ¸ÃÂ³Ã‘â‚¬ÃÂ¾ÃÂºÃÂ° ÃÂ¾ÃÂ±Ã‘â€¹Ã‘â€¡ÃÂ½Ã‘â€¹ÃÂ¼ ÃÂ¶ÃÂ¸Ã‘â€šÃÂµÃÂ»ÃÂµÃÂ¼.
         */
        if (!hasMember(player)) {

            members.add(
                    new CityMember(
                            player,
                            CityMember.Role.RESIDENT
                    )
            );
        }

        /*
         * ÃÅ¸ÃÂ¾Ã‘ÂÃÂ»ÃÂµ ÃÂ¿Ã‘â‚¬ÃÂ¸ÃÂ½Ã‘ÂÃ‘â€šÃÂ¸Ã‘Â ÃÂ¿Ã‘â‚¬ÃÂ¸ÃÂ³ÃÂ»ÃÂ°Ã‘Ë†ÃÂµÃÂ½ÃÂ¸ÃÂµ
         * Ã‘Æ’ÃÂ´ÃÂ°ÃÂ»Ã‘ÂÃÂµÃ‘â€šÃ‘ÂÃ‘Â.
         */
        removeInvite(player);
    }
}
