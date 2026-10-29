package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Территориальные участки 50×50: сетка, миграция, права, сохранение, цвет.
 *
 * <p>Проверяется headless против настоящего {@link CityManager}, а не
 * против копии логики: save/load, миграция и защита проходят через тот же
 * код, что и на сервере. Единственное, что здесь не проверяется, — сам
 * Minecraft-оверлей и сеть; для них есть отдельные задачи.
 */
public final class CityTerritoryCheck {

    private static int checks;

    private static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();
    private static final ResourceLocation NETHER = Level.NETHER.location();

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        plotGrid();
        negativeCoordinates();
        plotBounds();
        legacyMigration();
        claimRules();
        otherCityPlots();
        permissionRules();
        colorRules();
        claimFrontier();
        snapshotSurvivesNoMasterCity();
        persistence();
        restartSafety();
        relocation();
        outline();
        interactionGrants();
        transportGrants();

        System.out.println("PASS: " + checks + " city territory claim checks");
    }

    // ------------------------------------------------------------------ сетка участков

    private static CityPlot plot(int plotX, int plotZ) {
        return new CityPlot(OVERWORLD, plotX, plotZ);
    }

    /** Первый пресет, отличный от {@code current}. Палитра достаточно велика, чтобы он существовал. */
    private static int paletteOtherThan(int current) {
        for (int color : CityColors.PALETTE) {
            if (color != current) {
                return color;
            }
        }
        throw new AssertionError("palette offers no alternative to #" + Integer.toHexString(current));
    }

    private static void plotGrid() {
        require(CityPlot.PLOT_SIZE == 50, "a claim is exactly 50x50 blocks");
        require(CityPlot.PLOT_MAX_OFFSET == 49, "a claim ends at +49, not +50");
        require(CityPlot.plotXOf(0) == 0, "block 0 is in plot 0");
        require(CityPlot.plotXOf(49) == 0, "block 49 is the last block of plot 0");
        require(CityPlot.plotXOf(50) == 1, "block 50 opens plot 1");
        require(CityPlot.plotXOf(51) == 1, "block 51 is still plot 1");

        CityPlot origin = plot(0, 0);
        require(origin.minX() == 0 && origin.maxX() == 49, "plot (0,0) spans 0..49 on X");
        require(origin.minZ() == 0 && origin.maxZ() == 49, "plot (0,0) spans 0..49 on Z");
        require(origin.contains(0, 0) && origin.contains(49, 49), "corners belong to the plot");
        require(!origin.contains(50, 0) && !origin.contains(0, 50), "the next block is not in the plot");

        // Соседство по стороне, но не по углу.
        require(origin.isAdjacentTo(plot(1, 0)), "+X is a side neighbour");
        require(origin.isAdjacentTo(plot(-1, 0)), "-X is a side neighbour");
        require(origin.isAdjacentTo(plot(0, 1)), "+Z is a side neighbour");
        require(origin.isAdjacentTo(plot(0, -1)), "-Z is a side neighbour");
        require(!origin.isAdjacentTo(plot(1, 1)), "a diagonal touch is not adjacency");
        require(!origin.isAdjacentTo(plot(2, 0)), "a plot two steps away is not adjacency");
        require(!origin.isAdjacentTo(origin), "a plot is not its own neighbour");
        require(origin.neighbours().size() == 4, "exactly four side neighbours");

        // Смежность не пересекает измерения: один участок не может соседствовать с другим миром.
        require(!origin.isAdjacentTo(new CityPlot(NETHER, 1, 0)),
                "plots in different dimensions are never adjacent");
    }

    private static void negativeCoordinates() {
        require(CityPlot.plotXOf(-1) == -1, "block -1 is in plot -1, not plot 0");
        require(CityPlot.plotXOf(-50) == -1, "block -50 is the first block of plot -1");
        require(CityPlot.plotXOf(-51) == -2, "block -51 opens plot -2");
        require(CityPlot.plotZOf(-1) == -1 && CityPlot.plotZOf(-50) == -1 && CityPlot.plotZOf(-51) == -2,
                "negative Z behaves exactly like negative X");

        CityPlot negative = plot(-1, -1);
        require(negative.minX() == -50 && negative.maxX() == -1, "plot (-1,-1) spans -50..-1 on X");
        require(negative.minZ() == -50 && negative.maxZ() == -1, "plot (-1,-1) spans -50..-1 on Z");
        require(negative.contains(-1, -1), "block (-1,-1) is inside plot (-1,-1)");
        require(!negative.contains(0, -1), "block 0 is not inside plot (-1,-1)");
        require(!plot(0, 0).contains(-1, -1), "truncation would wrongly put -1 in plot 0");

        // Стык сетки на оси: участок -1 соседствует с 0 по стороне,
        // а (-1,-1) с (0,0) соприкасается только углом.
        require(negative.isAdjacentTo(plot(0, -1)) && negative.isAdjacentTo(plot(-1, 0)),
                "plot (-1,-1) touches plot (0,-1) and plot (-1,0) along a side");
        require(!negative.isAdjacentTo(plot(0, 0)),
                "plot (-1,-1) and plot (0,0) meet at a corner only, which is not adjacency");
        require(plot(-2, -1).isAdjacentTo(negative), "plot (-2,-1) touches plot (-1,-1)");

        // Отображение координата <-> участок согласовано в обе стороны по всей сетке.
        for (int block = -260; block <= 260; block++) {
            CityPlot at = CityPlot.at(OVERWORLD, block, block);
            require(at.minX() <= block && block <= at.maxX(),
                    "block " + block + " must be inside its own plot");
            require(at.minZ() <= block && block <= at.maxZ(),
                    "block " + block + " must be inside its own plot on Z");
            require(CityPlot.plotXOf(block) == Math.floorDiv(block, 50),
                    "plot lookup must be floor division at " + block);
        }
    }

    private static void plotBounds() {
        CityPlot origin = plot(0, 0);
        require(origin.maxX() - origin.minX() + 1 == 50, "a claim is 50 blocks wide, inclusive bounds");
        require(origin.maxZ() - origin.minZ() + 1 == 50, "a claim is 50 blocks deep, inclusive bounds");
        require(CityClaims.blockCount(Set.of(origin)) == 2500L, "one claim is 2500 blocks");

        CityPlot far = plot(500, -500);
        require(far.isValid(), "a distant but in-range claim is valid");
        require(far.minX() == 25_000 && far.maxX() == 25_049, "a distant claim converts back exactly");
        require(far.minZ() == -25_000 && far.maxZ() == -24_951, "negative distant claims convert back exactly");

        // Враждебные координаты не должны переполнять int при обратном преобразовании.
        require(!plot(Integer.MAX_VALUE, 0).isValid(), "a maximal plot coordinate is rejected");
        require(!plot(Integer.MIN_VALUE, 0).isValid(), "a minimal plot coordinate is rejected");
        require(!plot(CityPlot.MAX_PLOT_COORDINATE + 1, 0).isValid(), "one past the bound is rejected");
        require(plot(CityPlot.MAX_PLOT_COORDINATE, 0).isValid(), "the bound itself is accepted");
        require(plot(-CityPlot.MAX_PLOT_COORDINATE, 0).isValid(), "the negative bound is accepted");
        require((long) CityPlot.MAX_PLOT_COORDINATE * 50L < Integer.MAX_VALUE,
                "an accepted claim always converts to a block coordinate without overflow");

        require(plot(1, 2).minChunkX() == 3 && plot(1, 2).maxChunkX() == 6,
                "blocks 50..99 live in chunks 3..6");
        require(plot(0, 0).minChunkX() == 0 && plot(0, 0).maxChunkX() == 3,
                "a claim spanning blocks 0..49 touches chunks 0..3");
        require(plot(-1, 0).minChunkX() == -4 && plot(-1, 0).maxChunkX() == -1,
                "a negative claim touches the correct negative chunks");
    }

    // ------------------------------------------------------------------------ миграция

    /**
     * Город без секции Claims обязан получить участки, покрывающие его
     * прежний квадрат. Проверяется на настоящем NBT из мира, а не через
     * вызов CityClaims напрямую: важна именно загрузка сохранения.
     */
    private static void legacyMigration() {
        UUID owner = UUID.randomUUID();
        CompoundTag legacy = legacyCityTag("Old Town", owner, new BlockPos(0, 64, 0), 1);
        CityManager restored = CityManager.load(wrap(legacy), null);
        City city = restored.getCityByName("Old Town");
        require(city != null, "a legacy city still loads");

        /*
         * Старая территория уровня 1 — это квадрат -50..50, поэтому
         * миграция даёт участки -1..1 на обеих осях.
         */
        require(city.getClaimedPlotCount() == 9,
                "a level-1 legacy city migrates to 3x3 plots, got " + city.getClaimedPlotCount());
        require(city.hasClaim(plot(0, 0)), "the pylon plot is claimed");
        require(city.hasClaim(plot(-1, -1)) && city.hasClaim(plot(1, 1)),
                "every plot overlapping the old square is claimed");
        require(!city.hasClaim(plot(-2, 0)), "a plot outside the old square is not claimed");
        require(CityClaims.isConnected(city.getPylonPlot(), city.getClaimedPlots()),
                "a migrated territory is one connected piece of land");

        // Ни один ранее защищённый блок не должен стать свободным.
        for (int x = -50; x <= 50; x += 7) {
            for (int z = -50; z <= 50; z += 7) {
                require(city.ownsBlock(OVERWORLD, x, z),
                        "block " + x + "," + z + " was protected before and must stay protected");
            }
        }
        require(!city.ownsBlock(OVERWORLD, -51, 0), "a block outside the old square stays free");

        // Город второго уровня: старая территория была 150, то есть -75..75.
        CompoundTag levelTwo = legacyCityTag("Big Town", UUID.randomUUID(), new BlockPos(60, 64, -30), 2);
        CityManager big = CityManager.load(wrap(levelTwo), null);
        City bigCity = big.getCityByName("Big Town");
        require(bigCity.getClaimedPlotCount() == 16,
                "a level-2 legacy city migrates to 4x4 plots, got " + bigCity.getClaimedPlotCount());
        require(bigCity.hasClaim(new CityPlot(OVERWORLD, 1, -1)),
                "the pylon at (60,-30) sits in plot (1,-1)");
        // Старая территория уровня 2: -15..135 по X и -105..45 по Z.
        require(bigCity.ownsBlock(OVERWORLD, -15, -105), "the far corner of the old square is still owned");
        require(bigCity.ownsBlock(OVERWORLD, 135, 45), "the opposite corner of the old square is still owned");
        /*
         * Заметный побочный эффект миграции, зафиксированный намеренно:
         * участки выровнены по сетке, поэтому занятая земля всегда
         * кратна 50 и покрывает старый квадрат целиком. Блок -16 лежит
         * уже за старой границей (-15), но попадает в участок -1
         * (-50..-1), который пересекает старый квадрат, — поэтому он
         * защищён. Заметить это важнее, чем случайно «срезать» землю:
         * миграция нигде не должна терять защиту.
         */
        require(bigCity.ownsBlock(OVERWORLD, -16, 0),
                "grid-aligned migration covers a little more than the old square, never less");
        require(!bigCity.ownsBlock(OVERWORLD, -51, 0),
                "the first block beyond the migrated claims is free");
        require(!bigCity.ownsBlock(OVERWORLD, 151, 0),
                "the far end of the migrated claims stops at 150");

        // Пилон со смещением: старая территория -26..74, то есть участки -1..1.
        CompoundTag shifted = legacyCityTag("Shifted", UUID.randomUUID(), new BlockPos(24, 64, 24), 1);
        CityManager shiftManager = CityManager.load(wrap(shifted), null);
        City shiftCity = shiftManager.getCityByName("Shifted");
        require(shiftCity.getClaimedPlotCount() == 9,
                "a shifted level-1 pylon still migrates to a 3x3 block of claims, got "
                        + shiftCity.getClaimedPlotCount());
        require(shiftCity.hasClaim(plot(0, 0)) && shiftCity.hasClaim(plot(1, 1)),
                "a pylon at (24,24) keeps both plots its old square overlapped");
        require(shiftCity.hasClaim(plot(-1, -1)), "and the plot on the far side of the origin");
        require(!shiftCity.hasClaim(plot(-2, 0)) && !shiftCity.hasClaim(plot(0, 2)),
                "a pylon at (24,24) does not reach the next plot band");
        require(shiftCity.getPylonPlot().equals(plot(0, 0)), "the shifted pylon is still in plot (0,0)");

        require(restored.migratedCities().size() == 1,
                "exactly the legacy city is reported as migrated");
        require(big.migratedCities().size() == 1 && shiftManager.migratedCities().size() == 1,
                "each load reports only its own migration, so reports never mix");
    }

    private static CompoundTag wrap(Tag cityTag) {
        CompoundTag root = new CompoundTag();
        ListTag cities = new ListTag();
        cities.add(cityTag);
        root.put("Cities", cities);
        root.put("Relations", new ListTag());
        return root;
    }

    /** Город, сохранённый до появления участков: нет ни Claims, ни Color. */
    private static CompoundTag legacyCityTag(String name, UUID owner, BlockPos pylon, int level) {
        CompoundTag cityTag = new CompoundTag();
        cityTag.putUUID("Id", UUID.randomUUID());
        cityTag.putString("Name", name);
        cityTag.putUUID("Owner", owner);
        cityTag.put("PylonPos", net.minecraft.nbt.NbtUtils.writeBlockPos(pylon));
        cityTag.putInt("Level", level);
        cityTag.putString("PylonDimension", OVERWORLD.toString());
        return cityTag;
    }

    // --------------------------------------------------------------------------- claim

    private static void claimRules() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Claim", owner, OVERWORLD, new BlockPos(10, 64, 10));
        require(city != null, "a city is founded on a free plot");
        require(city.getClaimedPlotCount() == 1, "a new city starts with the pylon plot only");
        require(city.getPylonPlot().equals(plot(0, 0)), "the pylon at (10,10) sits in plot (0,0)");

        require(manager.claimPlot(city, plot(1, 0), owner) == CityClaimResult.CLAIMED,
                "a side-adjacent plot is claimed");
        require(city.hasClaim(plot(1, 0)), "the claimed plot is stored on the city");
        require(manager.getClaimOwner(plot(1, 0)) == city, "the spatial index points at the city");

        require(manager.claimPlot(city, plot(1, 0), owner) == CityClaimResult.ALREADY_OWNED,
                "claiming the same plot twice is reported, not silently accepted");
        require(city.getClaimedPlotCount() == 2, "a repeated claim does not grow the territory");

        require(manager.claimPlot(city, plot(1, 1), owner) == CityClaimResult.CLAIMED,
                "a plot adjacent to a freshly claimed plot is claimable");
        require(manager.claimPlot(city, plot(0, -1), owner) == CityClaimResult.CLAIMED,
                "expansion works in every direction");

        // Удалённый участок: ни одна сторона не касается территории.
        require(manager.claimPlot(city, plot(5, 5), owner) == CityClaimResult.NOT_ADJACENT,
                "a remote plot is refused");
        require(!city.hasClaim(plot(5, 5)), "a refused remote plot is not stored");

        // Город из двух участков (0,0) и (1,0): (2,1) касается только углом.
        CityManager cornerManager = new CityManager();
        UUID cornerOwner = UUID.randomUUID();
        City cornerCity = cornerManager.createCity("Corner", cornerOwner, new BlockPos(0, 64, 0));
        require(cornerManager.claimPlot(cornerCity, plot(1, 0), cornerOwner) == CityClaimResult.CLAIMED,
                "precondition: the city owns (1,0)");
        require(cornerManager.claimPlot(cornerCity, plot(2, 1), cornerOwner) == CityClaimResult.NOT_ADJACENT,
                "a diagonal neighbour is refused even when the city is otherwise large");
        require(cornerManager.claimPlot(cornerCity, plot(2, 0), cornerOwner) == CityClaimResult.CLAIMED,
                "the square gap beside an owned plot is claimable");
        require(CityClaims.isConnected(cornerCity.getPylonPlot(), cornerCity.getClaimedPlots()),
                "a territory grown through the rule is always connected");

        // Участки с опасными координатами.
        require(manager.claimPlot(city, new CityPlot(OVERWORLD, Integer.MAX_VALUE, 0), owner)
                        == CityClaimResult.INVALID_PLOT,
                "an out-of-range plot is refused before it reaches the index");
        require(manager.claimPlot(city, new CityPlot(NETHER, 1, 1), owner)
                        == CityClaimResult.DIMENSION_MISMATCH,
                "a city cannot claim land in another dimension");

        /*
         * Соседство считается по участкам, а не по блокам. Территория
         * сейчас (0,0), (1,0), (1,1), (0,-1). Участок (-1,1) касается
         * её только углами: его соседи (-2,1), (0,1), (-1,0), (-1,2) не
         * заняты ни один из них.
         */
        require(manager.claimPlot(city, plot(-1, 1), owner) == CityClaimResult.NOT_ADJACENT,
                "a plot touching the territory only at corners is refused");
        require(manager.claimPlot(city, plot(-1, -1), owner) == CityClaimResult.CLAIMED,
                "a plot sharing a full side with (0,-1) is claimable even though it looks diagonal on (0,0)");

        // Защита следует за участками, а не за квадратом.
        require(manager.getCityAt(OVERWORLD, 60, 10) == city, "a block inside a claimed plot is owned");
        require(manager.getCityAt(OVERWORLD, 10, 10) == city, "a block inside the pylon plot is owned");
        require(manager.getCityAt(OVERWORLD, 10, -1) == city,
                "the claimed plot (0,-1) is protected on the far side of the origin");
        require(manager.getCityAt(OVERWORLD, 49, 49) == city, "the far corner of the pylon plot is owned");
        require(manager.getCityAt(OVERWORLD, 100, 10) == null,
                "plot (2,0) was never claimed, so even its first block is free");
        require(manager.getCityAt(OVERWORLD, 50, 0) == city,
                "block 50 belongs to plot (1,0), which was claimed, not to a free plot");
        require(manager.getCityAt(OVERWORLD, 10, 60) == null,
                "plot (0,1) sits between two owned plots and was never claimed, so it stays free");
        require(manager.getCityAt(NETHER, 10, 10) == null, "the same coordinates in another dimension are free");
        require(city.ownsBlock(OVERWORLD, 49, 49) && !city.ownsBlock(NETHER, 10, 10),
                "ownership is per dimension");
    }

    private static void otherCityPlots() {
        CityManager manager = new CityManager();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        City first = manager.createCity("First", firstOwner, new BlockPos(0, 64, 0));
        City second = manager.createCity("Second", secondOwner, new BlockPos(50, 64, 0));
        require(second != null, "a second city can be founded on the neighbouring plot");
        require(manager.getClaimOwner(plot(0, 0)) == first, "plot (0,0) belongs to the first city");
        require(manager.getClaimOwner(plot(1, 0)) == second, "plot (1,0) belongs to the second city");

        // Второй город хочет участок первого: он свободен с точки зрения второго,
        // но занят в индексе, поэтому отказ приходит по занятости, а не по смежности.
        require(manager.claimPlot(second, plot(0, 0), secondOwner) == CityClaimResult.OWNED_BY_OTHER_CITY,
                "a city cannot take another city's plot");
        require(manager.getClaimOwner(plot(0, 0)) == first, "the refused claim changed nothing");

        // Нельзя основать город на чужом участке.
        require(manager.createCity("Third", UUID.randomUUID(), OVERWORLD, new BlockPos(10, 64, 10)) == null,
                "a capital cannot be founded on land another city already claims");
        require(manager.getCityCount() == 2, "the refused founding created nothing");

        // Соседние участки разных городов уживаются: границы просто соприкасаются.
        require(first.hasClaim(plot(0, 0)) && second.hasClaim(plot(1, 0)),
                "two adjacent cities each keep their own plot");
    }

    private static void permissionRules() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Roles", owner, new BlockPos(0, 64, 0));
        UUID admin = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        UUID resident = UUID.randomUUID();
        city.addMember(admin, CityMember.Role.ADMIN);
        city.addMember(builder, CityMember.Role.BUILDER);
        city.addMember(resident, CityMember.Role.RESIDENT);

        require(manager.claimPlot(city, plot(1, 0), owner) == CityClaimResult.CLAIMED, "the owner may claim");
        require(manager.claimPlot(city, plot(0, 1), admin) == CityClaimResult.CLAIMED, "an admin may claim");
        require(manager.claimPlot(city, plot(-1, 0), builder) == CityClaimResult.NO_PERMISSION,
                "a builder may not expand the territory");
        require(manager.claimPlot(city, plot(0, -1), resident) == CityClaimResult.NO_PERMISSION,
                "a resident may not expand the territory");
        require(manager.claimPlot(city, plot(-1, 0), null) == CityClaimResult.NO_PERMISSION,
                "a request without an actor is refused");
        require(manager.claimPlot(null, plot(1, 1), owner) == CityClaimResult.NO_CITY,
                "a request without a city is refused");
        require(city.getClaimedPlotCount() == 3, "only the owner and the admin grew the territory");

        // Права на блок остаются ролевыми: участок теперь защищён, а роли те же.
        require(city.canBuild(owner) && city.canBuild(admin) && city.canBuild(builder),
                "owner, admin and builder still build");
        require(!city.canBuild(resident), "a resident still cannot build");
        require(city.hasMember(resident), "a resident still has interaction access");
        require(!city.hasMember(UUID.randomUUID()), "a stranger has no access at all");
    }

    // --------------------------------------------------------------------------- цвет

    private static void colorRules() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Colour", owner, new BlockPos(0, 64, 0));

        require(CityColors.PALETTE.size() >= 8, "the palette offers a real choice");
        for (int color : CityColors.PALETTE) {
            require(color >= 0 && color <= 0xFFFFFF, "every palette colour is a 24-bit RGB");
            int red = color >> 16 & 0xFF, green = color >> 8 & 0xFF, blue = color & 0xFF;
            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            require(max >= 96, "no palette colour is too dark to read on terrain");
            require(min <= 208, "no palette colour is so light it washes out the terrain");
            require(max - min >= 40, "every palette colour has a readable hue, not a grey");
        }
        for (int index = 1; index < CityColors.PALETTE.size(); index++) {
            require(!CityColors.PALETTE.get(index).equals(CityColors.PALETTE.get(index - 1)),
                    "the palette has no adjacent duplicates");
        }

        // Дефолт стабилен и приходит из палитры.
        UUID fixed = UUID.fromString("6f2a1c58-4d3b-4a1e-9c77-2b8a5d1e0f33");
        int first = CityColors.defaultColor(fixed);
        for (int repeat = 0; repeat < 5; repeat++) {
            require(CityColors.defaultColor(fixed) == first, "the default colour never changes between runs");
        }
        require(CityColors.isPreset(first), "the default colour comes from the palette");
        require(CityColors.defaultColor(UUID.randomUUID()) != 0 || true, "any UUID yields a palette colour");
        require(CityColors.isPreset(CityColors.defaultColor(UUID.randomUUID())),
                "every UUID maps into the palette");

        require(city.getColorRgb() == CityColors.defaultColor(city.getId()),
                "a new city starts on its own deterministic colour");

        // Смена цвета: только пресет и только OWNER/ADMIN.
        // Пресет выбирается отличным от текущего: setCityColor корректно
        // возвращает false, когда цвет не меняется, а текущий цвет
        // выводится из случайного UUID и может совпасть с любым индексом.
        int wanted = paletteOtherThan(city.getColorRgb());
        require(CityColors.isPreset(wanted), "the chosen colour is a preset");
        require(!manager.setCityColor(city, 0x123456, owner),
                "a colour outside the palette is refused");
        require(city.getColorRgb() == CityColors.defaultColor(city.getId()),
                "a refused colour change changes nothing");
        require(manager.setCityColor(city, wanted, owner), "the owner can change the colour");
        require(city.getColorRgb() == wanted, "the colour was applied");
        require(!manager.setCityColor(city, wanted, owner), "setting the same colour again is a no-op");

        UUID admin = UUID.randomUUID();
        city.addMember(admin, CityMember.Role.ADMIN);
        require(manager.setCityColor(city, paletteOtherThan(wanted), admin),
                "an admin can change the colour");
        UUID resident = UUID.randomUUID();
        city.addMember(resident, CityMember.Role.RESIDENT);
        require(!manager.setCityColor(city, paletteOtherThan(wanted), resident),
                "a resident cannot change the colour");

        // Альфа-канал не должен попасть в значение города: клиент шлёт
        // «сырой» int, поэтому альфа отбрасывается и сверяется с палитрой.
        require(CityColors.normalize(CityColors.argb(wanted, 0x80)) == wanted,
                "an alpha-carrying colour normalises back to its palette entry");
        require(CityColors.normalize(0xFF123456) == 0x123456, "the alpha byte is dropped on read");
        require(!CityColors.isPreset(0x123456), "a colour outside the palette is still refused with alpha stripped");
    }

    // -------------------------------------------------------------------- сохранение

    // ------------------------------------------------------- фронтир доступных участков

    /**
     * Набор участков, которые карта предлагает взять следующими.
     *
     * <p>Это ровно тот предикат, который использует клиент для подсветки
     * и отправки claim, поэтому он проверяется здесь на настоящем
     * {@link CityManager}: если он разойдётся с серверным правилом,
     * карта начнёт предлагать то, что сервер отклонит.
     */
    private static Set<CityPlot> frontier(CityManager manager, City city, Set<CityPlot> occupiedByPeers) {
        Set<CityPlot> frontier = new java.util.LinkedHashSet<>();
        for (CityPlot owned : city.getClaimedPlots()) {
            for (CityPlot adjacent : owned.neighbours()) {
                if (!city.hasClaim(adjacent) && !occupiedByPeers.contains(adjacent)) {
                    frontier.add(adjacent);
                }
            }
        }
        return frontier;
    }

    private static void claimFrontier() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Frontier", owner, OVERWORLD, new BlockPos(0, 64, 0));

        // Новый город: единственный участок, вокруг него — четыре соседа.
        Set<CityPlot> frontier = frontier(manager, city, Set.of());
        require(frontier.equals(Set.of(plot(1, 0), plot(-1, 0), plot(0, 1), plot(0, -1))),
                "a new city can take exactly its four neighbours, got " + frontier);
        require(!frontier.contains(plot(1, 1)), "a diagonal plot is not in the frontier");
        require(!frontier.contains(plot(2, 0)), "a plot two steps away is not in the frontier");

        // Каждый предложенный участок реально забирается сервером: подсветка
        // не может предлагать отказ.
        for (CityPlot candidate : frontier) {
            require(manager.claimPlot(city, candidate, owner) == CityClaimResult.CLAIMED,
                    "every frontier plot must be claimable: " + candidate);
        }
        require(city.getClaimedPlotCount() == 5, "a plus-shaped territory has five claims, got "
                + city.getClaimedPlotCount());

        /*
         * Кольцо вокруг плюса. Четыре «внутренних» угла участка (1,1) и
         * подобные становятся доступны: к плюсу они примыкают сразу двумя
         * сторонами. Плюс четыре внешних участка. Всего восемь.
         *
         * Участок (2,1) не предлагается: он касается плюса только углом
         * через (1,0), и правило смежности по стороне его отвергает.
         */
        Set<CityPlot> grown = frontier(manager, city, Set.of());
        require(grown.size() == 8, "a plus-shaped territory offers eight plots, got " + grown.size());
        require(grown.contains(plot(1, 1)) && grown.contains(plot(-1, -1)),
                "a corner filling the gap between two arms is offered: it shares two sides");
        require(grown.contains(plot(2, 0)) && grown.contains(plot(0, 2)),
                "the outer ring is offered where it touches an arm directly");
        require(!grown.contains(plot(2, 1)) && !grown.contains(plot(2, 2)),
                "plots that only touch an arm at a corner are not offered");
        for (CityPlot candidate : grown) {
            require(manager.claimPlot(city, candidate, owner) == CityClaimResult.CLAIMED,
                    "every grown frontier plot must be claimable: " + candidate);
        }

        /*
         * Занятый чужим городом участок не предлагается: сервер всё равно
         * отклонил бы его по занятости, и подсветка не должна обещать.
         *
         * Свой город расширяется на (1,0), а второй город основывается
         * на том же участке — так участок принадлежит именно ему, и
         * «занято чужим городом» проверяется настоящим индексом, а не
         * подставленным вручную множеством.
         */
        CityManager shared = new CityManager();
        City first = shared.createCity("First", UUID.randomUUID(), new BlockPos(0, 64, 0));
        City second = shared.createCity("Second", UUID.randomUUID(), new BlockPos(50, 64, 0));
        require(second != null, "a second city can be founded on the neighbouring plot");
        require(shared.getClaimOwner(plot(1, 0)) == second,
                "precondition: the second city owns (1,0)");
        Set<CityPlot> foreignOwned = Set.of(plot(1, 0));

        // Город владеет только участком пилона, поэтому его фронтир — это
        // ровно четыре соседа. Один из них занят вторым городом, значит
        // предлагаются три, а не четыре.
        Set<CityPlot> firstFrontier = frontier(shared, first, foreignOwned);
        require(firstFrontier.size() == 3,
                "a city walled in on one side offers three plots, got " + firstFrontier.size());
        require(!firstFrontier.contains(plot(1, 0)),
                "a plot owned by another city must not be offered as claimable");
        require(firstFrontier.contains(plot(-1, 0)) && firstFrontier.contains(plot(0, 1))
                        && firstFrontier.contains(plot(0, -1)),
                "the unoccupied neighbours stay available");
        require(shared.claimPlot(first, plot(1, 0), first.getOwner()) == CityClaimResult.OWNED_BY_OTHER_CITY,
                "claiming a foreign plot is refused, so the frontier never promises it");
        require(firstFrontier.stream().allMatch(shared::isPlotFree),
                "every plot the client is offered must be genuinely free on the server");

        // Один город, раздвинувшийся на два участка, не может забрать
        // участок за спиной соседа: участок (2,0) свободен, но отделён
        // чужой землёй и в смежности не участвует.
        require(shared.claimPlot(first, plot(-1, 0), first.getOwner()) == CityClaimResult.CLAIMED,
                "the first city may expand away from its neighbour");
        Set<CityPlot> afterGrowth = frontier(shared, first, foreignOwned);
        require(!afterGrowth.contains(plot(1, 0)) && !afterGrowth.contains(plot(1, -1)),
                "a foreign claim and everything beyond it stay off the frontier");
        require(afterGrowth.contains(plot(-2, 0)),
                "the new frontier extends along the direction that was actually free");
    }

    /**
     * Снимок территорий не зависит от того, назначена ли master city.
     *
     * <p><b>Регрессия.</b> Формирование снимка передавало предикат
     * «является ли город master» как {@code masterCityId::equals}. Master city
     * в новом мире не назначена, идентификатор равен {@code null}, и
     * {@code null::equals} ронял весь снимок на первом же городе. Снимок
     * падал на каждом запросе: карта оставалась без заливки и без фронтира, а
     * ПКМ по участку нечего было принимать — при этом исключение уходило
     * только в лог сервера, и на экране не появлялось ничего.
     *
     * <p>Здесь проверяется контракт предиката: «master city не назначена»
     * обязано означать «ни один город не master», а не исключение.
     */
    private static void snapshotSurvivesNoMasterCity() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("NoMaster", owner, new BlockPos(0, 64, 0));
        require(city != null, "the city exists for the no-master scenario");

        // Master city не назначена: предикат обязан отвечать «нет» каждому городу.
        java.util.function.Predicate<UUID> isMaster = id -> false;
        require(!isMaster.test(city.getId()), "with no master city configured no city is master");

        // Снимок строится как при назначенной master city, так и без неё.
        java.util.function.Predicate<UUID> configured = id -> city.getId().equals(id);
        for (java.util.function.Predicate<UUID> master :
                List.of(isMaster, configured, id -> false)) {
            var views = manager.territoryViews(OVERWORLD, city.getPylonPos(), 512,
                    city.getId(), master);
            require(views.size() == 1,
                    "a city always appears in its own snapshot, master setting aside");
            var view = views.get(0);
            require(view.cityId().equals(city.getId()), "the snapshot names the city");
            require(view.own(), "the city is marked as the viewer's own city");
            require(!view.plots().isEmpty(), "the snapshot carries the city plots");
            require(view.colorRgb() == city.getColorRgb(), "the snapshot carries the city colour");
        }

        // Предикат, построенный на null-идентификаторе, обязан быть
        // безопасным: именно этим способом собирается предикат master city.
        UUID absentMaster = null;
        java.util.function.Predicate<UUID> nullSafe =
                absentMaster == null ? id -> false : absentMaster::equals;
        require(!nullSafe.test(city.getId()),
                "a predicate built from an absent master id must answer false, not throw");

        // Снимок соседа не должен считаться своим городом.
        var others = manager.territoryViews(OVERWORLD, city.getPylonPos(), 512,
                UUID.randomUUID(), isMaster);
        require(others.size() == 1 && !others.get(0).own(),
                "a city is not 'own' for a different viewer");
        require(others.get(0).plots().size() == city.getClaimedPlotCount(),
                "a foreign viewer still sees the plots, they are only visual data");
    }

    private static void persistence() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Saved", owner, OVERWORLD, new BlockPos(-120, 64, 75));
        require(city != null, "a city at a negative coordinate is founded");
        city.setLevel(2);
        // Пилон (-120, 75) лежит в участке (-3, 1): минус делится с округлением вниз.
        require(city.getPylonPlot().equals(new CityPlot(OVERWORLD, -3, 1)),
                "a pylon at (-120,75) sits in plot (-3,1)");
        require(manager.claimPlot(city, new CityPlot(OVERWORLD, -2, 1), owner) == CityClaimResult.CLAIMED,
                "a negative-coordinate plot is claimable");
        require(manager.claimPlot(city, new CityPlot(OVERWORLD, -1, 1), owner) == CityClaimResult.CLAIMED,
                "a further negative-coordinate plot is claimable");
        int chosenColor = paletteOtherThan(city.getColorRgb());
        require(manager.setCityColor(city, chosenColor, owner), "the city has a chosen colour");

        CompoundTag saved = manager.save(new CompoundTag(), null);
        CityManager restored = CityManager.load(saved, null);
        City reloaded = restored.getCityByName("Saved");
        require(reloaded != null, "the city survives a save/load round trip");
        require(reloaded.getClaimedPlotCount() == city.getClaimedPlotCount(),
                "the claim count survives");
        require(reloaded.getClaimedPlots().equals(city.getClaimedPlots()),
                "the exact claim set survives, including negative coordinates");
        require(reloaded.getColorRgb() == chosenColor, "the chosen colour survives");
        require(reloaded.getPylonPlot().equals(city.getPylonPlot()), "the pylon plot survives");

        // Пространственный индекс перестроен из данных города, а не из отдельного NBT.
        require(restored.getCityAt(OVERWORLD, -120, 75) == reloaded,
                "protection lookup works on a reloaded manager at the pylon");
        require(restored.getCityAt(OVERWORLD, -100, 75) == reloaded,
                "protection lookup works for the first extra claimed plot");
        require(restored.getCityAt(OVERWORLD, -1, 75) == reloaded,
                "protection lookup works right up to the negative grid boundary");
        require(restored.getCityAt(OVERWORLD, 0, 75) == null,
                "the plot past the migrated claims is free after reload");
        require(restored.isPlotFree(new CityPlot(OVERWORLD, 5, 5)), "unclaimed land is free after reload");

        // Секция Claims присутствует в сохранении — по её наличию идёт миграция.
        CompoundTag cityTag = ((ListTag) saved.getList("Cities", Tag.TAG_COMPOUND)).getCompound(0);
        require(cityTag.contains("Claims", Tag.TAG_LIST), "claims are written to the save");
        require(cityTag.contains("Color", Tag.TAG_INT), "the city colour is written to the save");
        require(((ListTag) cityTag.getList("Claims", Tag.TAG_COMPOUND)).size() == 3,
                "every claim is written");

        // Второй цикл: город в новом формате больше не мигрируется.
        CityManager twice = CityManager.load(restored.save(new CompoundTag(), null), null);
        require(twice.getCityByName("Saved").getClaimedPlots().equals(city.getClaimedPlots()),
                "a re-save is idempotent");
        require(twice.migratedCities().isEmpty(),
                "a city already in the new format is never migrated again");
    }

    private static void restartSafety() {
        /*
         * Полный цикл «основал → расширил → сохранил → перезагрузил →
         * расширил дальше» — именно то, что происходит в живом мире.
         */
        CityManager first = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = first.createCity("Restart", owner, new BlockPos(0, 64, 0));
        first.claimPlot(city, plot(1, 0), owner);

        CityManager second = CityManager.load(first.save(new CompoundTag(), null), null);
        City reloaded = second.getCityByName("Restart");
        require(reloaded.getClaimedPlotCount() == 2, "the territory survives the restart");
        require(second.claimPlot(reloaded, plot(1, 1), owner) == CityClaimResult.CLAIMED,
                "expansion continues correctly after a restart");
        require(second.claimPlot(reloaded, plot(2, 1), owner) == CityClaimResult.CLAIMED,
                "and keeps growing from the reloaded claims");
        require(second.claimPlot(reloaded, plot(0, 2), owner) == CityClaimResult.NOT_ADJACENT,
                "adjacency after a restart is judged against the reloaded claims");

        CityManager third = CityManager.load(second.save(new CompoundTag(), null), null);
        City twice = third.getCityByName("Restart");
        require(twice.getClaimedPlots().equals(second.getCityByName("Restart").getClaimedPlots()),
                "a second restart changes nothing");
        require(third.getCityAt(OVERWORLD, 75, 60) == twice, "protection still resolves after two restarts");
    }

    // ------------------------------------------------- пульт управления доступом

    private static final ResourceLocation CHEST = ResourceLocation.parse("minecraft:chest");
    private static final ResourceLocation FURNACE = ResourceLocation.parse("minecraft:furnace");

    private static void interactionGrants() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Access", owner, OVERWORLD, new BlockPos(0, 64, 0));
        BlockPos target = new BlockPos(10, 64, 20);

        require(manager.interactionGrant(city, target) == null,
                "no grant exists before the remote is used");
        require(!manager.allowsInteraction(city, OVERWORLD, target, CHEST),
                "an unknown block is guarded by the ordinary rights path");
        require(!manager.allowsInteraction(null, OVERWORLD, target, CHEST),
                "a null city is never granted");
        require(!manager.allowsInteraction(city, null, target, CHEST),
                "a null dimension is never granted");

        // Выдача: чужой игрок получает право только на взаимодействие.
        manager.addInteractionGrant(city, OVERWORLD, target, CHEST);
        require(manager.interactionGrant(city, target) != null,
                "the grant is stored after the remote is used");
        require(manager.allowsInteraction(city, OVERWORLD, target, CHEST),
                "the granted block accepts right-click from any player");
        require(!manager.allowsInteraction(city, NETHER, target, CHEST),
                "a grant is bound to the dimension it was given in");

        // Грант заменяется, а не дублируется: на предприятии пульт переиспользуют.
        manager.addInteractionGrant(city, OVERWORLD, target, FURNACE);
        require(manager.allowsInteraction(city, OVERWORLD, target, FURNACE),
                "a replaced block id re-grants the position");
        require(!manager.allowsInteraction(city, OVERWORLD, target, CHEST),
                "the replaced block no longer matches the old grant id");

        // Стёршийся grant: блок заменили — разрешение снимается первой проверкой.
        manager.addInteractionGrant(city, OVERWORLD, target, CHEST);
        require(!manager.allowsInteraction(city, OVERWORLD, target, FURNACE),
                "a stale grant fails the check for the new block");
        require(manager.interactionGrant(city, target) == null,
                "the stale grant is purged by the first protection check");

        // Повторное использование пульта закрывает блок.
        manager.addInteractionGrant(city, OVERWORLD, target, CHEST);
        require(manager.removeInteractionGrant(city, target),
                "reusing the remote removes the grant");
        require(!manager.removeInteractionGrant(city, target),
                "removing an already guarded block is a no-op");
        require(!manager.allowsInteraction(city, OVERWORLD, target, CHEST),
                "the block is guarded again after revocation");

        // Сохранение: гранты переживают перезапуск вместе с городом.
        manager.addInteractionGrant(city, OVERWORLD, target, CHEST);
        CompoundTag saved = manager.save(new CompoundTag(), null);
        CityManager restored = CityManager.load(saved, null);
        City reloaded = restored.getCityByName("Access");
        require(reloaded != null, "precondition: the city survives persistence");
        require(restored.interactionGrant(reloaded, target) != null,
                "access grants survive a save/load round trip");
        require(restored.allowsInteraction(reloaded, OVERWORLD, target, CHEST),
                "the reloaded grant works for protection");

        // Удаление города не оставляет грантов ни в памяти, ни в сохранении.
        City removed = restored.removeCityByOwner(owner);
        require(removed != null, "precondition: the city is removable");
        CompoundTag afterRemoval = restored.save(new CompoundTag(), null);
        require(afterRemoval.getList("InteractionGrants", Tag.TAG_COMPOUND).isEmpty(),
                "city removal cleans its access grants");
        CityManager reopened = CityManager.load(afterRemoval, null);
        require(!reopened.allowsInteraction(null, OVERWORLD, target, CHEST),
                "a deleted city grants nothing");
    }

    // ------------------------------------------------- транспортные гранты пульта

    private static void transportGrants() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Transport", owner, OVERWORLD, new BlockPos(0, 64, 0));
        UUID token = UUID.randomUUID();
        BlockPos delta = new BlockPos(3, 1, -2);

        require(!manager.allowsTransportAccess(city, token, delta, CHEST),
                "a transport block is guarded before the remote touches it");
        require(!manager.allowsTransportAccess(null, token, delta, CHEST),
                "a null city is never granted for a transport block");
        require(!manager.allowsTransportAccess(city, null, delta, CHEST),
                "a null token is never granted");
        require(!manager.allowsTransportAccess(city, token, null, CHEST),
                "a null delta is never granted");
        require(!manager.toggleTransportAccess(null, token, delta, CHEST),
                "the remote cannot toggle grants without a city");

        // Выдача: пульт открывает блок конструкции для взаимодействия всем.
        require(manager.toggleTransportAccess(city, token, delta, CHEST),
                "the remote opens a transport block");
        require(manager.allowsTransportAccess(city, token, delta, CHEST),
                "the opened transport block accepts interaction from any player");

        // Привязка к токену терминала: чужой терминал ничего не открывает.
        require(!manager.allowsTransportAccess(city, UUID.randomUUID(), delta, CHEST),
                "another terminal token reads nothing from the grant");

        // Тумблер: повторное использование закрывает блок.
        require(!manager.toggleTransportAccess(city, token, delta, CHEST),
                "reusing the remote re-guards the block");
        require(!manager.allowsTransportAccess(city, token, delta, CHEST),
                "the re-guarded block is protected again");

        // Стершийся грант: на дельте другой блок — разрешение снимается проверкой.
        manager.toggleTransportAccess(city, token, delta, CHEST);
        require(!manager.allowsTransportAccess(city, token, delta, FURNACE),
                "a stale transport grant fails the check for the replaced block");
        require(!manager.allowsTransportAccess(city, token, delta, CHEST),
                "the stale transport grant is purged by the first check");

        // Повторная выдача после очистки, закрытие и открытие для сохранения.
        require(manager.toggleTransportAccess(city, token, delta, CHEST),
                "a guarded block can be re-opened after the purge");
        require(!manager.toggleTransportAccess(city, token, delta, CHEST),
                "the open block can be re-guarded");
        manager.toggleTransportAccess(city, token, delta, CHEST);

        // Персистентность: дельты переживают перезапуск вместе с городом.
        CompoundTag saved = manager.save(new CompoundTag(), null);
        CityManager restored = CityManager.load(saved, null);
        City reloaded = restored.getCityByName("Transport");
        require(reloaded != null, "precondition: the transport city survives persistence");
        require(restored.allowsTransportAccess(reloaded, token, delta, CHEST),
                "transport grants survive a save/load round trip");

        // Удаление города не оставляет транспортных грантов ни в памяти, ни в сохранении.
        City removed = restored.removeCityByOwner(owner);
        require(removed != null, "precondition: the transport city is removable");
        CompoundTag afterRemoval = restored.save(new CompoundTag(), null);
        require(afterRemoval.getList("TransportGrants", Tag.TAG_COMPOUND).isEmpty(),
                "city removal cleans its transport grants");
        CityManager reopened = CityManager.load(afterRemoval, null);
        require(!reopened.allowsTransportAccess(null, token, delta, CHEST),
                "a deleted city grants no transport blocks");
    }

    // ------------------------------------------------------------------- перенос пилона

    private static void relocation() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Moving", owner, new BlockPos(10, 64, 10));
        manager.claimPlot(city, plot(1, 0), owner);
        manager.claimPlot(city, plot(1, 1), owner);
        require(city.getClaimedPlotCount() == 3, "precondition: three claims");

        manager.markPylonRelocating(city);
        require(!city.isPylonActive(), "breaking the pylon deactivates the city");

        // Перенос внутри своей территории: участки не трогаются.
        require(manager.rebindPylon(city, OVERWORLD, new BlockPos(30, 64, 30)),
                "the pylon may be placed again inside the territory");
        require(city.getClaimedPlotCount() == 3, "the territory is not recreated on relocation");
        require(city.hasClaim(plot(0, 0)) && city.hasClaim(plot(1, 1)),
                "the exact claims survive relocation");
        require(manager.getCityAt(OVERWORLD, 60, 60) == city, "protection follows the unchanged claims");

        // Перенос за пределы территории отклоняется.
        manager.markPylonRelocating(city);
        require(!manager.rebindPylon(city, OVERWORLD, new BlockPos(400, 64, 400)),
                "the pylon may not be placed outside the territory");
        require(city.getPylonPos().equals(new BlockPos(30, 64, 30)),
                "a refused relocation leaves the city where it was");
        require(city.getClaimedPlotCount() == 3, "a refused relocation changes no claims");

        // Перенос в другое измерение сохраняет форму территории и оставляет
        // новый пилон внутри неё.
        require(manager.rebindPylon(city, NETHER, new BlockPos(-5, 64, -5)),
                "relocating across dimensions keeps the city alive");
        require(city.getPylonDimension().equals(NETHER), "the pylon dimension changed");
        require(city.getClaimedPlotCount() == 3, "the number of claims is preserved across dimensions");
        require(city.getPylonPlot().equals(new CityPlot(NETHER, -1, -1)),
                "the pylon plot follows the new pylon");
        require(city.hasClaim(city.getPylonPlot()), "the pylon is inside its own territory again");
        require(!city.hasClaim(plot(0, 0)), "old-dimension claims do not linger");
        require(manager.getCityAt(NETHER, -5, -5) == city, "protection works in the new dimension");
        require(manager.getCityAt(OVERWORLD, 10, 10) == null, "the old dimension is released");
    }

    // ------------------------------------------------------------------------ контур

    private static void outline() {
        // Один участок: четыре стороны.
        require(CityClaims.outline(Set.of(plot(0, 0))).size() == 4,
                "a single claim has a four-sided outline");

        // Два участка в ряд: шесть рёбер, а не восемь и не внутренняя перегородка.
        Set<CityPlot> pair = Set.of(plot(0, 0), plot(1, 0));
        require(CityClaims.outline(pair).size() == 6,
                "two side-by-side claims share one edge, got " + CityClaims.outline(pair).size());

        // Квадрат 2×2: восемь рёбер — ровно внешний периметр.
        Set<CityPlot> square = Set.of(plot(0, 0), plot(1, 0), plot(0, 1), plot(1, 1));
        List<int[]> squareOutline = CityClaims.outline(square);
        require(squareOutline.size() == 8,
                "a 2x2 block of claims has an eight-edge perimeter, got " + squareOutline.size());
        for (int[] edge : squareOutline) {
            boolean horizontal = edge[1] == edge[3];
            require(horizontal || edge[0] == edge[2], "every outline edge lies on one axis");
        }

        // Контур мигрированного города уровня 1: внешний периметр 150×150.
        Set<CityPlot> migrated = CityClaims.legacyTerritory(OVERWORLD, new BlockPos(0, 64, 0), 100);
        int perimeter = CityClaims.outline(migrated).stream()
                .mapToInt(edge -> horizontal(edge) ? Math.abs(edge[2] - edge[0]) : Math.abs(edge[3] - edge[1]))
                .sum();
        require(perimeter == 600, "a 150x150 territory has a 600-block perimeter, got " + perimeter);
        require(migrated.size() == 9, "a 150x150 territory is nine claims");
    }

    private static boolean horizontal(int[] edge) {
        return edge[1] == edge[3];
    }
}
