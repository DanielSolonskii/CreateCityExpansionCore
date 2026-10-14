package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * ТЕРРИТОРИЯ: НАБОР УЧАСТКОВ 50×50
     * =========================================
     *
     * Каноническое представление территории города.
     *
     * Раньше территория вычислялась как квадрат вокруг пилона по
     * уровню города, и площадь менялась сама при смене уровня.
     * Теперь владение территорией — это явный набор участков
     * мировой сетки 50×50: он сохраняется, мигрируется из
     * старого квадрата один раз и больше не зависит от уровня.
     *
     * LinkedHashSet, а не HashSet: порядок сохранения в NBT
     * детерминирован, поэтому diff сохранения не «дрожит» и
     * миграцию видно в логе один раз, а не каждый запуск.
     */

    private final Set<CityPlot> claimedPlots;

    /** Собственный цвет города, 0xRRGGBB. Не null и всегда в пределах 24 бит. */
    private int colorRgb;
    private CityIdentity identity = CityIdentity.restore("", "");
    private long identityRevision;

    public CityIdentity getIdentity() { return identity; }
    public long getIdentityRevision() { return identityRevision; }
    void restoreIdentity(String description, String pixels, long revision) {
        identity = CityIdentity.restore(description, pixels);
        identityRevision = Math.max(0, revision);
    }
    void applyIdentity(CityIdentity value, int colour) {
        identity = value;
        colorRgb = colour;
        identityRevision++;
    }

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
         * Территория нового города не пустая: пилон обязан стоять
         * внутри собственной территории, иначе первое же расширение
         * было бы невозможно — соседнего участка не от чего брать.
         *
         * Стартовый набор — один участок 50×50 вокруг пилона.
         * Дальше город расширяется только вбок от уже занятых
         * участков, поэтому уровень города на размер территории
         * больше не влияет.
         */
        this.claimedPlots = new LinkedHashSet<>();
        this.claimedPlots.add(CityClaims.pylonPlot(pylonDimension, this.pylonPos));

        /*
         * При создании города список приглашений
         * пока пустой.
         */
        this.invites = new ArrayList<>();
        this.projects = new ArrayList<>(projects);
        this.developmentPrimaryResource = developmentPrimaryResource;
        this.colorRgb = CityColors.defaultColor(id);
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

    /**
     * Меняет уровень города.
     *
     * <p>Событие телеметрии переименовано из {@code TERRITORY_CHANGED} в
     * {@code CITY_LEVEL_CHANGED}: с переходом на участки территория
     * перестала зависеть от уровня, и событие «территория изменилась»
     * при смене уровня стало просто неверным фактом. Уровень по-прежнему
     * сообщается, но под своим именем.
     */
    public void setLevel(int level) {
        int previous = this.level;
        this.level = Math.max(1, Math.min(level, 2));
        if (previous != this.level) com.example.examplemod.auth.ServerTelemetryBridge.emit("CITY_LEVEL_CHANGED", owner, id, Integer.toString(this.level));
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
     * РАЗМЕР ТЕРРИТОРИИ (УСТАРЕВШЕЕ)
     * =========================================
     */

    /**
     * Старый квадратный размер территории по уровню города.
     *
     * <p><b>Оставлен только для миграции миров, сохранённых до
     * появления участков.</b> Ни владение территорией, ни защита,
     * ни карта, ни граница в мире его больше не читают: площадь
     * города определяется числом занятых участков, а не уровнем.
     *
     * @deprecated используйте {@link #getClaimedPlots()}
     */
    @Deprecated
    public int getLegacyTerritorySize() {

        return switch (level) {

            case 2 -> 150;
            default -> 100;
        };
    }

    /*
     * =========================================
     * ТЕРРИТОРИЯ: УЧАСТКИ 50×50
     * =========================================
     */

    /** Неизменяемый снимок участков города. Порядок стабилен — отсортирован по сетке. */
    public Set<CityPlot> getClaimedPlots() {
        if (claimedPlots.size() <= 1) {
            return claimedPlots.isEmpty() ? Set.of() : Set.copyOf(claimedPlots);
        }
        List<CityPlot> ordered = new ArrayList<>(claimedPlots);
        Collections.sort(ordered);
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    /** Количество участков. 1 участок = 2500 блоков. */
    public int getClaimedPlotCount() {
        return claimedPlots.size();
    }

    /** Суммарная площадь территории в блоках. */
    public long getTerritoryBlockCount() {
        return CityClaims.blockCount(claimedPlots);
    }

    public boolean hasClaim(CityPlot plot) {
        return plot != null && claimedPlots.contains(plot);
    }

    /** Участок, в котором стоит City Pylon города. Всегда входит в территорию. */
    public CityPlot getPylonPlot() {
        return CityClaims.pylonPlot(pylonDimension, pylonPos);
    }

    /**
     * Занимает ли город участок с этими координатами в своём измерении.
     *
     * <p>Именно это, а не квадрат вокруг пилона, определяет
     * принадлежность координаты городу — здесь нет двойного
     * источника правды.
     */
    public boolean ownsBlock(ResourceLocation dimension, int blockX, int blockZ) {
        if (dimension == null || !dimension.equals(pylonDimension)) {
            return false;
        }
        return claimedPlots.contains(CityPlot.at(dimension, blockX, blockZ));
    }

    /**
     * Добавляет участок без проверок.
     *
     * <p>Package-private: единственный путь входа — {@link CityManager#claimPlot}, который
     * проверяет занятость участка, смежность и права игрока. Публичный метод без проверок
     * позволил бы расширить территорию в обход всех правил.
     *
     * @return true, если участок действительно добавлен
     */
    boolean addClaim(CityPlot plot) {
        return plot != null && plot.isValid() && claimedPlots.add(plot);
    }

    /**
     * Снимает участок без проверок.
     *
     * <p>Тоже package-private и пока не используется: снятие участка требует запрета отрыва
     * территории от пилона, и это отдельная осознанная функция. Но мутатор уже существует, чтобы
     * добавить unclaim позже без изменения модели.
     *
     * @return true, если участок был и удалён
     */
    boolean removeClaim(CityPlot plot) {
        return plot != null && claimedPlots.remove(plot);
    }

    /**
     * Заменяет набор участков целиком — только при загрузке из NBT и при миграции.
     *
     * <p>Некорректные координаты отбрасываются молча: повреждённая запись в одном городе
     * не должна ронять загрузку всего мира.
     *
     * @return true, если хотя бы что-то изменилось
     */
    boolean replaceClaims(Collection<CityPlot> plots) {
        Set<CityPlot> replacement = new LinkedHashSet<>();
        if (plots != null) {
            for (CityPlot plot : plots) {
                /*
                 * Участок другого измерения не может принадлежать
                 * этому городу: у города одно измерение, и участок
                 * вне него не имеет смысла.
                 */
                if (plot != null && plot.isValid() && pylonDimension.equals(plot.dimension())) {
                    replacement.add(plot);
                }
            }
        }
        return applyClaims(replacement);
    }

    /**
     * Заменяет набор участков без проверки измерения.
     *
     * <p>Только для переноса пилона в другое измерение, и только после
     * того, как измерение города уже изменено. Загрузка и миграция идут
     * через {@link #replaceClaims(Collection)}, который такую запись
     * отбрасывает: в испорченном сохранении город не должен получить
     * землю в чужом мире.
     */
    boolean replaceClaimsRaw(Collection<CityPlot> plots) {
        Set<CityPlot> replacement = new LinkedHashSet<>();
        if (plots != null) {
            for (CityPlot plot : plots) {
                if (plot != null && plot.isValid()) {
                    replacement.add(plot);
                }
            }
        }
        return applyClaims(replacement);
    }

    private boolean applyClaims(Set<CityPlot> replacement) {
        if (replacement.equals(claimedPlots)) {
            return false;
        }
        claimedPlots.clear();
        claimedPlots.addAll(replacement);
        return true;
    }

    /*
     * =========================================
     * ЦВЕТ ГОРОДА
     * =========================================
     */

    /** Собственный цвет города, {@code 0xRRGGBB}. */
    public int getColorRgb() {
        return colorRgb;
    }

    /**
     * Применяет цвет, только если он входит в палитру.
     *
     * <p>Проверка палитры — не косметика: клиент присылает число,
     * и без неё в сохранение попадёт произвольный цвет, который
     * потом нельзя будет отобразить ни в палитре, ни на карте.
     *
     * @return true, если цвет изменился
     */
    boolean applyColor(int rgb) {
        int normalized = CityColors.normalize(rgb);
        if (!CityColors.isPreset(normalized) || normalized == colorRgb) {
            return false;
        }
        colorRgb = normalized;
        identityRevision++;
        return true;
    }

    /** Восстанавливает цвет из сохранения. Значение вне палитры заменяется детерминированным. */
    void restoreColor(int rgb) {
        colorRgb = CityColors.isPreset(rgb)
                ? CityColors.normalize(rgb)
                : CityColors.defaultColor(id);
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
            com.example.examplemod.auth.ServerTelemetryBridge.emit("PROJECT_REMOVED", project.creatorId(), projectId, project.projectType().name());
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

        boolean removed = members.removeIf(
                member ->
                        member.getPlayerId().equals(player)
        );
        if (removed) com.example.examplemod.auth.ServerTelemetryBridge.emit("CITY_LEAVE", player, id, "");
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
     * УДАЛИТЬ ПРИГЛАШЕНИЕ
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
            com.example.examplemod.auth.ServerTelemetryBridge.emit("CITY_JOIN", player, id, "");
        }

        /*
         * После принятия приглашение
         * удаляется.
         */
        removeInvite(player);
    }
}
