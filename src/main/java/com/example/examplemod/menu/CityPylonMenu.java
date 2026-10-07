package com.example.examplemod.menu;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.city.CityProjectType;
import com.example.examplemod.city.CityDevelopmentRequirementSnapshot;
import com.example.examplemod.city.CityDevelopmentRequirements;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.CityPylonStatePayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.RailwayStatePayload;
import com.example.examplemod.network.RailwayRouteOptionsPayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.network.CityRelationsStatePayload;
import com.example.examplemod.railway.RailwayLineSnapshot;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTrainSnapshot;
import com.example.examplemod.railway.RailwayTrainCandidate;
import com.example.examplemod.railway.RailwayOwnershipSnapshot;
import com.example.examplemod.railway.RailwaySectionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CityPylonMenu extends AbstractContainerMenu implements com.example.examplemod.client.WorldMapDataSource {

    private final BlockPos pylonPos;
    private final ResourceLocation dimensionId;
    private final boolean hasCity;
    private final String cityName;
    private int cityLevel;
    /**
     * Число заявленных участков 50×50.
     *
     * <p>Заменило старый квадратный {@code territorySize}: площадь
     * территории теперь определяется тем, сколько участков реально
     * занято, а не уровнем города.
     */
    private int plotCount;
    /** Собственный цвет города {@code 0xRRGGBB}. */
    private int colorRgb = com.example.examplemod.city.CityColors.PALETTE.get(0);
    /** OWNER или ADMIN — могут расширять территорию и менять цвет. */
    private boolean canManageTerritory;
    private final UUID owner;
    private final List<CityMember> members;
    private int projectCapacity;
    private List<CityProjectSnapshot> projects;
    private ResourceLocation developmentPrimaryResource;
    private List<CityDevelopmentRequirementSnapshot> developmentRequirements;
    private boolean pylonActive;
    private final List<CityMapTilePayload> pendingMapTiles = new ArrayList<>();
    private List<RailwayStationSnapshot> railwayStations = new ArrayList<>();
    private List<RailwayTrainSnapshot> railwayTrains = new ArrayList<>();
    private List<RailwayTrainCandidate> railwayTrainCandidates = new ArrayList<>();
    private List<RailwayLineSnapshot> railwayLines = new ArrayList<>();
    private List<RailwayOwnershipSnapshot> railwayOwnership = new ArrayList<>();
    private List<RailwaySectionSnapshot> railwayOwnedSections = new ArrayList<>();
    private RailwayMapPayload pendingRailwayMap;
    private RailwayRouteOptionsPayload pendingRouteOptions;
    private EconomyStatePayload economyState;
    private CityRelationsStatePayload relationsState;

    public CityPylonMenu(
            int containerId,
            Inventory inventory,
            RegistryFriendlyByteBuf data
    ) {
        this(
                containerId,
                inventory,
                data.readBlockPos(),
                data.readResourceLocation(),
                data.readBoolean(),
                data.readUtf(),
                data.readVarInt(),
                data.readVarInt(),
                data.readVarInt(),
                data.readBoolean(),
                data.readBoolean() ? data.readUUID() : null,
                readMembers(data),
                data.readVarInt(),
                data.readBoolean(),
                readProjects(data),
                data.readBoolean() ? data.readResourceLocation() : null,
                readDevelopmentRequirements(data)
        );
    }

    private static List<CityDevelopmentRequirementSnapshot> readDevelopmentRequirements(
            RegistryFriendlyByteBuf data
    ) {
        int count = Math.min(16, Math.max(0, data.readVarInt()));
        List<CityDevelopmentRequirementSnapshot> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new CityDevelopmentRequirementSnapshot(
                    data.readEnum(CityDevelopmentRequirements.RequirementGroup.class),
                    data.readResourceLocation(),
                    data.readVarLong(),
                    data.readVarLong()
            ));
        }
        return result;
    }

    private static List<CityProjectSnapshot> readProjects(RegistryFriendlyByteBuf data) {
        int count = Math.min(64, Math.max(0, data.readVarInt()));
        List<CityProjectSnapshot> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new CityProjectSnapshot(
                    data.readUUID(),
                    data.readEnum(CityProjectType.class),
                    data.readResourceLocation(),
                    data.readBlockPos(),
                    data.readUUID(),
                    data.readResourceLocation(),
                    data.readDouble(),
                    data.readUtf(128)
            ));
        }
        return result;
    }

    private static List<CityMember> readMembers(
            RegistryFriendlyByteBuf data
    ) {
        List<CityMember> members = new ArrayList<>();

        int count = data.readVarInt();

        for (int i = 0; i < count; i++) {

            UUID playerId = data.readUUID();

            int roleId = data.readVarInt();

            CityMember.Role[] roles = CityMember.Role.values();

            CityMember.Role role;

            if (roleId >= 0 && roleId < roles.length) {
                role = roles[roleId];
            } else {
                role = CityMember.Role.RESIDENT;
            }

            members.add(
                    new CityMember(playerId, role)
            );
        }

        return members;
    }

    public CityPylonMenu(
            int containerId,
            Inventory inventory,
            BlockPos pylonPos
    ) {
        this(
                containerId,
                inventory,
                pylonPos,
                inventory.player.level().dimension().location(),
                false,
                "",
                1,
                0,
                com.example.examplemod.city.CityColors.PALETTE.get(0),
                false,
                null,
                new ArrayList<>(),
                1,
                true,
                new ArrayList<>(),
                null,
                new ArrayList<>()
        );
    }

    public CityPylonMenu(
            int containerId,
            Inventory inventory,
            BlockPos pylonPos,
            ResourceLocation dimensionId,
            boolean hasCity,
            String cityName,
            int cityLevel,
            int plotCount,
            int colorRgb,
            boolean canManageTerritory,
            UUID owner,
            List<CityMember> members,
            int projectCapacity,
            boolean pylonActive,
            List<CityProjectSnapshot> projects,
            ResourceLocation developmentPrimaryResource,
            List<CityDevelopmentRequirementSnapshot> developmentRequirements
    ) {
        super(
                CreateCityExpansionCore.CITY_PYLON_MENU.get(),
                containerId
        );

        this.pylonPos = pylonPos;
        this.dimensionId = dimensionId;
        this.hasCity = hasCity;
        this.cityName = cityName;
        this.cityLevel = cityLevel;
        this.plotCount = plotCount;
        this.colorRgb = com.example.examplemod.city.CityColors.normalize(colorRgb);
        this.canManageTerritory = canManageTerritory;
        this.owner = owner;
        this.members = new ArrayList<>(members);
        this.projectCapacity = projectCapacity;
        this.pylonActive = pylonActive;
        this.projects = new ArrayList<>(projects);
        this.developmentPrimaryResource = developmentPrimaryResource;
        this.developmentRequirements = new ArrayList<>(developmentRequirements);
    }

    public BlockPos getPylonPos() {
        return pylonPos;
    }

    public ResourceLocation getDimensionId() {
        return dimensionId;
    }

    public boolean hasCity() {
        return hasCity;
    }

    public String getCityName() {
        return cityName;
    }

    public int getCityLevel() {
        return cityLevel;
    }

    /** Число заявленных участков 50×50. */
    public int getPlotCount() {
        return plotCount;
    }

    /**
     * Собственный цвет города {@code 0xRRGGBB}.
     *
     * <p>Клиент только показывает это значение; менять его может
     * исключительно сервер, поэтому подделать цвет на клиенте нельзя.
     */
    public int getColorRgb() {
        return colorRgb;
    }

    /** Может ли открывший меню игрок расширять территорию и менять цвет. */
    public boolean canManageTerritory() {
        return canManageTerritory;
    }

    public UUID getOwner() {
        return owner;
    }

    public List<CityMember> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public int getProjectCapacity() { return projectCapacity; }
    public List<CityProjectSnapshot> getProjects() { return List.copyOf(projects); }
    public ResourceLocation getDevelopmentPrimaryResource() { return developmentPrimaryResource; }
    public List<CityDevelopmentRequirementSnapshot> getDevelopmentRequirements() {
        return List.copyOf(developmentRequirements);
    }
    public boolean areDevelopmentRequirementsComplete() {
        return !developmentRequirements.isEmpty()
                && developmentRequirements.stream().allMatch(
                CityDevelopmentRequirementSnapshot::complete
        );
    }
    public boolean isPylonActive() { return pylonActive; }

    public void applyState(CityPylonStatePayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())) {
            return;
        }
        this.cityLevel = payload.cityLevel();
        this.projectCapacity = payload.projectCapacity();
        this.projects = new ArrayList<>(payload.projects());
        this.developmentPrimaryResource = payload.primaryResource();
        this.developmentRequirements = new ArrayList<>(payload.developmentRequirements());
        /*
         * Цвет и число участков — часть состояния города, поэтому
         * обновляются здесь же: смена цвета и успешный claim приходят
         * на открытый экран без переоткрытия пилона.
         */
        this.plotCount = payload.plotCount();
        this.colorRgb = com.example.examplemod.city.CityColors.normalize(payload.colorRgb());
        this.canManageTerritory = payload.canManageTerritory();
    }

    public void acceptMapTile(CityMapTilePayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())
                || !this.dimensionId.equals(payload.dimensionId())) {
            return;
        }
        if (this.pendingMapTiles.size() >= 64) {
            this.pendingMapTiles.remove(0);
        }
        this.pendingMapTiles.add(payload);
    }

    public List<CityMapTilePayload> drainMapTiles() {
        List<CityMapTilePayload> result = new ArrayList<>(this.pendingMapTiles);
        this.pendingMapTiles.clear();
        return result;
    }

    public List<RailwayStationSnapshot> getRailwayStations() {
        return List.copyOf(this.railwayStations);
    }

    public List<RailwayTrainSnapshot> getRailwayTrains() {
        return List.copyOf(this.railwayTrains);
    }

    /** Create trains visible to this pylon that are not registered in CCEC yet. */
    public List<RailwayTrainCandidate> getRailwayTrainCandidates() {
        return List.copyOf(this.railwayTrainCandidates);
    }

    public List<RailwayLineSnapshot> getRailwayLines() {
        return List.copyOf(this.railwayLines);
    }
    public List<RailwayOwnershipSnapshot> getRailwayOwnership(){return List.copyOf(this.railwayOwnership);}
    public List<RailwaySectionSnapshot> getRailwayOwnedSections(){return List.copyOf(this.railwayOwnedSections);}

    public void applyRailwayState(RailwayStatePayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())) {
            return;
        }
        this.railwayStations = new ArrayList<>(payload.stations());
        this.railwayTrains = new ArrayList<>(payload.trains());
        this.railwayTrainCandidates = new ArrayList<>(payload.candidates());
        this.railwayLines = new ArrayList<>(payload.lines());
        this.railwayOwnership = new ArrayList<>(payload.ownership());
        this.railwayOwnedSections = new ArrayList<>(payload.ownedSections());
    }

    public void acceptRailwayMap(RailwayMapPayload payload) {
        if (this.pylonPos.equals(payload.pylonPos())
                && this.dimensionId.equals(payload.dimension())) {
            this.pendingRailwayMap = payload;
        }
    }

    public RailwayMapPayload drainRailwayMap() {
        RailwayMapPayload result = this.pendingRailwayMap;
        this.pendingRailwayMap = null;
        return result;
    }
    public void acceptRouteOptions(RailwayRouteOptionsPayload payload){if(payload.pylonPos().equals(this.pylonPos))this.pendingRouteOptions=payload;}
    public RailwayRouteOptionsPayload drainRouteOptions(){RailwayRouteOptionsPayload value=pendingRouteOptions;pendingRouteOptions=null;return value;}
    public void applyEconomyState(EconomyStatePayload payload) {
        if (this.pylonPos.equals(payload.pylonPos())) this.economyState = payload;
    }
    public EconomyStatePayload getEconomyState() { return economyState; }
    public void applyRelationsState(CityRelationsStatePayload payload) {
        if (this.pylonPos.equals(payload.pylonPos())) this.relationsState = payload;
    }
    public CityRelationsStatePayload getRelationsState() { return relationsState; }
    public void updateMemberRole(
            UUID playerId,
            CityMember.Role role
    ) {
        for (CityMember member : this.members) {

            if (member.getPlayerId().equals(playerId)) {

                member.setRole(role);

                return;
            }
        }
    }
    /**
     * Replaces the whole member list from an authoritative snapshot. A snapshot cannot be applied
     * twice or out of order and end up wrong, so no delta bookkeeping is needed, and applying the
     * same snapshot twice is a no-op rather than a duplication.
     */
    public void replaceMembers(List<CityMember> authoritative) {
        this.members.clear();
        this.members.addAll(authoritative);
    }

    public void removeMember(UUID playerId) {

        this.members.removeIf(
                member -> member.getPlayerId().equals(playerId)
        );
    }
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        return ItemStack.EMPTY;
    }
}
