package com.example.examplemod.auth;
import java.util.*;
/** Constant-bounded non-blocking producer; pending batch stays immutable until acknowledgement. */
public final class TelemetryBuffer<T> {
    private final int capacity;private final ArrayDeque<T> queue=new ArrayDeque<>();private List<T> pending=List.of();private long dropped;
    public TelemetryBuffer(int capacity){if(capacity<1)throw new IllegalArgumentException();this.capacity=capacity;}
    public synchronized boolean offer(T item){if(queue.size()+pending.size()>=capacity){dropped++;return false;}queue.addLast(Objects.requireNonNull(item));return true;}
    public synchronized List<T> batch(int limit){if(limit<1)throw new IllegalArgumentException();if(!pending.isEmpty())return pending;var values=new ArrayList<T>();while(!queue.isEmpty()&&values.size()<limit)values.add(queue.removeFirst());return pending=List.copyOf(values);}
    public synchronized void acknowledge(){pending=List.of();}
    /** Permanent validation rejection: account for lost events, retaining queued successors. */
    public synchronized void discardPending(){dropped+=pending.size();pending=List.of();}
    public synchronized long dropped(){return dropped;}
}
