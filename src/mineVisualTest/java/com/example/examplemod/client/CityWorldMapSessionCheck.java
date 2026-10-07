package com.example.examplemod.client;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Привязка данных карты по клавише к пилону города.
 *
 * <p><b>Что здесь ломалось и почему это не видно.</b> Пилон города клиент узнать не
 * может: сервер определяет его по членству игрока. Значит сессия вынуждена принять
 * первый пакет как якорь. Якорь — это доверие: кто прислал первый пакет, тот
 * определил, чья это карта. Ошибка здесь не выглядит ошибкой — она выглядит как
 * «карта показала не тот город», и заметить её можно только по логу.
 *
 * <p><b>Что проверяется.</b> Первый пакет задаёт якорь, дальше сверка строгая.
 * Строгость и есть защита: если бы сессия принимала любой пакет, то пакет,
 * задержавшийся от прошлого города, нарисовал бы чужие рельсы поверх текущих.
 *
 * <p>Проверка работает на копии правил, а не на самом классе: экран и Minecraft
 * в headless недоступны, а правило приёмки — это несколько строк, которые важнее
 * всего остального в этом файле.
 */
public final class CityWorldMapSessionCheck {

    private static int checks;

    /** Пилон города, который сервер назовёт первым пакетом. */
    private static final BlockPos OWN_PYLON = new BlockPos(120, 64, -80);

    /** Пилон чужого города: пакет от него обязан быть отброшен. */
    private static final BlockPos OTHER_PYLON = new BlockPos(-900, 64, 1200);

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /**
     * Правило приёмки ровно как в {@code CityWorldMapSession.accepts}, но без
     * Minecraft-типов и без статического состояния.
     */
    private static final class Anchor {
        private BlockPos pylon;
        private boolean awaiting;
        private int accepted;
        private int rejected;

        void await() {
            this.awaiting = true;
            this.pylon = null;
        }

        void clear() {
            this.awaiting = false;
            this.pylon = null;
            this.accepted = 0;
            this.rejected = 0;
        }

        boolean accepts(BlockPos pos) {
            if (pos == null) {
                this.rejected++;
                return false;
            }
            if (this.pylon == null) {
                if (!this.awaiting) {
                    this.rejected++;
                    return false;
                }
                this.pylon = pos;
                this.awaiting = false;
                this.accepted++;
                return true;
            }
            if (this.pylon.equals(pos)) {
                this.accepted++;
                return true;
            }
            this.rejected++;
            return false;
        }
    }

    public static void main(String[] args) {
        firstPacketBindsAnchor();
        secondPacketOfSameCityAccepted();
        packetOfOtherCityRejected();
        unsolicitedPacketRejectedWithoutAwait();
        latePacketAfterCloseRejected();
        nullPositionRejected();
        stalePacketCannotRebindAnchor();
        denialIsDistinguishableFromLoading();

        System.out.println("PASS: " + checks + " city world map session checks");
    }

    /**
     * Первый пакет задаёт якорь: иначе сессия не знала бы, чей это город.
     *
     * <p>Раньше сессия отбрасывала всё, пока якорь не выставлен заранее, а выставить
     * его было нечем — пилон приходит из ответа. Экран висел бы в загрузке
     * навсегда.
     */
    private static void firstPacketBindsAnchor() {
        Anchor anchor = new Anchor();
        anchor.await();
        check(anchor.accepts(OWN_PYLON), "the first packet must bind the anchor");
        check(OWN_PYLON.equals(anchor.pylon), "the anchor is the first packet's pylon");
    }

    /** Свой город продолжает наполнять карту. */
    private static void secondPacketOfSameCityAccepted() {
        Anchor anchor = new Anchor();
        anchor.await();
        anchor.accepts(OWN_PYLON);
        check(anchor.accepts(OWN_PYLON), "further packets of the same city are accepted");
        check(anchor.accepts(OWN_PYLON), "and again");
        check(anchor.rejected == 0, "no packet of the own city was rejected");
    }

