package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.economy.EconomyTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record EconomyStatePayload(BlockPos pylonPos,UUID cityId,boolean unlocked,boolean masterCity,
                                  long wallet,long treasury,long treasuryEscrow,long procurementAvailable,
                                  long procurementReserved,long procurementCap,int privateFeeBasisPoints,
                                  int cityFeeBasisPoints,List<ContractView> contracts,List<ListingView> listings,
                                  List<ShipmentView> shipments,List<InfrastructureView> infrastructure,List<StockView> warehouse)
        implements CustomPacketPayload {
    public static final Type<EconomyStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"economy_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EconomyStatePayload> STREAM_CODEC=StreamCodec.of(EconomyStatePayload::encode,EconomyStatePayload::decode);
    public EconomyStatePayload{contracts=List.copyOf(contracts);listings=List.copyOf(listings);shipments=List.copyOf(shipments);infrastructure=List.copyOf(infrastructure);warehouse=List.copyOf(warehouse);}
    public record ContractView(UUID id,EconomyTypes.ContractKind kind,EconomyTypes.ContractMode mode,UUID issuerCityId,String issuerName,String acceptedByName,String contractorName,String title,String description,ResourceLocation itemId,long amount,long delivered,long reward,EconomyTypes.ContractState state,UUID contractorCityId,long deadlineAt,UUID destinationId,boolean mine,boolean issuerMine){}
    public record ListingView(UUID id,EconomyTypes.ListingType type,UUID creator,String creatorName,UUID counterparty,ResourceLocation itemId,long amount,long price,String meeting,EconomyTypes.ListingState state,boolean mine,ResourceLocation requestedItemId){
        public boolean isBarter(){return requestedItemId!=null;}
        public boolean offersCurrency(){return type==EconomyTypes.ListingType.BUY;}
        /** Предмет, который отдаёт автор объявления; null — если он отдаёт валюту. */
        public ResourceLocation offerItemId(){return offersCurrency()?null:itemId;}
        public long offerAmount(){return offersCurrency()?price:amount;}
        /** Предмет, который автор хочет получить; null — если он хочет валюту. */
        public ResourceLocation requestItemId(){return offersCurrency()?itemId:requestedItemId;}
        public long requestAmount(){return offersCurrency()?amount:price;}
    }
    public record ShipmentView(UUID id,UUID contractId,UUID trainId,String trainName,long declared,long verified,EconomyTypes.ShipmentStatus status){}
    public record InfrastructureView(UUID id,EconomyTypes.InfrastructureType type,ResourceLocation dimension,BlockPos pos,boolean available,boolean cityOwned,UUID stationId,EconomyTypes.IntakeAttributionError lastError){}
    public record StockView(ResourceLocation itemId,long amount){}

    private static void encode(RegistryFriendlyByteBuf b,EconomyStatePayload p){
        BlockPos.STREAM_CODEC.encode(b,p.pylonPos);b.writeUUID(p.cityId);b.writeBoolean(p.unlocked);b.writeBoolean(p.masterCity);
        b.writeLong(p.wallet);b.writeLong(p.treasury);b.writeLong(p.treasuryEscrow);b.writeLong(p.procurementAvailable);b.writeLong(p.procurementReserved);b.writeLong(p.procurementCap);b.writeVarInt(p.privateFeeBasisPoints);b.writeVarInt(p.cityFeeBasisPoints);
        b.writeVarInt(Math.min(512,p.contracts.size()));
        for(int i=0;i<Math.min(512,p.contracts.size());i++){var v=p.contracts.get(i);b.writeUUID(v.id);b.writeEnum(v.kind);b.writeEnum(v.mode);b.writeUUID(v.issuerCityId);b.writeUtf(v.issuerName,96);b.writeUtf(v.acceptedByName,64);b.writeUtf(v.contractorName,96);b.writeUtf(v.title,96);b.writeUtf(v.description,512);b.writeResourceLocation(v.itemId);b.writeLong(v.amount);b.writeLong(v.delivered);b.writeLong(v.reward);b.writeEnum(v.state);b.writeBoolean(v.contractorCityId!=null);if(v.contractorCityId!=null)b.writeUUID(v.contractorCityId);b.writeLong(v.deadlineAt);b.writeUUID(v.destinationId);b.writeBoolean(v.mine);b.writeBoolean(v.issuerMine);}
        b.writeVarInt(Math.min(512,p.listings.size()));
        for(int i=0;i<Math.min(512,p.listings.size());i++){var v=p.listings.get(i);b.writeUUID(v.id);b.writeEnum(v.type);b.writeUUID(v.creator);b.writeUtf(v.creatorName,64);b.writeBoolean(v.counterparty!=null);if(v.counterparty!=null)b.writeUUID(v.counterparty);b.writeResourceLocation(v.itemId);b.writeLong(v.amount);b.writeLong(v.price);b.writeUtf(v.meeting,160);b.writeEnum(v.state);b.writeBoolean(v.mine);b.writeBoolean(v.requestedItemId!=null);if(v.requestedItemId!=null)b.writeResourceLocation(v.requestedItemId);}
        b.writeVarInt(Math.min(512,p.shipments.size()));for(int i=0;i<Math.min(512,p.shipments.size());i++){var v=p.shipments.get(i);b.writeUUID(v.id);b.writeUUID(v.contractId);b.writeUUID(v.trainId);b.writeUtf(v.trainName,96);b.writeLong(v.declared);b.writeLong(v.verified);b.writeEnum(v.status);}
        b.writeVarInt(Math.min(128,p.infrastructure.size()));
        for(int i=0;i<Math.min(128,p.infrastructure.size());i++){var v=p.infrastructure.get(i);b.writeUUID(v.id);b.writeEnum(v.type);b.writeResourceLocation(v.dimension);BlockPos.STREAM_CODEC.encode(b,v.pos);b.writeBoolean(v.available);b.writeBoolean(v.cityOwned);b.writeBoolean(v.stationId!=null);if(v.stationId!=null)b.writeUUID(v.stationId);b.writeEnum(v.lastError);}
        b.writeVarInt(Math.min(512,p.warehouse.size()));for(int i=0;i<Math.min(512,p.warehouse.size());i++){var v=p.warehouse.get(i);b.writeResourceLocation(v.itemId);b.writeLong(v.amount);}
    }

    private static EconomyStatePayload decode(RegistryFriendlyByteBuf b){
        BlockPos p=BlockPos.STREAM_CODEC.decode(b);UUID city=b.readUUID();boolean unlocked=b.readBoolean(),master=b.readBoolean();long wallet=b.readLong(),treasury=b.readLong(),escrow=b.readLong(),available=b.readLong(),reserved=b.readLong(),cap=b.readLong();int privateFee=b.readVarInt(),cityFee=b.readVarInt();
        List<ContractView> contracts=new ArrayList<>();int cc=Math.min(512,b.readVarInt());
        for(int i=0;i<cc;i++){UUID id=b.readUUID();var kind=b.readEnum(EconomyTypes.ContractKind.class);var mode=b.readEnum(EconomyTypes.ContractMode.class);UUID issuer=b.readUUID();String issuerName=b.readUtf(96),acceptedByName=b.readUtf(64),contractorName=b.readUtf(96),title=b.readUtf(96),description=b.readUtf(512);ResourceLocation item=b.readResourceLocation();long amount=b.readLong(),delivered=b.readLong(),reward=b.readLong();var state=b.readEnum(EconomyTypes.ContractState.class);UUID contractor=b.readBoolean()?b.readUUID():null;long deadline=b.readLong();UUID destination=b.readUUID();boolean mine=b.readBoolean(),issuerMine=b.readBoolean();contracts.add(new ContractView(id,kind,mode,issuer,issuerName,acceptedByName,contractorName,title,description,item,amount,delivered,reward,state,contractor,deadline,destination,mine,issuerMine));}
        List<ListingView> listings=new ArrayList<>();int lc=Math.min(512,b.readVarInt());
        for(int i=0;i<lc;i++){UUID id=b.readUUID();var type=b.readEnum(EconomyTypes.ListingType.class);UUID creator=b.readUUID();String name=b.readUtf(64);UUID counterparty=b.readBoolean()?b.readUUID():null;ResourceLocation item=b.readResourceLocation();long amount=b.readLong(),price=b.readLong();String meeting=b.readUtf(160);var state=b.readEnum(EconomyTypes.ListingState.class);boolean mine=b.readBoolean();ResourceLocation requested=b.readBoolean()?b.readResourceLocation():null;listings.add(new ListingView(id,type,creator,name,counterparty,item,amount,price,meeting,state,mine,requested));}
        List<ShipmentView> shipments=new ArrayList<>();int shc=Math.min(512,b.readVarInt());for(int i=0;i<shc;i++)shipments.add(new ShipmentView(b.readUUID(),b.readUUID(),b.readUUID(),b.readUtf(96),b.readLong(),b.readLong(),b.readEnum(EconomyTypes.ShipmentStatus.class)));
        List<InfrastructureView> infra=new ArrayList<>();int ic=Math.min(128,b.readVarInt());
        for(int i=0;i<ic;i++){UUID id=b.readUUID();var type=b.readEnum(EconomyTypes.InfrastructureType.class);ResourceLocation dimension=b.readResourceLocation();BlockPos pos=BlockPos.STREAM_CODEC.decode(b);boolean ok=b.readBoolean(),cityOwned=b.readBoolean();UUID station=b.readBoolean()?b.readUUID():null;var error=b.readEnum(EconomyTypes.IntakeAttributionError.class);infra.add(new InfrastructureView(id,type,dimension,pos,ok,cityOwned,station,error));}
        List<StockView> stock=new ArrayList<>();int sc=Math.min(512,b.readVarInt());for(int i=0;i<sc;i++)stock.add(new StockView(b.readResourceLocation(),b.readLong()));
        return new EconomyStatePayload(p,city,unlocked,master,wallet,treasury,escrow,available,reserved,cap,privateFee,cityFee,contracts,listings,shipments,infra,stock);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
