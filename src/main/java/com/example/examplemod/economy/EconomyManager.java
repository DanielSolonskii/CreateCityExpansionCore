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
    private static final int FORMAT_VERSION=17;
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
    private final Map<UUID,Long> companyAccounts = new LinkedHashMap<>();
    private final com.example.examplemod.cargo.center.CargoCommerceBook.Accounts financialAccounts = new com.example.examplemod.cargo.center.CargoCommerceBook.Accounts(){
        private Map<UUID,Long> balances(com.example.examplemod.cargo.center.OwnerRef o){return switch(o.kind()){case PLAYER->wallets;case CITY->treasuries;case COMPANY->companyAccounts;case FOREIGN_TRADE->throw new IllegalArgumentException("Dedicated foreign trade account");};}
        private boolean foreign(com.example.examplemod.cargo.center.OwnerRef o){if(o.kind()!=com.example.examplemod.cargo.center.OwnerRef.Kind.FOREIGN_TRADE)return false;if(!com.example.examplemod.cargo.center.MasterCityTradeBook.ACCOUNT_ID.equals(o.id()))throw new IllegalArgumentException("Unknown foreign trade account");return true;}
        public long balance(com.example.examplemod.cargo.center.OwnerRef o){return foreign(o)?masterTrade.balance():balances(o).getOrDefault(o.id(),0L);}
        public boolean debit(com.example.examplemod.cargo.center.OwnerRef o,long n){return n>=0&&(foreign(o)?masterTrade.debit(n):EconomyManager.debit(balances(o),o.id(),n));}
        public boolean canCredit(com.example.examplemod.cargo.center.OwnerRef o,long n){return n>=0&&(foreign(o)?masterTrade.canCredit(n):EconomyManager.canCredit(balances(o),o.id(),n));}
        public void credit(com.example.examplemod.cargo.center.OwnerRef o,long n){if(n<0)throw new IllegalArgumentException("Negative credit");if(foreign(o))masterTrade.credit(n);else EconomyManager.credit(balances(o),o.id(),n);setDirty();}
        public boolean canReceiveRevenue(com.example.examplemod.cargo.center.OwnerRef o,long n){return switch(o.kind()){case COMPANY->companies.canReceiveRevenue(o.id(),n);case FOREIGN_TRADE->n>=0;default->canCredit(o,n);};}
        public boolean canReceivePayments(Map<com.example.examplemod.cargo.center.OwnerRef,Long> revenue,Map<com.example.examplemod.cargo.center.OwnerRef,Long> refunds){
            try{var credits=new HashMap<com.example.examplemod.cargo.center.OwnerRef,Long>();for(var e:revenue.entrySet()){if(e.getValue()<0)return false;if(e.getKey().kind()==com.example.examplemod.cargo.center.OwnerRef.Kind.COMPANY)companies.revenueCredits(e.getKey().id(),e.getValue()).forEach((owner,n)->credits.merge(owner,n,Math::addExact));else if(e.getKey().kind()!=com.example.examplemod.cargo.center.OwnerRef.Kind.FOREIGN_TRADE)credits.merge(e.getKey(),e.getValue(),Math::addExact);else foreign(e.getKey());}for(var e:refunds.entrySet()){if(e.getValue()<0)return false;credits.merge(e.getKey(),e.getValue(),Math::addExact);}return credits.entrySet().stream().allMatch(e->canCredit(e.getKey(),e.getValue()));}catch(RuntimeException invalid){return false;}
        }
        public void revenue(UUID receipt,com.example.examplemod.cargo.center.OwnerRef owner,long n){switch(owner.kind()){case COMPANY->companies.revenue(receipt,owner.id(),n,System.currentTimeMillis());case FOREIGN_TRADE->{foreign(owner);masterTrade.importSale(receipt,n);}default->credit(owner,n);}setDirty();}
        public void revenue(com.example.examplemod.cargo.center.OwnerRef owner,long n){revenue(UUID.randomUUID(),owner,n);}
        public com.example.examplemod.cargo.center.OwnerRef saleRecipient(UUID offer,com.example.examplemod.cargo.center.OwnerRef seller){return masterTrade.isImportOffer(offer)?new com.example.examplemod.cargo.center.OwnerRef(com.example.examplemod.cargo.center.OwnerRef.Kind.FOREIGN_TRADE,com.example.examplemod.cargo.center.MasterCityTradeBook.ACCOUNT_ID):seller;}
        public long maximum(){return Config.ECONOMY_MAX_TRANSACTION.get();}
        public void dirty(){setDirty();}
    };
    private final com.example.examplemod.economy.company.CompanyBook companies = new com.example.examplemod.economy.company.CompanyBook(financialAccounts);
    private final com.example.examplemod.cargo.center.RailTollBook roadTolls = new com.example.examplemod.cargo.center.RailTollBook(financialAccounts);
    private final com.example.examplemod.cargo.center.MasterCityTradeBook masterTrade = new com.example.examplemod.cargo.center.MasterCityTradeBook(new com.example.examplemod.cargo.center.MasterCityTradeBook.Accounts(){public long maximum(){return financialAccounts.maximum();}public void dirty(){setDirty();}});
    private final com.example.examplemod.cargo.center.CargoCommerceBook cargo = new com.example.examplemod.cargo.center.CargoCommerceBook(financialAccounts);
    public EconomyManager(){companies.externalDebt(owner->roadTolls.totalDebt(owner));}
    public com.example.examplemod.cargo.center.CargoCommerceBook.Accounts accounts(){return financialAccounts;}
    public com.example.examplemod.economy.company.CompanyBook companies(){return companies;}
    public com.example.examplemod.cargo.center.RailTollBook roadTolls(){return roadTolls;}
    public com.example.examplemod.cargo.center.MasterCityTradeBook masterTrade(){return masterTrade;}
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

    private static void readCompanyAccounts(CompoundTag root,EconomyManager manager){
        if(!root.contains("CompanyAccounts"))return;
        if(!(root.get("CompanyAccounts") instanceof ListTag list))throw new IllegalArgumentException("Invalid company account list");
        for(Tag raw:list){if(!(raw instanceof CompoundTag tag)||!tag.hasUUID("Company")||!tag.contains("Amount",Tag.TAG_LONG)||tag.getLong("Amount")<=0)throw new IllegalArgumentException("Invalid company balance");UUID id=tag.getUUID("Company");if(manager.companies.find(id).isEmpty()||manager.companyAccounts.putIfAbsent(id,tag.getLong("Amount"))!=null)throw new IllegalArgumentException("Unknown or duplicate company balance");}
    }

    private static EconomyManager load(CompoundTag root,HolderLookup.Provider registries){
        int version=root.getInt("FormatVersion");
        if(version>FORMAT_VERSION)throw new IllegalStateException("Unsupported economy save format "+version);
        var manager=new EconomyManager();manager.retainedData=root.copy();manager.loadingBalances=true;
        manager.masterCityId=root.hasUUID("MasterCity")?root.getUUID("MasterCity"):null;
        readUuidLongMap(root,"Wallets","Player",manager.wallets);
        readUuidLongMap(root,"Treasuries","City",manager.treasuries);
        manager.companies.load(root.getCompound("Companies"));manager.roadTolls.load(root.getCompound("RoadTolls"));manager.masterTrade.load(root.getCompound("MasterCityTrade"));
        readCompanyAccounts(root,manager);
        manager.projects.load(root.getCompound("CityProjects"));manager.bank.load(root.getCompound("PersonalBank"));
        manager.cargo.load(root.getCompound("CargoCommerce"));
        manager.loadingBalances=false;return manager;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        if(unavailableData!=null)return unavailableData.copy();
        root.merge(retainedData.copy());root.putInt("FormatVersion",FORMAT_VERSION);
        root.remove("MasterCity");if(masterCityId!=null)root.putUUID("MasterCity",masterCityId);
        root.put("Wallets",writeUuidLongMap(wallets,"Player"));root.put("Treasuries",writeUuidLongMap(treasuries,"City"));
        root.put("CompanyAccounts",writeUuidLongMap(companyAccounts,"Company"));root.put("Companies",companies.save());root.put("RoadTolls",roadTolls.save());root.put("MasterCityTrade",masterTrade.save());
        root.put("PersonalBank",bank.save());root.put("CityProjects",projects.save());root.put("CargoCommerce",cargo.save());return root;
    }
}
