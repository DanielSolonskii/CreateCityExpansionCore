package com.example.examplemod.economy;
import java.math.BigInteger;
import java.util.*;
/** Exact integer CR allocation: no floating point, inflation or lost rounding remainder. */
public final class FreightCostSharing{
 private FreightCostSharing(){}
 public static List<Long> split(long total,List<Long> weights){
  if(total<0||weights.isEmpty()||weights.stream().anyMatch(w->w<0))throw new IllegalArgumentException("Invalid cost split");
  var sum=weights.stream().map(BigInteger::valueOf).reduce(BigInteger.ZERO,BigInteger::add);if(sum.signum()==0){if(total!=0)throw new IllegalArgumentException("No weight");return weights.stream().map(w->0L).toList();}
  var result=new ArrayList<Long>();long allocated=0;for(long w:weights){long value=BigInteger.valueOf(total).multiply(BigInteger.valueOf(w)).divide(sum).longValueExact();result.add(value);allocated+=value;}
  for(int i=0;allocated<total;i=(i+1)%weights.size())if(weights.get(i)>0){result.set(i,result.get(i)+1);allocated++;}return List.copyOf(result);
 }
}
