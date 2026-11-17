"""Original paint rifle: dedicated atlas, Java item model and portable Blockbench project."""
from pathlib import Path
import base64
import copy
import json
import uuid
from PIL import Image, ImageDraw
import build_cargo_models as preview

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT/'art/paintgun'
ASSETS = ROOT/'src/main/resources/assets/createcityexpansioncore'
MATERIALS = {'steel':(78,91,94), 'edge':(133,151,150), 'brass':(185,139,70),
             'rubber':(34,40,42), 'grip':(98,65,45), 'paint':(255,255,255),
             'bore':(16,23,25), 'gauge':(216,210,177)}
TILES = {name:((i%4)*32,(i//4)*32) for i,name in enumerate(MATERIALS)}

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'ccec/paintgun/'+name))
def save(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

def atlas():
    image=Image.new('RGBA',(128,128),(0,0,0,255)); d=ImageDraw.Draw(image)
    for name,color in MATERIALS.items():
        x,y=TILES[name]
        d.rectangle((x,y,x+31,y+31),fill=color+(255,))
        # Restrained machining lines; no borrowed block graphics on fittings.
        if name not in ('bore','gauge'):
            low=tuple(round(c*.88) for c in color)+(255,)
            hi=tuple(min(255,c+13) for c in color)+(255,)
            d.line((x+1,y+1,x+30,y+1),fill=hi)
            d.line((x+1,y+30,x+30,y+30),fill=low)
            for yy in ((7,15,23) if name in ('grip','rubber') else (23,)):
                d.line((x+3,y+yy,x+28,y+yy),fill=low)
        if name=='gauge':
            d.rectangle((x,y,x+31,y+31),fill='#253236')
            d.ellipse((x+3,y+3,x+28,y+28),fill=color+(255,))
            for px,py in ((7,15),(9,8),(16,6),(23,9),(25,16)):
                d.rectangle((x+px,y+py,x+px+1,y+py+2),fill='#5a6259')
            d.line((x+16,y+20,x+22,y+11),fill='#a95232',width=2)
            d.rectangle((x+14,y+18,x+17,y+21),fill='#354145')
    return image

def main():
    ART.mkdir(parents=True,exist_ok=True)
    tex=atlas(); tex.save(ART/'paintgun.png')
    tex.save(ASSETS/'textures/item/panel_painter.png')
    elements=[]; groups={}
    def cube(group,name,a,b,material,face_materials=None):
        faces={}
        for face in ('north','south','east','west','up','down'):
            mat=(face_materials or {}).get(face,material); x,y=TILES[mat]
            faces[face]={'uv':[x/8,y/8,(x+32)/8,(y+32)/8],'texture':'#atlas'}
            if mat=='paint': faces[face]['tintindex']=0
        elements.append({'name':name,'from':list(a),'to':list(b),'faces':faces})
        groups.setdefault(group,[]).append(uid(name))
    c=cube
    c('receiver','receiver',(5.5,7,1),(10.5,11.5,15),'steel')
    c('receiver','upper_rail',(5.8,11.5,.5),(10.2,12,15.5),'edge')
    c('receiver','lower_rail',(6,6.5,1),(10,7,15),'brass')
    for x in (5.25,10.5):
        c('receiver','side_cover_'+str(x),(x,7.6,6),(x+.25,10.8,14),'brass')
        for z in (6.4,13):
            c('fasteners',f'screw_{x}_{z}',(x-.06,8,z),(x+.31,8.6,z+.6),'edge')
    c('receiver','pressure_gauge',(10.5,8.2,1.8),(10.9,11.1,4.7),'brass',{'east':'gauge'})
    c('receiver','pressure_gauge_left',(5.1,8.2,1.8),(5.5,11.1,4.7),'brass',{'west':'gauge'})
    c('stock','stock_bridge',(6.6,8,15),(9.4,10.5,19),'edge')
    c('stock','stock_upper',(6,8,19),(10,11,24),'grip')
    c('stock','stock_heel',(6,5.5,22),(10,8,24),'grip')
    c('stock','rubber_butt',(5.8,5.3,24),(10.2,11.2,24.8),'rubber')
    c('grip','grip_neck',(6.5,5.3,12),(9.5,7,15),'brass')
    c('grip','grip',(6.5,.8,12.5),(9.5,5.3,15.5),'grip')
    c('grip','grip_foot',(6.2,.3,12.2),(9.8,.8,15.8),'rubber')
    c('trigger','trigger',(7.4,4.2,10.7),(8.6,6.5,11.4),'brass')
    c('trigger','guard_front',(7.2,3.5,8.5),(8.8,6.5,9.1),'steel')
    c('trigger','guard_bottom',(7.2,3,8.5),(8.8,3.5,12.5),'steel')
    c('barrel','barrel_core',(6.5,8,-6),(9.5,10.5,1),'edge')
    c('barrel','foregrip',(5.8,6.4,-2),(10.2,8,1),'rubber')
    for z in (-4.5,-3,-1.5):
        c('barrel','cooling_collar_'+str(z),(6.1,7.6,z),(9.9,10.9,z+.55),'steel')
    c('nozzle','nozzle_body',(6,7.6,-7.1),(10,10.9,-5.8),'brass')
    c('nozzle','nozzle_mouth',(6.6,8.2,-7.25),(9.4,10.3,-7.1),'bore')
    c('nozzle','spray_jet',(7.2,8.6,-7.4),(8.8,9.9,-7.25),'edge')
    c('nozzle','spray_slit',(7.7,8.8,-7.43),(8.3,9.7,-7.4),'bore')
    c('tank','feed_neck',(6.7,12,6),(9.3,13.5,8.6),'brass')
    # Stepped octagonal reservoir, with disjoint volumes and neutral tinted enamel.
    for name,a,b in [('center',(5.5,13.5,3.5),(10.5,21.5,10.5)),
                     ('left',(4.5,13.5,4.5),(5.5,21.5,9.5)),
                     ('right',(10.5,13.5,4.5),(11.5,21.5,9.5))]:
        c('tank','paint_reservoir_'+name,a,b,'paint')
    for y in (13.5,20.8):
        for name,a,b in [('front',(5.4,y,3.3),(10.6,y+.5,3.5)),
                         ('back',(5.4,y,10.5),(10.6,y+.5,10.7)),
                         ('left',(4.3,y,4.4),(4.5,y+.5,9.6)),
                         ('right',(11.5,y,4.4),(11.7,y+.5,9.6))]:
            c('tank_hardware',f'band_{y}_{name}',a,b,'brass')
    c('tank_hardware','tank_cap',(5.6,21.5,4.6),(10.4,22.2,9.4),'steel')
    c('tank_hardware','cap_latch',(7,22.2,5.5),(9,22.8,8.5),'brass')
    c('plumbing','pipe_horizontal',(10.6,10.9,8),(11.4,11.7,14),'brass')
    c('plumbing','pipe_riser',(10.6,11.7,8),(11.4,14.6,8.8),'brass')
    c('plumbing','valve_hub',(10.5,10,14),(12,11.5,15),'steel')
    c('plumbing','valve_handle',(11.8,9.4,13.6),(12.4,12.1,15.4),'grip')
    display={
        'gui':{'rotation':[20,120,-12],'translation':[0,-1,0],'scale':[.48]*3},
        'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.3]*3},
        'fixed':{'rotation':[0,90,0],'translation':[0,-1,0],'scale':[.5]*3},
        'firstperson_righthand':{'rotation':[0,0,0],'translation':[0,3,-3.6],'scale':[.60]*3},
        'firstperson_lefthand':{'rotation':[0,0,0],'translation':[0,3,-3.6],'scale':[.60]*3},
        'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,1.25,-2.4],'scale':[.65]*3},
        'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,1.25,-2.4],'scale':[.65]*3}}
    model={'credit':'CCEC paint rifle / art/paintgun/paintgun.bbmodel','parent':'minecraft:block/block',
           'gui_light':'side','textures':{'atlas':'createcityexpansioncore:item/panel_painter',
           'particle':'createcityexpansioncore:item/panel_painter'},'display':display,'elements':elements}
    save(ASSETS/'models/item/panel_painter.json',model)
    bb=[]
    for e in elements:
        bb.append({'name':e['name'],'uuid':uid(e['name']),'type':'cube','from':e['from'],'to':e['to'],
                   'origin':[8,8,8],'rotation':[0,0,0],'box_uv':False,'autouv':0,
                   'faces':{f:{'uv':[v*8 for v in d['uv']],'texture':0,
                       **({'tint':0} if 'tintindex' in d else {})} for f,d in e['faces'].items()}})
    save(ART/'paintgun.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},
         'name':'CCEC Paint Rifle','resolution':{'width':128,'height':128},'elements':bb,'display':display,
         'outliner':[{'name':g,'uuid':uid(g),'origin':[8,8,8],'rotation':[0,0,0],'children':ids} for g,ids in groups.items()],
         'textures':[{'name':'paintgun.png','id':'0','uuid':uid('atlas'),'width':128,'height':128,
                      'uv_width':128,'uv_height':128,'source':'data:image/png;base64,'+base64.b64encode((ART/'paintgun.png').read_bytes()).decode()}]})
    sheet=Image.new('RGBA',(1200,880),(28,34,39,255)); d=ImageDraw.Draw(sheet)
    for i,(name,color) in enumerate([('ORANGE',(249,128,29)),('LIGHT BLUE',(58,179,218)),('WHITE',(249,255,254)),('BLACK',(29,29,33))]):
        tinted=tex.copy(); ox,oy=TILES['paint']
        for y in range(oy,oy+32):
            for x in range(ox,ox+32):
                p=tex.getpixel((x,y));tinted.putpixel((x,y),tuple(round(p[j]*color[j]/255) for j in range(3))+(255,))
        preview.ATLAS=tinted
        small=copy.deepcopy(model)
        for e in small['elements']:
            for key in ('from','to'):
                e[key]=[8+(e[key][0]-8)*.8,7.5+(e[key][1]-11.5)*.8,8+(e[key][2]-8)*.8]
        shot=preview.render(small,590,390,yaw=-65,pitch=15)
        sheet.alpha_composite(shot,((i%2)*600,(i//2)*440+35))
        d.text(((i%2)*600+24,(i//2)*440+14),'CCEC / PAINT RIFLE / '+name,fill='#d6dedb',font_size=18)
    sheet.convert('RGB').save(ART/'preview.png')
    print(f'Paint rifle: {len(elements)} elements, dedicated 128px atlas, tinted tank, Blockbench source.')

if __name__=='__main__': main()