    /**
     * Пакет чужого города отбрасывается.
     *
     * <p>Главный случай защиты: без сверки задержавшийся пакет от прошлого города
     * нарисовал бы чужие рельсы поверх текущих, и игрок увидел бы карту, которой
     * нет.
     */
    private static void packetOfOtherCityRejected() {
        Anchor anchor = new Anchor();
        anchor.await();
        anchor.accepts(OWN_PYLON);
        check(!anchor.accepts(OTHER_PYLON), "a packet for another city must be rejected");
        check(OWN_PYLON.equals(anchor.pylon), "and it must not move the anchor");
    }

    /**
     * Без ожидания первый пакет тоже отбрасывается.
     *
     * <p>Это защита от «случайно начали принимать»: сессия не должна наполняться
     * данными города, карту которого никто не открывал.
     */
    private static void unsolicitedPacketRejectedWithoutAwait() {
        Anchor anchor = new Anchor();
        check(!anchor.accepts(OWN_PYLON),
                "a packet with no open request must not start a session");
        check(anchor.pylon == null, "and no anchor is created");
    }

    /** После закрытия карты пакеты прошлого города не должны наполнять её снова. */
    private static void latePacketAfterCloseRejected() {
        Anchor anchor = new Anchor();
        anchor.await();
        anchor.accepts(OWN_PYLON);
        anchor.clear();
        check(!anchor.accepts(OWN_PYLON), "a packet arriving after close must be rejected");
        check(anchor.pylon == null, "and must not restore the anchor");
    }

    /** Отсутствие позиции отбрасывается, а не роняет разбор пакета. */
    private static void nullPositionRejected() {
        Anchor anchor = new Anchor();
        anchor.await();
        check(!anchor.accepts(null), "a packet without a position must be rejected");
        check(anchor.pylon == null, "and must not bind an anchor");
        anchor.accepts(OWN_PYLON);
        check(!anchor.accepts(null), "a positionless packet is rejected once bound too");
    }

    /**
     * Пакет чужого города не становится новым якорем.
     *
     * <p>Отдельный случай потому, что «отброшен» и «стал якорем» — это разные
     * исходы одного и того же вызова. Проверка на отбрасывание без проверки якоря
     * пропустила бы подмену города целиком.
     */
    private static void stalePacketCannotRebindAnchor() {
        Anchor anchor = new Anchor();
        anchor.await();
        anchor.accepts(OWN_PYLON);
        for (int i = 0; i < 5; i++) {
            anchor.accepts(OTHER_PYLON);
        }
        check(OWN_PYLON.equals(anchor.pylon),
                "repeated foreign packets must not replace the city being shown");
        check(anchor.accepts(OWN_PYLON), "and the own city is still readable afterwards");
    }

    /**
     * «Города нет» — это ответ, а не «ещё грузится».
     *
     * <p>Сервер на запрос карты игроку без города раньше не отвечал ничем. Для
     * кли��нта молчание неотличимо от медленной загрузки, и экран оставался в
     * состоянии «загрузка территорий» бесконечно. Проверяется, что отказ помечается,
     * что новый запрос его снимает и что очистка сессии его снимает тоже: иначе
     * игрок, вступивший в город, нажал бы M и снова увидел бы отказ.
     */
    private static void denialIsDistinguishableFromLoading() {
        CityWorldMapSession session = CityWorldMapSession.INSTANCE;

        session.clear();
        session.awaitAnchor();
        check(!session.isDenied(), "a fresh request starts un-denied");
        check(!session.isReady(), "no pylon before the server names a city");

        session.deny();
        check(session.isDenied(), "a denial is recorded");
        check(!session.isReady(), "a denial never produces a pylon");

        session.awaitAnchor();
        check(!session.isDenied(), "re-requesting clears a stale denial");
        check(!session.isReady(), "re-requesting waits for a pylon again");

        session.deny();
        session.clear();
        check(!session.isDenied(), "clearing the session clears the denial");

        // A player who does have a city must not be denied by accident.
        session.awaitAnchor();
        session.begin(OWN_PYLON, net.minecraft.resources.ResourceLocation.parse(
                "minecraft:overworld"));
        check(!session.isDenied(), "a member's session is never denied");
        check(session.isReady(), "a member's session has a pylon");
        session.clear();
    }
}
