package com.example.examplemod.menu;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityMember;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CityPylonMenu extends AbstractContainerMenu {

    private final BlockPos pylonPos;
    private final boolean hasCity;
    private final String cityName;
    private final int cityLevel;
    private final int territorySize;
    private final UUID owner;
    private final List<CityMember> members;

    public CityPylonMenu(
            int containerId,
            Inventory inventory,
            RegistryFriendlyByteBuf data
    ) {
        this(
                containerId,
                inventory,
                data.readBlockPos(),
                data.readBoolean(),
                data.readUtf(),
                data.readVarInt(),
                data.readVarInt(),
                data.readBoolean() ? data.readUUID() : null,
                readMembers(data)
        );
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
                false,
                "",
                1,
                100,
                null,
                new ArrayList<>()
        );
    }

    public CityPylonMenu(
            int containerId,
            Inventory inventory,
            BlockPos pylonPos,
            boolean hasCity,
            String cityName,
            int cityLevel,
            int territorySize,
            UUID owner,
            List<CityMember> members
    ) {
        super(
                CreateCityExpansionCore.CITY_PYLON_MENU.get(),
                containerId
        );

        this.pylonPos = pylonPos;
        this.hasCity = hasCity;
        this.cityName = cityName;
        this.cityLevel = cityLevel;
        this.territorySize = territorySize;
        this.owner = owner;
        this.members = new ArrayList<>(members);
    }

    public BlockPos getPylonPos() {
        return pylonPos;
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

    public int getTerritorySize() {
        return territorySize;
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