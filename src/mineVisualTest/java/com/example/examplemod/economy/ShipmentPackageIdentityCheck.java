package com.example.examplemod.economy;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Headless regression for the freight package identity: a freshly issued package must still be
 * recognisable as its own shipment after the ordinary Create Postbox/mail/train transfer.
 *
 * <p>The defect was a component shadow, not a lost transfer. Create 6 stores the order of any box
 * produced by a packager request in {@code PACKAGE_ORDER_DATA} â€” {@code PackagerBlockEntity#createBox}
 * calls {@code PackageItem.setOrder} â€” and {@code PackageItem.getOrderContext} resolves that
 * component <em>in preference to</em> the legacy {@code PACKAGE_ORDER_CONTEXT}. {@code mark} used
 * {@code PackageItem.addOrderContext}, which writes only the legacy component, so the dispatch seal
 * landed where every reader skipped it: not the stamp guard, not {@code trainCargo}, and not
 * Create's own unpacking call, which reads the same accessor. A freshly stamped box therefore
 * counted as an unrecognised package the moment it reached the train â€” and every later pass minted
 * another token it could never see.
 *
 * <p><b>Scope note.</b> Cases A, B, D, E, F and G execute the real predicate
 * ({@link FreightLogistics#packageVerdict}) against the real ledger. Cases A-wiring and C pin the
 * component path by inspecting the production source instead of by executing it: this harness cannot
 * construct an {@code ItemStack}, because NeoForge's {@code FeatureFlags} needs a live
 * {@code LoadingModList} that a plain JavaExec does not have. The precedence rule itself is Create's
 * own code and is quoted in {@link #componentPrecedenceIsNotRewritable()}.
 */
public final class ShipmentPackageIdentityCheck {
    private static int checks;
    private static String freightSource;

    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}

    private static FreightLogistics.PackageVerdict verdict(Set<UUID> ids,UUID subject,EconomyManager manager){
        return FreightLogistics.packageVerdict(ids,subject,manager,new HashSet<>());
    }

    private static String productionSource(){
        if(freightSource!=null)return freightSource;
        Path root=Paths.get("").toAbsolutePath();
        while(root!=null&&!Files.isDirectory(root.resolve("src/main/java")))root=root.getParent();
        if(root==null)throw new AssertionError("cannot locate source tree");
        try{
            freightSource=Files.readString(root.resolve("src/main/java/com/example/examplemod/economy/FreightLogistics.java"));
        }catch(Exception ex){throw new AssertionError("cannot read FreightLogistics.java: "+ex);}
        return freightSource;
    }

    /** Quoted from Create 6.0.10-280 {@code PackageItem#getOrderContext} and {@code #addOrderContext}. */
    private static void componentPrecedenceIsNotRewritable(){
        String source=productionSource();
        require(source.contains("static Set<UUID> packageTokens(ItemStack box)"),
                "a single reader owns the precedence rule instead of each call site guessing");
        require(source.contains("PackageOrderWithCrafts context=PackageItem.getOrderContext(box);"),
                "the reader resolves the order through Create's own accessor, never the legacy component");
    }

    public static void main(String[] args)throws Exception{
        bootstrapConfig();
        EconomyManager manager=new EconomyManager();
        FreightLogistics freight=manager.freight();

        UUID cityA=UUID.randomUUID(),cityB=UUID.randomUUID(),player=UUID.randomUUID();
        FreightAssignment assignment=new FreightAssignment(UUID.randomUUID(),UUID.randomUUID(),new FreightPoint(
                UUID.randomUUID(),cityB,ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(100,64,0),new BlockPos(102,64,0),"Destination",FreightPoint.Mode.BOTH));
        UUID subject=assignment.subject;
        map(manager,"shipmentTokens");

        // A token that really is ACTIVE for this shipment: exactly what authorizeExportPackage mints.
        ShipmentToken active=new ShipmentToken(UUID.randomUUID(),UUID.randomUUID(),subject,
                EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT,cityA,cityB,
                ResourceLocation.parse("minecraft:diamond"),64L,0L,1000L);
        ShipmentToken other=new ShipmentToken(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT,cityA,UUID.randomUUID(),
                ResourceLocation.parse("minecraft:diamond"),64L,0L,1000L);
        map(manager,"shipmentTokens").put(active.tokenId(),active);
        map(manager,"shipmentTokens").put(other.tokenId(),other);
        // A second, still-live stamp of this same shipment, used by the mixed and duplicate cases.
        ShipmentToken second=new ShipmentToken(UUID.randomUUID(),UUID.randomUUID(),subject,
                EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT,cityA,cityB,
                ResourceLocation.parse("minecraft:diamond"),8L,0L,1000L);
        map(manager,"shipmentTokens").put(second.tokenId(),second);

        Set<UUID> freshBox=Set.of(active.tokenId());
        Set<UUID> emptyBox=Set.of();

        // ================= A: a stamped box in the train counts as cargo for its own shipment
        FreightLogistics.PackageVerdict loaded=verdict(freshBox,subject,manager);
        require(loaded.counted()==64L&&!loaded.stray(),
                "A: the ACTIVE stamp of a fresh shipment counts as cargo and is not reported as stray");
        require(loaded.counted()>=EconomyMath.checkedPositive(64L,100_000_000L,"amount"),
                "A: the counted amount is the token's own amount");

        // ================= C-wiring: the seal must be written where the reader looks first
        componentPrecedenceIsNotRewritable();
        String source=productionSource();
        require(source.contains("PackageItem.setOrder(box,order.orderId(),order.linkIndex(),order.isFinalLink(),order.fragmentIndex(),order.isFinal(),stamped)"),
                "C: the stamp is written through setOrder, the sanctioned writer of PACKAGE_ORDER_DATA");
        require(source.contains("if(box.has(AllDataComponents.PACKAGE_ORDER_DATA))"),
                "C: the packager's order data is detected explicitly rather than assumed absent");
        require(source.indexOf("PackageItem.setOrder(box,")<source.indexOf("PackageItem.addOrderContext(box,stamped)"),
                "C: the shadowed legacy write is only the fallback for a box that has no order data");
        require(source.contains("}else PackageItem.addOrderContext(box,stamped);"),
                "C: a box without order data still gets a readable order");
        require(source.contains("if(!packageTokens(box).isEmpty())return;"),
                "C: the stamp guard reads the stamp the same way trainCargo does, so it stops re-minting");

        // ================= B: the token stays ACTIVE until the real intake transition
        require(active.state()==EconomyTypes.ShipmentTokenState.ACTIVE,"B: issuing and travelling consume nothing");
        require(verdict(freshBox,subject,manager).counted()==64L,"B: the travelling package still counts as cargo");
        require(active.state()==EconomyTypes.ShipmentTokenState.ACTIVE,"B: reading cargo has no ledger side effect");

        // ================= D: a spent or invalid stamp of this dispatch is rejected
        active.consume(7L);
        FreightLogistics.PackageVerdict spent=verdict(freshBox,subject,manager);
        require(spent.counted()==0L&&spent.stray(),"D: a consumed token counts nothing and is reported unusable");
        require(verdict(freshBox,subject,manager).counted()==0L,"D: rejection is stable across repeated scans");
        active.invalidate();
        require(verdict(freshBox,subject,manager).counted()==0L,"D: an invalidated token counts nothing");

        // ================= E: another dispatch's live stamp is neither cargo nor a fault
        Set<UUID> foreignBox=Set.of(other.tokenId());
        FreightLogistics.PackageVerdict foreign=verdict(foreignBox,subject,manager);
        require(foreign.counted()==0L&&!foreign.stray(),
                "E: another dispatch's live package is not counted and not reported as a fault");
        Set<UUID> mixed=Set.of(second.tokenId(),other.tokenId());
        FreightLogistics.PackageVerdict mixedVerdict=verdict(mixed,subject,manager);
        require(mixedVerdict.counted()==8L&&!mixedVerdict.stray(),
                "E: a box that also carries a live token of our own counts ours and is not a fault");
        Set<UUID> spentPlusForeign=Set.of(active.tokenId(),other.tokenId());
        FreightLogistics.PackageVerdict spentMixed=verdict(spentPlusForeign,subject,manager);
        require(spentMixed.counted()==0L&&spentMixed.stray(),
                "E: a spent stamp of ours makes the box unusable even alongside a live foreign stamp");
        require(verdict(emptyBox,subject,manager).counted()==0L&&verdict(emptyBox,subject,manager).stray(),
                "E: an unstamped box is the one case that is provably unusable");

        // ================= F: no double count, no duplicate token, no duplicate reward
        require(!second.tokenId().equals(active.tokenId()),"F: a second package means a second token id");
        Set<UUID> shared=new HashSet<>();
        long counted=FreightLogistics.packageVerdict(Set.of(second.tokenId()),subject,manager,shared).counted()
                +FreightLogistics.packageVerdict(Set.of(second.tokenId()),subject,manager,shared).counted()
                +FreightLogistics.packageVerdict(Set.of(active.tokenId(),second.tokenId()),subject,manager,shared).counted();
        require(counted==8L,"F: re-offering the same token in one scan adds nothing: 8 stays 8");
        require(manager.shipmentTokens().stream().filter(t->t.subjectId().equals(subject)).count()==2,
                "F: stamping never re-uses a token id");
        require(manager.shipmentTokens().stream().filter(t->t.subjectId().equals(subject))
                        .mapToLong(ShipmentToken::amount).sum()==72L,
                "F: in-flight cargo equals the authorised packages exactly once each");

        // ================= G: a restart does not break token resolution
        FreightAssignment reloaded=FreightAssignment.load(assignment.save());
        require(reloaded.subject.equals(subject),"G: the subject survives a restart");
        require(verdict(freshBox,reloaded.subject,manager).counted()==0L&&verdict(freshBox,reloaded.subject,manager).stray(),
                "G: a spent token stays rejected after a restart; token state is never resurrected");
        Set<UUID> freshAfterRestart=Set.of(second.tokenId());
        require(verdict(freshAfterRestart,reloaded.subject,manager).counted()==8L,
                "G: a still-live token keeps resolving to its shipment after a restart");

        System.out.println("PASS: "+checks+" shipment package identity, stamp visibility and duplicate checks");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID,ShipmentToken> map(EconomyManager manager,String name)throws Exception{
        Field field=EconomyManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return(java.util.Map<UUID,ShipmentToken>)field.get(manager);
    }

    private static void bootstrapConfig()throws Exception{
        Field configField=com.example.examplemod.Config.class.getDeclaredField("SPEC");
        configField.setAccessible(true);
        var spec=(net.neoforged.neoforge.common.ModConfigSpec)configField.get(null);
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(config);
        config.set("items",List.of());
        var loadedType=Class.forName("net.neoforged.fml.config.LoadedConfig");
        var constructor=loadedType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)constructor.newInstance(config,null,null));
    }
}