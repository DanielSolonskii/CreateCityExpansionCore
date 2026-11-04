package com.example.examplemod.economy;
import com.example.examplemod.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Shared wallets, city treasuries, bank and project funding. Retired data is inert. */
public final class EconomyManager extends SavedData {
    private static final String DATA_NAME="createcityexpansioncore_economy";
    private static final int FORMAT_VERSION=16;
    @Nullable private CompoundTag unavailableData;
    @Nullable private RuntimeException loadFailure;
    private CompoundTag retainedData=new CompoundTag();
    private UUID masterCityId;
    private final com.example.examplemod.projects.ProjectManager projects = new com.example.examplemod.projects.ProjectManager(
            new com.example.examplemod.projects.ProjectManager.Accounts() {
                public long balance(boolean city,UUID id){return (city?treasuries:wallets).getOrDefault(id,0L);}
                public boolean debit(boolean city,UUID id,long amount){return EconomyManager.debit(city?treasuries:wallets,id,amount);}
                public boolean canCredit(boolean city,UUID id,long amount){return EconomyManager.canCredit(city?treasuries:wallets,id,amount);}
                public void credit(boolean city,UUID id,long amount){EconomyManager.credit(city?treasuries:wallets,id,amount);}
                public long maximum(){return Config.ECONOMY_MAX_TRANSACTION.get();}
                public long serverBudget(){return Config.SERVER_PROJECT_BUDGET.get();}
                public void dirty(){setDirty();}
            });
    public com.example.examplemod.projects.ProjectManager projects(){return projects;}

    private boolean loadingBalances;
    private final BankBook bank = new BankBook(new BankBook.Wallet(){
        public long balance(UUID owner){return wallet(owner);}
        public boolean debit(UUID owner,long amount){return EconomyManager.debit(wallets,owner,amount);}
        public boolean canCredit(UUID owner,long amount){return EconomyManager.canCredit(wallets,owner,amount);}
        public void credit(UUID owner,long amount){EconomyManager.credit(wallets,owner,amount);}
        public long maximum(){return Config.ECONOMY_MAX_TRANSACTION.get();}
        public void dirty(){setDirty();}
    });
    public BankBook bank(){return bank;}
    private final Map<UUID, Long> wallets = new LinkedHashMap<>(){
        @Override public Long put(UUID id,Long amount){long before=getOrDefault(id,0L);Long old=super.put(id,amount);if(!loadingBalances)bank.observeWallet(id,before,amount);return old;}
        @Override public Long remove(Object key){Long old=super.remove(key);if(!loadingBalances&&key instanceof UUID id&&old!=null)bank.observeWallet(id,old,0);return old;}
    };

    private final Map<UUID, Long> treasuries = new LinkedHashMap<>();
    private final com.example.examplemod.cargo.center.CargoCommerceBook cargo = new com.example.examplemod.cargo.center.CargoCommerceBook(
        new com.example.examplemod.cargo.center.CargoCommerceBook.Accounts(){
            private Map<UUID,Long> balances(com.example.examplemod.cargo.center.OwnerRef o){return o.kind()==com.example.examplemod.cargo.center.OwnerRef.Kind.CITY?treasuries:wallets;}
            public long balance(com.example.examplemod.cargo.center.OwnerRef o){return balances(o).getOrDefault(o.id(),0L);}
            public boolean debit(com.example.examplemod.cargo.center.OwnerRef o,long n){return EconomyManager.debit(balances(o),o.id(),n);}
            public boolean canCredit(com.example.examplemod.cargo.center.OwnerRef o,long n){return EconomyManager.canCredit(balances(o),o.id(),n);}
            public void credit(com.example.examplemod.cargo.center.OwnerRef o,long n){EconomyManager.credit(balances(o),o.id(),n);}
            public long maximum(){return Config.ECONOMY_MAX_TRANSACTION.get();}
            public void dirty(){setDirty();}
        });
    public com.example.examplemod.cargo.center.CargoCommerceBook cargo(){return cargo;}

    public UUID masterCityId(){return masterCityId;}
    public long treasuryEscrow(UUID cityId){return projects.all().stream()
        .filter(p->cityId.equals(p.city())&&p.funding()==com.example.examplemod.projects.Project.Funding.TREASURY)
        .mapToLong(com.example.examplemod.projects.Project::reserved)
        .reduce(0L,(a,b)->EconomyMath.saturatingAdd(a,b,Long.MAX_VALUE));}
public static EconomyManager get(ServerLevel anyLevel) {
        return fromStorage(anyLevel.getServer().overworld().getDataStorage());
    }

    static EconomyManager fromStorage(net.minecraft.world.level.storage.DimensionDataStorage storage) {
        EconomyManager manager = storage.computeIfAbsent(
                new SavedData.Factory<>(EconomyManager::new, EconomyManager::loadForStorage), DATA_NAME);
        // Throw outside DimensionDataStorage's exception handler; otherwise it
        // silently replaces a rejected financial registry with empty accounts.
        if (manager.loadFailure != null)
            throw new IllegalStateException("Economy save cannot be loaded; original data retained", manager.loadFailure);
        return manager;
    }

