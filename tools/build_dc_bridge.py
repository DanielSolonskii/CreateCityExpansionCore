"""Dedicated two-section DC converter; original atlas and editable Blockbench source."""
from pathlib import Path
import json, copy, base64, uuid
from PIL import Image, ImageDraw
import build_cargo_models as preview
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/createcityexpansioncore'
OUT=ROOT/'art/dc_power_bridge'
OUT.mkdir(parents=True,exist_ok=True)
COLORS={'iron':(63,65,68),'dark':(29,31,32),'brass':(190,143,65),'wood':(91,56,34),'copper':(177,91,52),'ceramic':(215,207,178),'screen':(11,25,17),'fin':(83,85,86),'plus':(215,207,178),'minus':(215,207,178),'screw':(157,119,59),'rim':(88,89,91)}
TILES={k:((i%4)*32,(i//4)*32) for i,k in enumerate(COLORS)}
def save(p,data):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'ccec/dc/'+s))
def main():
 tex=Image.new('RGBA',(128,128),(30,30,30,255));d=ImageDraw.Draw(tex)
 for k,c in COLORS.items():
  x,y=TILES[k];d.rectangle((x,y,x+31,y+31),fill=c+(255,))
  if k=='screen':continue
  for v in range(3,30,5):
   for u in range(2,30,7):
    delta=((u+v*3)%7)-3
    d.rectangle((x+u,y+v,x+u+3,y+v+1),fill=tuple(max(0,min(255,b+delta)) for b in c)+(255,))
  hi=tuple(min(255,b+12) for b in c);lo=tuple(max(0,b-14) for b in c)
  d.line((x,y+31,x,y,x+31,y),fill=hi,width=1);d.line((x+31,y,x+31,y+31,x,y+31),fill=lo,width=1)
  if k=='wood':
   for v in (10,21):d.line((x+1,y+v,x+30,y+v),fill=(65,39,25),width=1)
   for u,v in ((4,5),(16,15),(8,26)):d.line((x+u,y+v,x+u+8,y+v),fill=(111,72,43))
  if k=='screw':
   d.rectangle((x+7,y+7,x+24,y+24),fill=(192,151,79));d.line((x+11,y+20,x+20,y+11),fill=(81,68,46),width=3)
  if k in ('iron','copper','fin'):
   for u,v in ((3,7),(21,25)):d.line((x+u,y+v,x+u+6,y+v),fill=hi)
  if k in ('plus','minus'):
   d.rectangle((x+8,y+14,x+23,y+17),fill=(44,43,37))
   if k=='plus':d.rectangle((x+14,y+8,x+17,y+23),fill=(44,43,37))
 tex.save(OUT/'dc_power_bridge.png');tex.save(A/'textures/block/dc_power_bridge.png')
 all_elements=[]
 for tfmg in (False,True):
  el=[];h=10 if tfmg else 12
  def c(name,a,b,mat,front=None):
   faces={}
   for f in ('north','south','east','west','up','down'):
    x,y=TILES[front if f=='north' and front else mat]
    faces[f]={'uv':[x/8,y/8,(x+32)/8,(y+32)/8],'texture':'#atlas'}
   el.append({'name':name,'from':list(a),'to':list(b),'faces':faces})
  c('brown_cabinet',(1.5,1,2),(13.7 if tfmg else 14.5,h-1,14.5),'wood')
  c('base',(0,0,0.5),(16,1.5,15.5),'iron')
  c('roof',(0.5,h-1,.5),(15.5,h,15.5),'iron')
  for x in (.5,14):
   for z in (1,13.5):
    c(f'corner{x}{z}',(x,1,z),(x+1.5,h-1,z+1.5),'iron')
    c(f'foot{x}{z}',(x-.2,0,z-.2),(x+1.7,1.7,z+1.7),'dark')
    c(f'bolt{x}{z}',(x+.5,h,z+.5),(x+1,h+.2,z+1),'brass')
  bottom=2.5;top=9 if tfmg else 11
  c('glass',(2,bottom,.9),(14,top,1.1),'screen')
  for name,a,b in [('left',(1,bottom-.65,.45),(2,top+.65,1.6)),('right',(14,bottom-.65,.45),(15,top+.65,1.6)),('top',(2,top,.45),(14,top+.65,1.6)),('bottom',(2,bottom-.65,.45),(14,bottom,1.6))]:c('bezel_'+name,a,b,'brass')
  for x in (1.2,14.2):
   for y in (bottom-.45,top+.05):c(f'bezel_screw{x}{y}',(x,y,.25),(x+.6,y+.4,.45),'screw')
  c('base_inlay',(2,1.55,.6),(14,1.85,.9),'brass')
  c('service_handle_mount',(6,.35,.35),(10,1.25,.5),'brass')
  c('service_handle',(6.5,.6,.12),(9.5,1,.35),'rim')
  for x in (1,14.4):c('front_frame_strip'+str(x),(x,2,1),(x+.35,h-.8,1.2),'rim')
  # Three electrical terminals total; all remain inside the 2x1x1 volume.
  for index,x in enumerate((8,) if tfmg else (4,12)):
   c('terminal_mount'+str(index),(x-2.2,h,8),(x+2.2,h+.35,12),'brass')
   c('insulator_base'+str(index),(x-1.9,h+.35,8.25),(x+1.9,h+1.4,11.75),'ceramic',None if tfmg else ('plus' if index else 'minus'))
   c('insulator_neck'+str(index),(x-1.35,h+1.4,8.8),(x+1.35,h+2.2,11.2),'ceramic')
   c('copper_contact_base'+str(index),(x-.85,h+2.2,9.15),(x+.85,h+2.6,10.85),'copper')
   for suffix,a,b in [('front',(x-.85,h+2.6,9.15),(x+.85,h+3.8,9.55)),('back',(x-.85,h+2.6,10.45),(x+.85,h+3.8,10.85)),('left',(x-.85,h+2.6,9.55),(x-.4,h+3.8,10.45)),('right',(x+.4,h+2.6,9.55),(x+.85,h+3.8,10.45))]:c('contact_'+str(index)+suffix,a,b,'copper')
   for dx in (-1.85,1.45):c(f'terminal_bolt{index}{dx}',(x+dx,h+.35,8.1),(x+dx+.4,h+.55,8.5),'screw')
  if tfmg:
   # Outer side (+X) copper winding with dark core and two protective bars.
   c('coil_recess',(13.7,2,3.5),(13.85,9,12.5),'dark')
   c('coil_core',(13.85,2.1,6),(15.25,8.7,10),'iron')
   for y in (2.5,3.8,5.1,6.4,7.7):
    for n,a,b in [('inner',(13.85,y,4.3),(14.5,y+.65,11.7)),('step',(14.5,y,4.8),(15.1,y+.65,11.2)),('outer',(15.1,y,5.5),(15.8,y+.65,10.5))]:c('winding'+str(y)+n,a,b,'copper')
   for y in (2.1,8.5):
    c('coil_guard'+str(y),(15.8,y,3.5),(15.94,y+.45,12.5),'brass')
    for z in (3.5,12):c('coil_guard_bolt'+str(y)+str(z),(15.95,y,z),(16,y+.4,z+.4),'screw')
   for z in (3.5,12):c('coil_frame'+str(z),(14.5,2,z),(15.5,9,z+.5),'iron')
   c('joining_brace',(0,10.25,5.5),(3,11,7.5),'brass')
   c('joining_support',(2,10,5.5),(3,10.25,7.5),'brass')
  else:
   c('heatsink_base',(.7,2.55,3.55),(1.5,9.45,12.45),'dark')
   for z in (4,5.5,7,8.5,10,11.5):
    c('cooling_fin'+str(z),(.1,3,z),(.7,9,z+.4),'fin')
    c('fin_foot'+str(z),(.35,2.5,z),(.7,3,z+.4),'fin')
    c('fin_shoulder'+str(z),(.35,9,z),(.7,9.5,z+.4),'fin')
   for y in (2,9.5):c('radiator_guard'+str(y),(0,y,3),(1,y+.5,13),'brass')
   for z in (3,12.5):c('radiator_frame'+str(z),(0,2.5,z),(.45,9.5,z+.5),'brass')
   c('joining_brace',(14.5,10.25,5.5),(16,11,7.5),'brass')
   c('joining_bracket',(14.25,9.8,5.2),(15,11.45,7.8),'brass')
  c('rear_service_door',(3,2,14.5),(13,h-2,14.8),'wood')
  c('rear_vent_recess',(4,3,14.8),(12,6.7,14.9),'dark')
  for y in (3.3,4.3,5.3,6.3):c('back_vent'+str(y),(4.3,y,14.9),(11.7,y+.3,15.2),'rim')
  for x in (3,12.5):
   for y in (2.3,h-2.6):c(f'rear_screw{x}{y}',(x,y,14.8),(x+.5,y+.5,15),'screw')
  for y in (3,h-3):c('door_hinge'+str(y),(2.6,y,14.6),(3,y+1,15.1),'brass')
  name='dc_power_bridge_tfmg' if tfmg else 'dc_power_bridge'
  model={'parent':'minecraft:block/block','ambientocclusion':True,'textures':{'atlas':'createcityexpansioncore:block/dc_power_bridge','particle':'createcityexpansioncore:block/dc_power_bridge'},'elements':el}
  save(A/f'models/block/{name}.json',model)
  for e in copy.deepcopy(el):
   e['name']=('TFMG_' if tfmg else 'EE_')+e['name']
   if tfmg:
    e['from'][0]+=16;e['to'][0]+=16
   all_elements.append(e)
 variants={}
 for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
  for tfmg in (False,True):variants[f'facing={face},tfmg={str(tfmg).lower()}']={'model':'createcityexpansioncore:block/dc_power_bridge'+('_tfmg' if tfmg else ''),'y':rot}
 save(A/'blockstates/dc_power_bridge.json',{'variants':variants})
 display={'gui':{'rotation':[25,225,0],'translation':[-3,-1,0],'scale':[.42]*3},'ground':{'translation':[-4,2,0],'scale':[.3]*3},'fixed':{'translation':[-4,0,0],'scale':[.4]*3},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[-3,2,1],'scale':[.3]*3},'firstperson_righthand':{'rotation':[0,45,0],'translation':[-2,0,0],'scale':[.35]*3}}
 item={'parent':'minecraft:block/block','textures':model['textures'],'display':display,'elements':all_elements}
 save(A/'models/item/dc_power_bridge.json',item)
 bb=[]
 for e in all_elements:
  bb.append({'name':e['name'],'uuid':uid(e['name']),'type':'cube','from':e['from'],'to':e['to'],'origin':[16,8,8],'rotation':[0,0,0],'box_uv':False,'autouv':0,'faces':{f:{'uv':[v*8 for v in d['uv']],'texture':0} for f,d in e['faces'].items()}})
 save(OUT/'dc_power_bridge.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':'CCEC dual DC converter','resolution':{'width':128,'height':128},'elements':bb,'outliner':[e['uuid'] for e in bb],'textures':[{'name':'dc_power_bridge.png','id':'0','uuid':uid('atlas'),'width':128,'height':128,'uv_width':128,'uv_height':128,'source':'data:image/png;base64,'+base64.b64encode((OUT/'dc_power_bridge.png').read_bytes()).decode()}],'display':display})
 preview.ATLAS=tex
 small=copy.deepcopy(item)
 for e in small['elements']:
  for key in ('from','to'):e[key]=[(e[key][0]-16)*.65+8,(e[key][1]-8)*.65+8,(e[key][2]-8)*.65+8]
 sheet=Image.new('RGBA',(1400,650),(239,233,220,255))
 sheet.alpha_composite(preview.render(small,900,650,yaw=30),(0,0))
 sheet.alpha_composite(preview.render(small,500,500,yaw=-145),(900,80))
 sheet.save(OUT/'preview.png')
 loot=ROOT/'src/main/resources/data/createcityexpansioncore/loot_table/blocks/dc_power_bridge.json'
 data=json.loads(loot.read_text());data['pools'][0]['conditions']=[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'createcityexpansioncore:dc_power_bridge','properties':{'tfmg':'false'}}];save(loot,data)
 assert all(0<=e['from'][i]<e['to'][i]<=([32,16,16][i]) for e in all_elements for i in range(3))
 print(f'PASS: {len(all_elements)} elements, 3 terminals, 2 screens, bounds 32x16x16; Blockbench + models + atlas')
if __name__=='__main__':main()
