package com.example.examplemod.daily;

/** Calendar progression only. A reserved reward counts for its server date, even while awaiting space. */
public record DailyRewardProgress(long lastDate,int day,int starterMask,boolean lastStarter,int runUniqueMask) {
    public static final long NEVER=Long.MIN_VALUE;
    public DailyRewardProgress{if(day<0||day>7||starterMask<0||starterMask>127||runUniqueMask<0||runUniqueMask>127||(lastDate==NEVER)!=(day==0))throw new IllegalArgumentException("Daily progression");}
    public static DailyRewardProgress fresh(){return new DailyRewardProgress(NEVER,0,0,true,0);}
    public boolean completedStarter(){return starterMask==127;}
    public boolean claimed(long today){return lastDate!=NEVER&&lastDate>=today;}
    public int nextDay(long today){if(claimed(today))return day;return lastDate==today-1&&day<7?day+1:1;}
    public boolean starterCycle(long today){return claimed(today)?lastStarter:!completedStarter();}
    public boolean unique(int rewardDay){return !completedStarter()&&(starterMask&(1<<(rewardDay-1)))==0;}
    public DailyRewardProgress reserve(long today){if(claimed(today))throw new IllegalStateException("Already claimed");int next=nextDay(today);boolean starter=!completedStarter();int run=next==1?0:runUniqueMask;if(unique(next))run|=1<<(next-1);return new DailyRewardProgress(today,next,starterMask|(starter?1<<(next-1):0),starter,run);}
}