    private static EconomyManager loadForStorage(CompoundTag root, HolderLookup.Provider registries) {
        try {
            return load(root, registries);
        } catch (RuntimeException failure) {
            EconomyManager unavailable = new EconomyManager();
            unavailable.unavailableData = root.copy();
            unavailable.loadFailure = failure;
            return unavailable;
        }
    }

public long wallet(UUID playerId) { return wallets.getOrDefault(playerId, 0L); }

public long treasury(UUID cityId) { return treasuries.getOrDefault(cityId, 0L); }

public boolean setMasterCity(@Nullable UUID cityId) {
        if (Objects.equals(masterCityId, cityId)) return false;
        masterCityId = cityId;
        setDirty();
        return true;
    }

public boolean adminSetWallet(UUID playerId, long amount) {
        if (amount < 0L || amount > Config.ECONOMY_MAX_TRANSACTION.get()) return false;
        putBalance(wallets, playerId, amount); setDirty(); return true;
    }

public boolean adminSetTreasury(UUID cityId, long amount) {
        if (amount < 0L || amount > Config.ECONOMY_MAX_TRANSACTION.get()) return false;
        putBalance(treasuries, cityId, amount); setDirty(); return true;
    }

public boolean pay(UUID sender, UUID receiver, long amount) {
        if(sender==null||receiver==null)return false;
        return bank.execute(sender,new BankBook.Command(UUID.randomUUID(),bank.revision(sender),BankBook.Action.TRANSFER,null,null,receiver,amount,0,"",0),System.currentTimeMillis()).equals("ok");
    }

public boolean depositToCity(UUID playerId, UUID cityId, long amount) {
        if (!validMoney(amount) || !debit(wallets, playerId, amount)) return false;
        try { credit(treasuries, cityId, amount); }
        catch (ArithmeticException ex) { credit(wallets, playerId, amount); return false; }
        setDirty(); return true;
    }

public boolean withdrawFromCity(UUID cityId, UUID playerId, long amount) {
        if (!validMoney(amount) || !debit(treasuries, cityId, amount)) return false;
        try { credit(wallets, playerId, amount); }
        catch (ArithmeticException ex) { credit(treasuries, cityId, amount); return false; }
        setDirty(); return true;
    }

private boolean validMoney(long amount) { return amount > 0L && amount <= Config.ECONOMY_MAX_TRANSACTION.get(); }

private static void putBalance(Map<UUID, Long> map, UUID id, long amount) { if (amount == 0L) map.remove(id); else map.put(id, amount); }

private static boolean debit(Map<UUID, Long> map, UUID id, long amount) { long value=map.getOrDefault(id,0L);if(amount<0L||value<amount)return false;putBalance(map,id,value-amount);return true; }

private static boolean canCredit(Map<UUID,Long> map,UUID id,long amount){return amount<=0L||id!=null&&map.getOrDefault(id,0L)<=Long.MAX_VALUE-amount;}

private static void credit(Map<UUID, Long> map, UUID id, long amount) { if(amount<=0L)return;putBalance(map,id,EconomyMath.addExactNonNegative(map.getOrDefault(id,0L),amount)); }

private static ListTag writeUuidLongMap(Map<UUID,Long> values,String idKey){ListTag list=new ListTag();values.forEach((id,amount)->{if(amount<=0L)return;CompoundTag tag=new CompoundTag();tag.putUUID(idKey,id);tag.putLong("Amount",amount);list.add(tag);});return list;}

private static void readUuidLongMap(CompoundTag root,String listKey,String idKey,Map<UUID,Long> target){for(Tag raw:root.getList(listKey,Tag.TAG_COMPOUND)){try{CompoundTag tag=(CompoundTag)raw;long amount=tag.getLong("Amount");if(tag.hasUUID(idKey)&&amount>0L)target.put(tag.getUUID(idKey),amount);}catch(RuntimeException ignored){}}}

    private static EconomyManager load(CompoundTag root,HolderLookup.Provider registries){
        int version=root.getInt("FormatVersion");
        if(version>FORMAT_VERSION)throw new IllegalStateException("Unsupported economy save format "+version);
        var manager=new EconomyManager();manager.retainedData=root.copy();manager.loadingBalances=true;
        manager.masterCityId=root.hasUUID("MasterCity")?root.getUUID("MasterCity"):null;
        readUuidLongMap(root,"Wallets","Player",manager.wallets);
        readUuidLongMap(root,"Treasuries","City",manager.treasuries);
        manager.projects.load(root.getCompound("CityProjects"));manager.bank.load(root.getCompound("PersonalBank"));
        manager.cargo.load(root.getCompound("CargoCommerce"));
        manager.loadingBalances=false;return manager;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        if(unavailableData!=null)return unavailableData.copy();
        root.merge(retainedData.copy());root.putInt("FormatVersion",FORMAT_VERSION);
        root.remove("MasterCity");if(masterCityId!=null)root.putUUID("MasterCity",masterCityId);
        root.put("Wallets",writeUuidLongMap(wallets,"Player"));root.put("Treasuries",writeUuidLongMap(treasuries,"City"));
        root.put("PersonalBank",bank.save());root.put("CityProjects",projects.save());root.put("CargoCommerce",cargo.save());return root;
    }
}
