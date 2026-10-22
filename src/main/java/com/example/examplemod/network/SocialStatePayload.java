package com.example.examplemod.network;
import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.social.SocialBook;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public record SocialStatePayload(UUID request,String result,long revision,UUID target,String name,SocialBook.Relation relation,int filter,int page,int total,List<Contact> contacts,List<SocialBook.Message> messages,boolean older,int requests,int unread,String city,String role) implements CustomPacketPayload {
    public record Contact(UUID id,String name,boolean online,SocialBook.Relation relation,int unread,String city,String role){public Contact(UUID id,String name,boolean online,SocialBook.Relation relation,int unread){this(id,name,online,relation,unread,"","");}}
    public SocialStatePayload(UUID request,String result,long revision,UUID target,String name,SocialBook.Relation relation,int filter,int page,int total,List<Contact> contacts,List<SocialBook.Message> messages,boolean older,int requests,int unread){this(request,result,revision,target,name,relation,filter,page,total,contacts,messages,older,requests,unread,"","");}
    public SocialStatePayload {contacts=List.copyOf(contacts);messages=List.copyOf(messages);if(contacts.size()>16||messages.size()>20)throw new IllegalArgumentException("Social page too large");}
    public static final Type<SocialStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"social_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SocialStatePayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.request);b.writeUtf(p.result,24);b.writeVarLong(p.revision);b.writeBoolean(p.target!=null);if(p.target!=null)b.writeUUID(p.target);b.writeUtf(p.name,64);b.writeEnum(p.relation);b.writeVarInt(p.filter);b.writeVarInt(p.page);b.writeVarInt(p.total);
        b.writeVarInt(p.contacts.size());for(var c:p.contacts){b.writeUUID(c.id);b.writeUtf(c.name,64);b.writeBoolean(c.online);b.writeEnum(c.relation);b.writeVarInt(c.unread);b.writeUtf(c.city,128);b.writeUtf(c.role,24);}
        b.writeVarInt(p.messages.size());for(var m:p.messages){b.writeVarLong(m.id());b.writeUUID(m.nonce());b.writeUUID(m.sender());b.writeLong(m.time());b.writeUtf(m.text(),500);}
        b.writeBoolean(p.older);b.writeVarInt(p.requests);b.writeVarInt(p.unread);b.writeUtf(p.city,128);b.writeUtf(p.role,24);
    },b->{UUID request=b.readUUID();String result=b.readUtf(24);long revision=b.readVarLong();UUID target=b.readBoolean()?b.readUUID():null;String name=b.readUtf(64);var relation=b.readEnum(SocialBook.Relation.class);int filter=b.readVarInt(),page=b.readVarInt(),total=b.readVarInt();
        int count=b.readVarInt();if(count<0||count>16)throw new IllegalArgumentException("Contacts");List<Contact> contacts=new ArrayList<>();for(int i=0;i<count;i++)contacts.add(new Contact(b.readUUID(),b.readUtf(64),b.readBoolean(),b.readEnum(SocialBook.Relation.class),b.readVarInt(),b.readUtf(128),b.readUtf(24)));
        count=b.readVarInt();if(count<0||count>20)throw new IllegalArgumentException("Messages");List<SocialBook.Message> messages=new ArrayList<>();for(int i=0;i<count;i++)messages.add(new SocialBook.Message(b.readVarLong(),b.readUUID(),b.readUUID(),b.readLong(),b.readUtf(500)));
        return new SocialStatePayload(request,result,revision,target,name,relation,filter,page,total,contacts,messages,b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(128),b.readUtf(24));
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
