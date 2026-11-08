package com.example.examplemod.economy;

import net.minecraft.nbt.*;
import java.util.*;

/** Personal banking within EconomyManager's save. The primary account IS the existing wallet. */
public final class BankBook {
    public enum Action { VIEW, RESOLVE, CREATE, EDIT, CLOSE, TRANSFER, REQUEST, PAY_REQUEST, DECLINE, CANCEL }
    public enum Kind { SAVINGS, TARGET }
    public enum State { OPEN, PAID, DECLINED, CANCELLED }
    public interface Wallet {long balance(UUID owner);boolean debit(UUID owner,long amount);boolean canCredit(UUID owner,long amount);void credit(UUID owner,long amount);long maximum();void dirty();}
    public record Account(UUID id,UUID owner,String name,Kind kind,long balance,long goal){public Account{Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(kind);if(!text(name,32)||balance<0||goal<0)throw new IllegalArgumentException("Bank account");}}
    public record Payment(UUID id,UUID owner,UUID payer,long amount,String note,long created,long expires,State state){public Payment{if(id==null||owner==null||payer==null||owner.equals(payer)||amount<=0||!text(note,96)||created<0||expires<created||state==null)throw new IllegalArgumentException("Payment request");}Payment settled(State s){return new Payment(id,owner,payer,amount,note,created,expires,s);}}
    public record Entry(UUID id,long time,String kind,long amount,UUID peer,String note){ }
    public record Command(UUID nonce,long revision,Action action,UUID source,UUID destination,UUID peer,long amount,long goal,String text,int kind){ }
    private final Wallet wallet;private final Map<UUID,Account> accounts=new LinkedHashMap<>();private final Map<UUID,Payment> requests=new LinkedHashMap<>();
    private final Map<UUID,Long> revisions=new LinkedHashMap<>();private final Map<UUID,LinkedHashSet<UUID>> receipts=new LinkedHashMap<>();private final LinkedHashMap<UUID,List<Entry>> history=new LinkedHashMap<>();
    private boolean banking;
    public BankBook(Wallet wallet){this.wallet=Objects.requireNonNull(wallet);}
    public long revision(UUID owner){return revisions.getOrDefault(owner,0L);}
    public List<Account> accounts(UUID owner){return accounts.values().stream().filter(a->a.owner.equals(owner)).toList();}
    public List<Payment> requests(UUID owner){return requests.values().stream().filter(r->r.owner.equals(owner)||r.payer.equals(owner)).sorted(Comparator.comparingInt((Payment r)->r.state==State.OPEN&&r.expires>System.currentTimeMillis()?0:1).thenComparing(Comparator.comparingLong(Payment::created).reversed()).thenComparing(Payment::id)).limit(64).toList();}
    public List<Entry> history(UUID owner){return List.copyOf(history.getOrDefault(owner,List.of()));}
    public long balance(UUID owner,UUID id){if(id==null)return wallet.balance(owner);var a=accounts.get(id);return a!=null&&a.owner.equals(owner)?a.balance:-1;}
    private void revisionUp(UUID owner){revisions.put(owner,revision(owner)==Long.MAX_VALUE?Long.MAX_VALUE:revision(owner)+1);}
    public static boolean text(String value,int maximum){return value!=null&&!value.isBlank()&&value.length()<=maximum&&value.chars().noneMatch(c->Character.isISOControl(c)||c==167);}
    public void observeWallet(UUID owner,long before,long after){if(banking||before==after)return;revisionUp(owner);entry(owner,new Entry(UUID.randomUUID(),System.currentTimeMillis(),after>before?"system_in":"system_out",after-before,null,""));}
    private void entry(UUID owner,Entry entry){var list=history.computeIfAbsent(owner,k->new ArrayList<>());list.addFirst(entry);while(list.size()>100)list.removeLast();while(history.size()>4096)history.remove(history.keySet().iterator().next());}
    private boolean creditable(UUID owner,UUID id,long amount){long value=balance(owner,id);return value>=0&&value<=Long.MAX_VALUE-amount&&(id!=null||wallet.canCredit(owner,amount));}
    private boolean debit(UUID owner,UUID id,long amount){if(id==null)return wallet.debit(owner,amount);var a=accounts.get(id);accounts.put(id,new Account(a.id,a.owner,a.name,a.kind,a.balance-amount,a.goal));return true;}
    private void credit(UUID owner,UUID id,long amount){if(id==null)wallet.credit(owner,amount);else{var a=accounts.get(id);accounts.put(id,new Account(a.id,a.owner,a.name,a.kind,Math.addExact(a.balance,amount),a.goal));}}
    /** Validate completely before the first balance mutation. Expected revisions remain replay-safe after receipt pruning. */
    public synchronized String execute(UUID owner,Command c,long now){
        if(owner==null||c==null||c.nonce==null||c.action==null||c.text==null)return "invalid";
        if(c.action==Action.VIEW||c.action==Action.RESOLVE)return "ok";
        if(receipts.getOrDefault(owner,new LinkedHashSet<>()).contains(c.nonce))return "duplicate";
        if(c.revision!=revision(owner))return "stale";
        if(revision(owner)==Long.MAX_VALUE)return "limit";
        UUID changedPeer=null;String kind="";long delta=0;String note=c.text;Payment payment=null;
        if(c.action==Action.CREATE){if(accounts.containsKey(c.nonce)||accounts(owner).size()>=8||!text(c.text,32)||c.kind<0||c.kind>=Kind.values().length||c.goal<0)return "invalid";}
        else if(c.action==Action.EDIT||c.action==Action.CLOSE){var a=accounts.get(c.source);if(a==null||!a.owner.equals(owner))return "permission";if(c.action==Action.CLOSE&&a.balance!=0)return "not_empty";if(c.action==Action.EDIT&&(!text(c.text,32)||c.goal<0))return "invalid";}
        else if(c.action==Action.REQUEST){if(requests.containsKey(c.nonce)||c.peer==null||owner.equals(c.peer)||c.amount<=0||c.amount>wallet.maximum()||!text(c.text,96))return "invalid";if(requests.values().stream().filter(r->(r.owner.equals(owner)||r.payer.equals(owner))&&r.state==State.OPEN&&r.expires>now).count()>=32||requests.values().stream().filter(r->(r.owner.equals(c.peer)||r.payer.equals(c.peer))&&r.state==State.OPEN&&r.expires>now).count()>=32)return "limit";changedPeer=c.peer;}
        else if(c.action==Action.TRANSFER||c.action==Action.PAY_REQUEST){
            UUID receiver=c.peer==null?owner:c.peer;UUID destination=c.destination;long amount=c.amount;
            if(c.action==Action.PAY_REQUEST){payment=requests.get(c.destination);if(payment==null||!payment.payer.equals(owner))return "permission";if(payment.state!=State.OPEN||payment.expires<=now)return "closed";receiver=payment.owner;destination=null;amount=payment.amount;note=payment.note;}
            if(amount<=0||amount>wallet.maximum()||note.length()>96||note.chars().anyMatch(v->Character.isISOControl(v)||v==167)||!receiver.equals(owner)&&destination!=null||receiver.equals(owner)&&Objects.equals(c.source,destination))return "invalid";
            if(balance(owner,c.source)<0)return "permission";if(balance(owner,c.source)<amount)return "funds";if(!creditable(receiver,destination,amount))return "overflow";
            if(!receiver.equals(owner)&&revision(receiver)==Long.MAX_VALUE)return "limit";
            banking=true;try{if(!debit(owner,c.source,amount))return "funds";credit(receiver,destination,amount);}finally{banking=false;}
            delta=receiver.equals(owner)?0:-amount;kind=receiver.equals(owner)?"move":"transfer_out";
            if(!receiver.equals(owner)){changedPeer=receiver;entry(receiver,new Entry(c.nonce,now,"transfer_in",amount,owner,note));}
            if(payment!=null)requests.put(payment.id,payment.settled(State.PAID));
        }else{payment=requests.get(c.destination);if(payment==null)return "missing";if(payment.state!=State.OPEN)return "closed";if(c.action==Action.CANCEL?!payment.owner.equals(owner):c.action!=Action.DECLINE||!payment.payer.equals(owner))return "permission";changedPeer=payment.owner.equals(owner)?payment.payer:payment.owner;}
        switch(c.action){
            case CREATE->{var id=c.nonce;accounts.put(id,new Account(id,owner,c.text.strip(),Kind.values()[c.kind],0,c.goal));kind="create";}
            case EDIT->{var a=accounts.get(c.source);accounts.put(a.id,new Account(a.id,a.owner,c.text.strip(),a.kind,a.balance,c.goal));kind="edit";}
            case CLOSE->{note=accounts.remove(c.source).name;kind="close";}
            case REQUEST->{requests.put(c.nonce,new Payment(c.nonce,owner,c.peer,c.amount,c.text.strip(),now,Math.addExact(now,7L*24*60*60*1000),State.OPEN));kind="request";}
            case DECLINE,CANCEL->{requests.put(payment.id,payment.settled(c.action==Action.CANCEL?State.CANCELLED:State.DECLINED));kind=c.action.name().toLowerCase(Locale.ROOT);note=payment.note;}
            default->{}
        }
        if(c.action==Action.TRANSFER&&delta==0)note=accountName(owner,c.source)+" → "+accountName(owner,c.destination)+" · "+c.amount+" CR"+(c.text.isBlank()?"":" · "+c.text);
        entry(owner,new Entry(c.nonce,now,kind,delta,changedPeer,note));revisionUp(owner);if(changedPeer!=null)revisionUp(changedPeer);
        var seen=receipts.computeIfAbsent(owner,k->new LinkedHashSet<>());seen.add(c.nonce);while(seen.size()>256)seen.remove(seen.iterator().next());
        requests.entrySet().removeIf(e->e.getValue().expires<now-30L*24*60*60*1000);wallet.dirty();return "ok";
    }
    private String accountName(UUID owner,UUID id){return id==null?"CR":accounts.get(id).name;}
    public CompoundTag save(){var root=new CompoundTag();root.putInt("Version",1);var list=new ListTag();for(var a:accounts.values()){var t=new CompoundTag();t.putUUID("Id",a.id);t.putUUID("Owner",a.owner);t.putString("Name",a.name);t.putString("Kind",a.kind.name());t.putLong("Balance",a.balance);t.putLong("Goal",a.goal);list.add(t);}root.put("Accounts",list);list=new ListTag();for(var p:requests.values()){var t=new CompoundTag();t.putUUID("Id",p.id);t.putUUID("Owner",p.owner);t.putUUID("Payer",p.payer);t.putLong("Amount",p.amount);t.putString("Note",p.note);t.putLong("Created",p.created);t.putLong("Expires",p.expires);t.putString("State",p.state.name());list.add(t);}root.put("Requests",list);list=new ListTag();for(var owner:revisions.keySet()){var t=new CompoundTag();t.putUUID("Owner",owner);t.putLong("Revision",revision(owner));var seen=new ListTag();receipts.getOrDefault(owner,new LinkedHashSet<>()).forEach(id->seen.add(NbtUtils.createUUID(id)));t.put("Receipts",seen);var entries=new ListTag();for(var e:history(owner)){var n=new CompoundTag();n.putUUID("Id",e.id);n.putLong("Time",e.time);n.putString("Kind",e.kind);n.putLong("Amount",e.amount);if(e.peer!=null)n.putUUID("Peer",e.peer);n.putString("Note",e.note);entries.add(n);}t.put("History",entries);list.add(t);}root.put("Owners",list);return root;}
    public void load(CompoundTag root){if(root.getInt("Version")>1)throw new IllegalStateException("Unsupported banking version");accounts.clear();requests.clear();revisions.clear();receipts.clear();history.clear();for(Tag raw:root.getList("Accounts",10)){var t=(CompoundTag)raw;var a=new Account(t.getUUID("Id"),t.getUUID("Owner"),t.getString("Name"),Kind.valueOf(t.getString("Kind")),t.getLong("Balance"),t.getLong("Goal"));if(accounts.putIfAbsent(a.id,a)!=null||accounts(a.owner).size()>8)throw new IllegalStateException("Invalid bank accounts; funds preserved by refusing load");}for(Tag raw:root.getList("Requests",10)){var t=(CompoundTag)raw;var p=new Payment(t.getUUID("Id"),t.getUUID("Owner"),t.getUUID("Payer"),t.getLong("Amount"),t.getString("Note"),t.getLong("Created"),t.getLong("Expires"),State.valueOf(t.getString("State")));if(requests.putIfAbsent(p.id,p)!=null)throw new IllegalStateException("Duplicate payment request");}for(Tag raw:root.getList("Owners",10)){var t=(CompoundTag)raw;UUID owner=t.getUUID("Owner");long revision=t.getLong("Revision");if(revision<0||revisions.putIfAbsent(owner,revision)!=null)throw new IllegalStateException("Invalid bank revision");var seen=new LinkedHashSet<UUID>();for(Tag n:t.getList("Receipts",11)){if(seen.size()>=256)break;seen.add(NbtUtils.loadUUID(n));}receipts.put(owner,seen);var list=new ArrayList<Entry>();for(Tag n:t.getList("History",10)){var e=(CompoundTag)n;if(list.size()<100)list.add(new Entry(e.getUUID("Id"),e.getLong("Time"),e.getString("Kind"),e.getLong("Amount"),e.hasUUID("Peer")?e.getUUID("Peer"):null,e.getString("Note")));}if(!list.isEmpty())history.put(owner,list);}}
}
