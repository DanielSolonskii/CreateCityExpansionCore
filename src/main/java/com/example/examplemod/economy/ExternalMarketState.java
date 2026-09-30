package com.example.examplemod.economy;

public final class ExternalMarketState {
    private long demand;
    private long supply;
    private long lastRecoveryTick;
    public ExternalMarketState(long demand,long supply,long lastRecoveryTick){this.demand=Math.max(0,demand);this.supply=Math.max(0,supply);this.lastRecoveryTick=Math.max(0,lastRecoveryTick);}
    public long demand(){return demand;} public long supply(){return supply;} public long lastRecoveryTick(){return lastRecoveryTick;}
    public void recover(MarketDefinition definition,long now){if(lastRecoveryTick<=0){lastRecoveryTick=now;return;}long elapsed=Math.max(0,now-lastRecoveryTick);if(elapsed<=0||definition.recoveryPerHour()<=0)return;long add=java.math.BigInteger.valueOf(elapsed).multiply(java.math.BigInteger.valueOf(definition.recoveryPerHour())).divide(java.math.BigInteger.valueOf(72_000)).longValue();if(add<=0)return;demand=Math.min(definition.targetDemand(),EconomyMath.saturatingAdd(demand,add,Long.MAX_VALUE));supply=Math.min(definition.targetSupply(),EconomyMath.saturatingAdd(supply,add,Long.MAX_VALUE));lastRecoveryTick=now;}
    public boolean reserve(EconomyTypes.MarketDirection direction,long amount){if(amount<=0)return false;if(direction==EconomyTypes.MarketDirection.SELL_TO_MASTER){if(demand<amount)return false;demand-=amount;}else{if(supply<amount)return false;supply-=amount;}return true;}
    public void release(EconomyTypes.MarketDirection direction,long amount,MarketDefinition d){if(amount<=0)return;if(direction==EconomyTypes.MarketDirection.SELL_TO_MASTER)demand=Math.min(d.targetDemand(),EconomyMath.saturatingAdd(demand,amount,Long.MAX_VALUE));else supply=Math.min(d.targetSupply(),EconomyMath.saturatingAdd(supply,amount,Long.MAX_VALUE));}
}
