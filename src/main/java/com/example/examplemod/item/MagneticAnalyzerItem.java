package com.example.examplemod.item;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.geology.GeologySurvey;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.network.GeologyWideScanPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Магнитный анализатор.
 *
 * Ручной сканер. Поэтому он обязан реагировать на ПКМ в воздухе
 * через {@link #use(Level, Player, InteractionHand)}, а ПКМ по
 * блоку дополнительно обрабатывает через
 * {@link #useOn(UseOnContext)}. Раньше был реализован только
 * useOn, поэтому ПКМ в воздухе уходил в базовый
 * {@code Item.use} и возвращал PASS: прибор не реагировал.
 *
 * Один предмет с двумя режимами поведения:
 *
 *  - вне месторождения прибор находит ближайшую промышленную
 *    аномалию в пределах 200 блоков от её границы и сообщает
 *    ресурс, направление и грубую дистанцию;
 *  - внутри месторождения прибор показывает локальный
 *    production potential, оценочный остаток запаса и класс
 *    размера.
 *
 * Координаты ядра не раскрываются ни в одном режиме: игрок
 * находит максимум перемещением, сравнивая показания.
 *
 * Расходуемых реагентов нет. Долговечность предмета сохранена
 * как была: замер стоит одну единицу. Дополнительно действует
 * небольшой перезаряд, чтобы замер нельзя было спамить.
 */
public class MagneticAnalyzerItem extends Item {

    /** Перезаряд прибора, в тиках. */
    public static final int COOLDOWN_TICKS = 60;

    /**
     * Радиус широкого спектра: контуры залежей доставляются на
     * глобальную карту игрока. Узкий спектр как был прицельным
     * сканом с {@link GeologySurvey#SCAN_RADIUS_BLOCKS}, так и
     * остаётся — широкой спектр не меняет его баланс.
     */
    public static final int WIDE_SCAN_RADIUS_BLOCKS = 1000;

    /**
     * Предел контуров в одном пакете. Залежи вычёркиваются
     * с конца списка находок (клиент увидит «N из M»), а не
     * молча переполняют буфер.
     */
    public static final int MAX_WIDE_CONTOURS = 256;

    /**
     * Перезаряд хранится в persistent data игрока, а не в
     * статической таблице: состояние переживает реконнект
     * и не создаёт утечки памяти на выходе игрока.
     *
     * Отсутствие ключа означает «никогда не сканировал», то есть
     * первый замер проходит безусловно.
     */
    private static final String LAST_SCAN_KEY = "magnetic_analyzer_last_scan";

    public MagneticAnalyzerItem(Properties properties) {
        super(properties);
    }

    /**
     * ПКМ по блоку. Замер идёт по позиции игрока, а не по блоку:
     * прибор считывает магнитное поле оператора, а не конкретного
     * блока. Блок при этом не ломается: возвращается SUCCESS,
     * а не CONSUME, и стандартное взаимодействие с блоком не
     * блокируется.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        /*
         * Shift+ПКМ — смена спектра: и по воздуху, и по блоку
         * переключение должно вести себя одинаково.
         */
        if (context.getPlayer().isShiftKeyDown()) {
            toggleSpectrum(player, context.getItemInHand());
            return InteractionResult.SUCCESS;
        }

        if (!performScan(player, context.getItemInHand())) {
            return InteractionResult.FAIL;
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * ПКМ в воздухе. Основной путь ручного сканера: без него
     * прибор не реагировал вообще, потому что ПКМ в воздухе
     * вызывает use(), а не useOn().
     */
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand usedHand
    ) {
        ItemStack stack = player.getItemInHand(usedHand);

        /*
         * Shift+ПКМ — смена спектра. Замер при этом не
         * выполняется: заряд и перезаряд не списываются.
         */
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                toggleSpectrum(serverPlayer, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (player instanceof ServerPlayer serverPlayer) {
            boolean scanned = performScan(serverPlayer, stack);
            if (!scanned) {
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Общий замер для обоих путей взаимодействия.
     *
     * @return true, если замер выполнен и пейлоад отправлен.
     */
    private boolean performScan(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();

        if (!GeologyManager.get(level).isStorageAvailable()) {
            return false;
        }

        if (!canScan(player, level.getGameTime())) {
            return false;
        }

        /*
         * Замер идёт по позиции игрока, а не по блоку, по
         * которому он кликнул: прибор считывает магнитное
         * поле оператора, а не конкретного блока.
         */
        BlockPos origin = player.blockPosition();

        /*
         * Широкий спектр — обзорная ветка: она делит с узким
         * спектром перезаряд, прочность и проверку хранилища,
         * но собирает контуры вместо прицельного замера.
         */
        if (spectrum(stack) == MagneticAnalyzerMode.WIDE) {
            return performWideScan(player, stack, level, origin);
        }

        /*
         * Анализатору нужен NEARBY-поиск, а не поиск «что под
         * точкой»: игрок чаще всего стоит ещё вне залежи, и
         * contains-only список был бы пустым ровно тогда,
         * когда прибору нужны данные. Режим NEAREST ANOMALY
         * из-за этого был недостижим.
         */
        List<GeologicalDeposit> candidates = new ArrayList<>(
                GeologyManager.get(level).queryNearby(
                        origin,
                        GeologySurvey.SCAN_RADIUS_BLOCKS
                )
        );

        /*
         * Прочность списывается только после того, как
         * подтверждена доступность геологии и снят перезаряд:
         * неудачная попытка не должна стоить игроку заряд.
         */
        stack.hurtAndBreak(
                1,
                player,
                player.getEquipmentSlotForItem(stack)
        );

        int remainingUses = Math.max(
                0,
                stack.getMaxDamage() - stack.getDamageValue()
        );

        GeologySurvey survey = GeologySurvey.of(origin, candidates, remainingUses);
        player.getPersistentData().putLong(LAST_SCAN_KEY, level.getGameTime());

        player.connection.send(GeologyAnalysisPayload.of(survey));
        return true;
    }

    private static boolean canScan(ServerPlayer player, long gameTime) {
        var data = player.getPersistentData();
        if (!data.contains(LAST_SCAN_KEY, Tag.TAG_LONG)) {
            return true;
        }
        long last = data.getLong(LAST_SCAN_KEY);
        return gameTime - last >= COOLDOWN_TICKS;
    }

    /**
     * Текущий спектр замера. Отсутствие компонента — узкий
     * спектр: это оптимизация по умолчанию и историческое
     * поведение прибора.
     */
    public static MagneticAnalyzerMode spectrum(ItemStack stack) {
        MagneticAnalyzerMode mode =
                stack.get(CreateCityExpansionCore.ANALYZER_MODE.get());
        return mode == null ? MagneticAnalyzerMode.NARROW : mode;
    }

    /**
     * Shift+ПКМ: смена спектра. Составляет пару с
     * {@link MagneticAnalyzerMode#next()}; заряд и перезаряд
     * не расходуются — переключение не является замером.
     */
    private static void toggleSpectrum(
            ServerPlayer player,
            ItemStack stack
    ) {
        MagneticAnalyzerMode next = spectrum(stack).next();
        stack.set(CreateCityExpansionCore.ANALYZER_MODE.get(), next);

        /*
         * Action bar, не чат: смена режима не должна заваливать
         * журнал во время активной работы с прибором.
         */
        player.displayClientMessage(
                Component.translatable(
                        "gui.createcityexpansioncore.magnetic_analyzer.spectrum.switched",
                        next.label()
                ),
                true
        );
    }

    /**
     * Широкий спектр: обзорный замер. Все залежи в радиусе
     * {@link #WIDE_SCAN_RADIUS_BLOCKS} уходят на карту игрока
     * контурами; прицельная разведка остаётся задачей узкого
     * спектра.
     *
     * <p>Максимум с плавной координатой здесь не раскрывается —
     * раскрывается сам абрис залежей: поиск «самого богатого
     * участка» по-прежнему через узкий спектр на месте.
     */
    private boolean performWideScan(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos origin
    ) {
        /*
         * Nearby-запрос с тем же фильтром по границе, что и у
         * узкого спектра: залежь попадает в выборку, когда до
         * неё не дальше радиуса сканирования.
         */
        List<GeologicalDeposit> deposits = new ArrayList<>(
                GeologyManager.get(level).queryNearby(
                        origin,
                        WIDE_SCAN_RADIUS_BLOCKS
                )
        );

        GeologyWideScanPayload payload = GeologyWideScanPayload.of(
                level,
                origin,
                deposits,
                MAX_WIDE_CONTOURS
        );
        player.connection.send(payload);

        MutableComponent report = Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.wide_scan.found",
                payload.contours().size(),
                payload.foundTotal()
        );
        if (payload.truncated()) {
            report = report.append(" ").append(Component.translatable(
                    "gui.createcityexpansioncore.magnetic_analyzer.wide_scan.truncated"
            ));
        }
        player.displayClientMessage(report, true);

        player.getPersistentData().putLong(LAST_SCAN_KEY, level.getGameTime());
        return true;
    }

    /**
     * Тултип прибора: текущий спектр и способ его смены.
     */
    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext tooltipContext,
            List<Component> tooltip,
            TooltipFlag tooltipFlag
    ) {
        tooltip.add(Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.spectrum.tooltip",
                spectrum(stack).label()
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.spectrum.hint"
        ).withStyle(ChatFormatting.DARK_GRAY));
    }
}