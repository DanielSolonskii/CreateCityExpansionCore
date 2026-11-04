package com.example.examplemod.cargo;

/** Conservative postcondition only; never moves items and never trusts an unpacker's boolean alone. */
final class CargoUnpackingEvidence {
    private CargoUnpackingEvidence() {}
    static boolean complete(boolean simulated, boolean nativeResult, long[] expected, long[] before, long[] after) {
        if (simulated || !nativeResult || expected.length == 0 || expected.length != before.length || before.length != after.length)
            return false;
        for (int i = 0; i < expected.length; i++)
            if (expected[i] <= 0 || before[i] < 0 || after[i] < before[i] || after[i] - before[i] != expected[i]) return false;
        return true;
    }
}
