package com.example.examplemod.daily;
import com.example.examplemod.client.screen.*;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
/** Focused calendar/save/bounds checks. No new framework or live reward spawning. */
public final class DailyRewardsCheck {
    private static int checks;
    private static void require(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var p=DailyRewardProgress.fresh();require(p.nextDay(100)==1&&!p.claimed(100)&&p.unique(1),"fresh starter day one");
        p=p.reserve(100);require(p.day()==1&&p.claimed(100)&&!p.unique(1),"claim records date and unique day");
        boolean rejected=false;try{p.reserve(100);}catch(IllegalStateException e){rejected=true;}require(rejected,"duplicate same-day claim rejected");
        require(p.claimed(99),"backward calendar cannot award another reward");require(p.nextDay(101)==2,"consecutive calendar day advances");
        p=p.reserve(101);require(p.nextDay(103)==1,"skipped calendar day resets streak");require(!p.unique(1)&&!p.unique(2)&&p.unique(3),"unique starter flags survive a broken streak");
        p=p.reserve(103);require(p.runUniqueMask()==0&&p.starterMask()==3,"repeat starter day one uses regular reward and resets run display");
        for(long date=104;date<=109;date++)p=p.reserve(date);
        require(p.completedStarter()&&p.starterMask()==127,"all seven unique days complete starter permanently");require(p.nextDay(110)==1&&!p.starterCycle(110),"next week becomes regular cycle");
        require(p.starterCycle(109),"last starter claim keeps its completed showcase for today");
        p=p.reserve(110);require(!p.lastStarter()&&p.runUniqueMask()==0,"regular cycle never awards starter items");
        require(p.nextDay(112)==1&&!p.starterCycle(112),"missed regular day cannot restart unique starter rewards");
        var id=UUID.randomUUID();var data=new DailyRewardsData();data.put(id,new DailyRewardsData.Entry("OfflinePlayer",p,null));
        var saved=data.save(new CompoundTag(),null);var restored=DailyRewardsData.load(saved,null);require(restored.entry(id).progress().equals(p),"date, day, mask and cycle survive NBT");
        restored.reset(id);require(restored.entry(id).progress().equals(DailyRewardProgress.fresh()),"offline UUID reset clears starter progress");
        data.resetAll();require(data.entries().values().stream().allMatch(e->e.progress().starterMask()==0&&e.pending()==null),"resetall covers every saved UUID");
        for(int[] size:new int[][]{{320,180},{640,360},{960,540}}){
            var layout=DailyRewardsLayout.of(size[0],size[1]);require(layout.x()>=0&&layout.y()>=0&&layout.scale()>0,"compact window fits GUI scale");
            require(layout.card(6).right()<DailyRewardsLayout.WIDTH&&layout.wood(7).right()<layout.claim().x(),"seven reward cards and eight wood choices fit");
            var hub=SocialHubLayout.calculate(size[0],size[1]);require(hub.dailyGift().right()<hub.search().x()&&hub.dailyGift().x()>hub.back().right(),"gift does not overlap search/back");
        }
        System.out.println("Daily rewards: "+checks+" focused checks passed");
    }
}
