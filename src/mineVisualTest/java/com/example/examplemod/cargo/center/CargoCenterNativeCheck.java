package com.example.examplemod.cargo.center;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Pinned bytecode contract only. ItemStack decoding requires the actual NeoForge game launcher. */
public final class CargoCenterNativeCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Object annotation(AnnotationNode a,String key){for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return null;}
    public static void main(String[] args)throws Exception{
        try(var input=CargoCenterNativeCheck.class.getClassLoader().getResourceAsStream("com/example/examplemod/mixin/GlobalStationCargoMixin.class")){var type=new ClassNode();new ClassReader(input).accept(type,0);int ordinal=0;for(String name:List.of("ccec$observeBoarding","ccec$observeAlighting")){var m=type.methods.stream().filter(v->v.name.equals(name)).findFirst().orElseThrow();var redirect=m.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();var at=(AnnotationNode)annotation(redirect,"at");require(annotation(at,"ordinal").equals(ordinal++),"separate exact native source-clear ordinal");require(annotation(redirect,"require").equals(1)&&annotation(redirect,"allow").equals(1),"fail closed on Create injection drift");int clears=0,observes=0,lastClear=-1,observe=-1;for(int i=0;i<m.instructions.size();i++)if(m.instructions.get(i) instanceof MethodInsnNode call){if(call.owner.equals("net/neoforged/neoforge/items/IItemHandlerModifiable")&&call.name.equals("setStackInSlot")){clears++;lastClear=i;}if(call.owner.endsWith("/CargoShipmentRegistry")&&call.name.equals("mailTransfer")){observes++;observe=i;}}require(clears==1&&observes==1&&lastClear<observe,"native source mutation occurs exactly once before observation");}}
        System.out.println("PASS: "+checks+" exact native boarding/alighting observer injection checks");
    }
}
