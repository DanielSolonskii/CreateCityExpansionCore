package com.example.examplemod.policy;

import com.example.examplemod.policy.CityMapReadAccess.Decision;
import com.example.examplemod.policy.CityMapReadAccess.Facts;

/**
 * Доступ к карте города по клавише — только чтение и только участнику.
 *
 * <p><b>Что здесь защищается.</b> Карта по клавише не привязана к пилону, поэтому
 * у неё нет ни открытого контейнера, ни проверки расстояния, ни блока под ногами.
 * Единственное, что остаётся между посторонним и участником, — членство. Если
 * эта проверка ошибётся, карта города утекает к любому, кто нажал клавишу, и
 * никакая последующая проверка это уже не остановит: данные уйдут в первый же
 * пакет.
 *
 * <p><b>Почему факты, а не моки.</b> Решение принимается чистой функцией от двух
 * фактов, поэтому проверяется перебором и не зависит ни от сервера, ни от сети.
 */
public final class CityMapReadAccessCheck {

    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        memberOfCityMayRead();
        cityWithoutMemberIsRefused();
        noCityIsRefused();
        decisionIsNeverPartlyAllowed();
        refusalCoversEveryCombination();
        nullFactsAreRefused();

        System.out.println("PASS: " + checks + " city map read access checks");
    }

    /** Участник города получает карту — и только на чтение. */
    private static void memberOfCityMayRead() {
        check(CityMapReadAccess.evaluate(new Facts(true, true)) == Decision.READ_ONLY,
                "a member of a city may open the map");
        check(CityMapReadAccess.allowed(new Facts(true, true)),
                "memberOfCityMayRead: allowed() must agree with evaluate()");
    }

    /**
     * Город есть, но игрок в него не входит — отказ.
     *
     * <p>Это ровно тот случай, из-за которого проверка «город существует»
     * недостаточна: пилон чужого города виден всем, кто подошёл.
     */
    private static void cityWithoutMemberIsRefused() {
        check(CityMapReadAccess.evaluate(new Facts(true, false)) == Decision.REFUSED,
                "existing city without membership is refused");
        check(!CityMapReadAccess.allowed(new Facts(true, false)),
                "cityWithoutMemberIsRefused: allowed() must agree");
    }

    /** Нет своего города — отказ. */
    private static void noCityIsRefused() {
        check(CityMapReadAccess.evaluate(new Facts(false, false)) == Decision.REFUSED,
                "a player without a city is refused");
        check(CityMapReadAccess.evaluate(new Facts(false, true)) == Decision.REFUSED,
                "membership without a city is refused");
    }

    /**
     * Решения ровно два: читать можно, всё остальное — нет.
     *
     * <p>Появление третьего состояния (например, «видно, но нельзя ничего
     * нажать») означало бы, что появился и путь, который это состояние
     * обслуживает, — а правом является только членство.
     */
    private static void decisionIsNeverPartlyAllowed() {
        check(Decision.values().length == 2,
                "there must be exactly two decisions, got " + Decision.values().length);
    }

    /** Перебор всех сочетаний: разрешён ровно один. */
    private static void refusalCoversEveryCombination() {
        boolean[] hasCity = {false, true};
        boolean[] member = {false, true};
        int allowed = 0;
        for (boolean city : hasCity) {
            for (boolean inCity : member) {
                if (CityMapReadAccess.allowed(new Facts(city, inCity))) {
                    allowed++;
                    check(city && inCity,
                            "only city+member may be allowed, got city=" + city + " member=" + inCity);
                }
            }
        }
        check(allowed == 1,
                "exactly one of four combinations is allowed, got " + allowed);
    }

    /**
     * Отсутствие фактов — отказ, а не исключение.
     *
     * <p>Факты приходят с сервера, и «фактов нет» означает «данных не
     * пришло». Исключение здесь уронило бы разбор пакета, а тихий отказ ничего.
     */
    private static void nullFactsAreRefused() {
        check(CityMapReadAccess.evaluate(null) == Decision.REFUSED,
                "absent facts must be refused, not throw");
    }
}