"""Deterministic full Ponder scene; no world/server data."""
import gzip,struct
from pathlib import Path
P=Path(__file__).resolve().parents[1]/'src/main/resources/assets/createcityexpansioncore/ponder'
blocks={}
def put(x,y,z,name,props=None,nbt=None):blocks[x,y,z]=(name,props or {},nbt)
for x in range(13):
 for z in range(11):put(x,0,z,'minecraft:polished_andesite')
for z,first,last in [(2,3,5),(8,7,9)]:
 for i,x in enumerate(range(first,last+1)):
  part='start' if i==0 else 'end' if x==last else 'middle'
  put(x,1,z,'create:belt',{'facing':'east','slope':'horizontal','part':part,'casing':'false','waterlogged':'false'}, {'id':'create:belt','Controller':(first,1,z),'Length':last-first+1,'Index':i,'Speed':-32.0})
 put(first,2,z,'create:andesite_funnel',{'facing':'east','extracting':'true'})
 put(last,2,z,'create:andesite_funnel',{'facing':'west','extracting':'false'})
put(1,2,2,'minecraft:barrel');put(2,2,2,'create:packager',{'facing':'east'})
put(6,2,2,'create:white_postbox',{'facing':'west'})
put(6,2,8,'create:white_postbox',{'facing':'east'})
put(10,2,8,'create:packager',{'facing':'west'});put(11,2,8,'minecraft:barrel')
for pos in [(1,1,2),(2,1,2),(6,1,2),(6,1,8),(10,1,8),(11,1,8)]:put(*pos,'create:metal_girder')
for x in range(13):put(x,1,5,'create:track',{'shape':'xo'})
put(6,1,4,'create:track_station');put(6,1,6,'create:track_station')
put(3,2,5,'create:small_bogey',{'axis':'x'});put(3,3,5,'minecraft:barrel');put(2,3,5,'create:train_controls',{'facing':'east'})
put(4,3,5,'minecraft:barrel')
put(1,2,1,'create:packager',{'facing':'north'});put(1,1,1,'create:metal_girder')
put(1,2,0,'create:factory_gauge',{'face':'wall','facing':'north','powered':'false','waterlogged':'false'},{'id':'create:factory_gauge','Restocker':1})
put(2,3,2,'create:stock_link');put(4,1,0,'create:stock_ticker')
# Vault represents the centre's actual receiving buffer, with visible connected storage.
for x in range(9,12):put(x,1,9,'create:item_vault',{'axis':'x'})
def payload(t,v):
 if t==3:return struct.pack('>i',v)
 if t==5:return struct.pack('>f',v)
 if t==8:
  b=v.encode();return struct.pack('>H',len(b))+b
 if t==11:return struct.pack('>i',len(v))+b''.join(struct.pack('>i',n) for n in v)
 if t==9:
  subtype,seq=v;return bytes([subtype])+struct.pack('>i',len(seq))+b''.join(payload(subtype,n) for n in seq)
 if t==10:return b''.join(bytes([kind])+payload(8,k)+payload(kind,item) for k,(kind,item) in v.items())+b'\0'
 raise ValueError(t)
def compound(d):return {k:(10,compound(v)) if isinstance(v,dict) else (11,v) if isinstance(v,tuple) else (8,v) if isinstance(v,str) else (5,v) if isinstance(v,float) else (3,v) for k,v in d.items()}
palette=[];entries=[]
for pos,(name,props,nbt) in sorted(blocks.items()):
 state={'Name':name}
 if props:state['Properties']=props
 if state not in palette:palette.append(state)
 item={'pos':(9,(3,pos)),'state':(3,palette.index(state))}
 if nbt:item['nbt']=(10,compound(nbt))
 entries.append(item)
root={'DataVersion':(3,3955),'size':(9,(3,[13,6,11])),'palette':(9,(10,[compound(v) for v in palette])),'blocks':(9,(10,entries)),'entities':(9,(10,[]))}
P.mkdir(parents=True,exist_ok=True)
(P/'freight_terminal.nbt').write_bytes(gzip.compress(bytes([10])+payload(8,'')+payload(10,root),mtime=0))
print(f'Ponder v2: {len(entries)} blocks, {len(palette)} states, full equipment bounds')
