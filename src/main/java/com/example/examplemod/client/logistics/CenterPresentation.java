package com.example.examplemod.client.logistics;

import java.util.Set;

/** Pure UI policy. A lost mutation receipt is never permission to send a second mutation. */
public final class CenterPresentation {
    public static final long RESPONSE_TIMEOUT_MS = 12_000;
    private static final Set<String> PROBLEMS = Set.of("MISSING", "BLOCKED", "UNLOADED", "FAILED",
        "DISABLED", "WAITING_POINT", "WAITING_ROUTE", "WAITING_TRAIN", "DEBT", "ARREARS", "UNCERTAIN", "RECONCILE", "EXPIRED", "UNRESOLVED", "OWNERSHIP_CHANGED", "NOT_READY", "QUARANTINED");
    private CenterPresentation() {}
    public static boolean problem(String status) { return PROBLEMS.contains(status); }
    public static boolean timedOut(long sent, long now) { return now - sent >= RESPONSE_TIMEOUT_MS; }
    public static boolean validNumber(String key, String value) {
        try {
            if (value == null || !value.matches("[0-9]{1,19}")) return false;
            long number = Long.parseLong(value);
            return number >= (Set.of("Tariff","Rate","DispatchFee","Salary","Tax","TaxBps","Budget","Minimum","Target","Maximum","ImportQuota","ExportQuota").contains(key) ? 0 : 1);
        } catch (NumberFormatException ignored) { return false; }
    }
    public static boolean validNumber(net.minecraft.nbt.CompoundTag field,String value){
        if(value==null||!value.matches("[0-9]{1,19}"))return false;
        try{long n=Long.parseLong(value);boolean valid=field.contains("Min")?n>=field.getLong("Min"):field.getString("Source").equals("0")?n>=0:validNumber(field.getString("Key"),value);return valid&&(!field.contains("Max")||n<=field.getLong("Max"));}
        catch(NumberFormatException ignored){return false;}
    }
    public static boolean validPercent(String value){try{percentBasisPoints(value);return true;}catch(IllegalArgumentException|ArithmeticException ignored){return false;}}
    public static long percentBasisPoints(String value){
        if(value==null||!value.matches("[0-9]{1,3}(?:[.,][0-9]{1,2})?"))throw new IllegalArgumentException("percent");
        long basisPoints=new java.math.BigDecimal(value.replace(',','.')).movePointRight(2).longValueExact();
        if(basisPoints<0||basisPoints>10000)throw new IllegalArgumentException("percent");return basisPoints;
    }
    public static int scrollLimit(int content, int viewport) { return Math.max(0, content - Math.max(0, viewport)); }
    public static float ease(long elapsed, long duration) {
        float t = Math.max(0, Math.min(1, elapsed / (float)Math.max(1, duration)));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
