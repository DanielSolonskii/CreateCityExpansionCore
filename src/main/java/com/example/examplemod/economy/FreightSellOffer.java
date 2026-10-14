package com.example.examplemod.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** City sale advertisement. Escrow and logistics are created only when a buyer confirms. */
public record FreightSellOffer(UUID id,UUID city,UUID creator,ResourceLocation item,long amount,long reward,long deadline,String title,String description,UUID contract){
    public FreightSellOffer accepted(UUID contract){return new FreightSellOffer(id,city,creator,item,amount,reward,deadline,title,description,contract);}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("Id",id);t.putUUID("City",city);t.putUUID("Creator",creator);t.putString("Item",item.toString());t.putLong("Amount",amount);t.putLong("Reward",reward);t.putLong("Deadline",deadline);t.putString("Title",title);t.putString("Description",description);if(contract!=null)t.putUUID("Contract",contract);return t;}
    public static FreightSellOffer load(CompoundTag t){return new FreightSellOffer(t.getUUID("Id"),t.getUUID("City"),t.getUUID("Creator"),ResourceLocation.parse(t.getString("Item")),t.getLong("Amount"),t.getLong("Reward"),t.getLong("Deadline"),t.getString("Title"),t.getString("Description"),t.hasUUID("Contract")?t.getUUID("Contract"):null);}
}
