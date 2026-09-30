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
     * СОЗДАНИЕ ГОРОДА
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
     * ОСНОВНОЙ КОНСТРУКТОР
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
         * При создании города список приглашений
         * пока пустой.
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
         * Владелец всегда должен быть
         * участником города.
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
     * ОСНОВНАЯ ИНФОРМАЦИЯ
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
     * УРОВЕНЬ ГОРОДА
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
     * РАЗМЕР ТЕРРИТОРИИ
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
     * УЧАСТНИКИ
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
     * ДОБАВЛЕНИЕ УЧАСТНИКА
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
         * Если у игрока было приглашение,
         * оно больше не нужно.
         */
        removeInvite(player);
    }

    public void addMember(
            UUID player,
            CityMember.Role role
    ) {

        if (!hasMember(player)) {

            /*
             * Нельзя назначить OWNER через
             * обычное добавление участника.
             *
             * Настоящий владелец определяется
             * полем owner.
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
     * УДАЛЕНИЕ УЧАСТНИКА
     * =========================================
     */

    public void removeMember(UUID player) {

        /*
         * Владелец не может быть удалён
         * из собственного города.
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
     * ИЗМЕНЕНИЕ РОЛИ
     * =========================================
     */

    public void setMemberRole(
            UUID player,
            CityMember.Role role
    ) {

        /*
         * Владельца нельзя превратить
         * в другую роль.
         */
        if (player.equals(owner)) {
            return;
        }

        /*
         * OWNER нельзя назначить через
         * управление участниками.
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
     * ПРОВЕРКА ВЛАДЕЛЬЦА
     * =========================================
     */

    public boolean isOwner(UUID player) {
        return player.equals(owner);
    }

    /*
     * =========================================
     * ПРОВЕРКА АДМИНИСТРАТОРА
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
     * ПРОВЕРКА ПРАВА СТРОИТЬ
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
     * ПРИГЛАШЕНИЯ
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
     * СОЗДАТЬ ПРИГЛАШЕНИЕ
     * =========================================
     */

    public void addInvite(UUID player) {

        /*
         * Нельзя пригласить владельца.
         */
        if (player.equals(owner)) {
            return;
        }

        /*
         * Нельзя пригласить того,
         * кто уже состоит в городе.
         */
        if (hasMember(player)) {
            return;
        }

        /*
         * Нельзя создать второе такое же
         * приглашение.
         */
        if (hasInvite(player)) {
            return;
        }

        invites.add(
                new CityInvite(player)
        );
    }

    /*
     * =========================================
     * УДАЛИТЬ ПРИГЛАШЕНИЕ
     * =========================================
     */

    public void removeInvite(UUID player) {

        invites.removeIf(
                invite ->
                        invite.getPlayerId().equals(player)
        );
    }

    /*
     * =========================================
     * ПРИНЯТЬ ПРИГЛАШЕНИЕ
     * =========================================
     */

    public void acceptInvite(UUID player) {

        /*
         * Приглашения нет —
         * ничего не делаем.
         */
        if (!hasInvite(player)) {
            return;
        }

        /*
         * Добавляем игрока обычным жителем.
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
         * После принятия приглашение
         * удаляется.
         */
        removeInvite(player);
    }
}
