package com.example.examplemod.city;
import com.example.examplemod.network.CityBoundariesPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Small read-only physical-neighbourhood snapshot; never loads terrain. */
public final class CityBoundaryNetwork {
 private CityBoundaryNetwork(){}
 public static void request(ServerPlayer player){
  long now=net.minecraft.Util.getMillis();var budget=player.getPersistentData();long previous=budget.getLong("CCECBoundaryRefresh");
  if(now>=previous&&now-previous<4000)return;budget.putLong("CCECBoundaryRefresh",now);
  var manager=CityManager.get(player.getServer().overworld());var dim=player.serverLevel().dimension().location();var anchor=player.blockPosition();
  var current=manager.getCityAt(dim,anchor);
  var views=new ArrayList<>(manager.territoryViews(dim,anchor,128,null,id->false));
  views.sort(Comparator.comparingInt(v->current!=null&&v.cityId().equals(current.getId())?0:1));
  List<CityBoundariesPayload.CityView> result=new ArrayList<>();int remaining=CityBoundariesPayload.MAX_PLOTS;
  for(var view:views){if(result.size()>=CityBoundariesPayload.MAX_CITIES)break;if(view.plots().size()>remaining)continue;
   var city=manager.getCity(view.cityId());if(city==null)continue;
   result.add(new CityBoundariesPayload.CityView(view.cityId(),view.name(),view.colorRgb(),manager.allowsTerritoryAction(city,player.getUUID(),true),Set.copyOf(view.plots())));remaining-=view.plots().size();
  }
  PacketDistributor.sendToPlayer(player,new CityBoundariesPayload(dim,anchor,result));
 }
}
