"""Industrial Mine source, runtime parts and exact-geometry previews.
Python + Pillow + numpy. Coordinates in blocks relative to NORTH Controller.
Runtime JSON coordinates are reduced by 8 to respect Java's [-16,32] limit;
the renderer restores scale 8. UVs remain independent of geometry scale.
"""
from pathlib import Path
import base64, json, math, uuid
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'art/industrial_mine'
ASSETS = ROOT/'src/main/resources/assets/createcityexpansioncore'
PARTS = ASSETS/'models/block/industrial_mine'
OUT.mkdir(exist_ok=True, parents=True)
PARTS.mkdir(exist_ok=True, parents=True)
SCALE = 8
PALETTE = {
    'steel':[(48,54,57),(58,64,67),(67,73,75),(88,95,96)],
    'edge':[(72,77,76),(92,99,96),(117,123,117),(146,151,138)],
    'brass':[(114,71,49),(158,105,71),(206,160,90),(247,203,108)],
    'copper':[(99,54,44),(154,80,56),(178,98,71),(214,123,91)],
    'plate':[(38,44,47),(49,56,60),(60,67,70),(94,99,95)],
    'gear':[(89,83,70),(133,121,89),(182,158,103),(217,189,121)],
    'shaft':[(68,74,75),(93,102,102),(125,135,132),(155,163,150)],
    'dark':[(21,26,29),(29,35,38),(37,43,46),(61,65,65)],
    'warning':[(36,39,39),(159,115,47),(204,157,62),(231,187,82)],
    'ceramic':[(92,98,96),(132,140,133),(175,181,164),(203,207,184)],
    'screen':[(9,23,27),(17,40,43),(22,56,59),(37,83,83)],
    'green':[(22,54,39),(34,86,56),(60,130,82),(112,175,113)],
    'amber':[(73,43,18),(125,73,25),(204,136,46),(235,178,81)],
    'red':[(63,24,23),(101,34,30),(170,52,40),(211,89,66)],
    'grate':[(28,33,34),(57,64,66),(87,93,92),(115,123,118)],
    'rubber':[(20,23,23),(27,30,30),(35,38,38),(49,51,48)]}
atlas=Image.new('RGBA',(64,64)); tiles={}
for i,(name,p) in enumerate(PALETTE.items()):
    tx,ty=i%4*16,i//4*16; tiles[name]=(tx,ty)
    tile=Image.new('RGBA',(16,16),(*p[1],255));d=ImageDraw.Draw(tile)
    for y in range(0,16,3):
        for x in range(0,16,4):
            d.rectangle((x,y,x+3,y+2),fill=(*p[1+(x//4+y//3)%2],255))
    d.line((0,0,15,0),fill=(*p[3],255));d.line((0,1,0,15),fill=(*p[2],255))
    d.line((15,1,15,15),fill=(*p[0],255));d.line((1,15,14,15),fill=(*p[0],255))
    if name=='plate':
        d.rectangle((2,2,13,13),outline=(*p[0],255))
        for x,y in ((1,1),(14,1),(1,14),(14,14)):d.point((x,y),fill=(*p[3],255))
    if name=='grate':
        for y in range(2,15,3):d.line((1,y,14,y),fill=(*p[0],255))
    if name=='warning':
        for y in range(16):
            for x in range(16):tile.putpixel((x,y),(*p[0 if (x+y)//4%2 else 2],255))
    if name=='screen':
        d.rectangle((0,0,15,15),fill=(*p[1],255))
        for y in range(1,16,2):d.line((1,y,14,y),fill=(*p[2],255))
    if name in ('green','amber','red'):
        d.rectangle((0,0,15,15),fill=(*p[0],255));d.rectangle((2,2,13,13),fill=(*p[2],255))
        d.line((3,2,9,2),fill=(*p[3],255));d.line((2,3,2,7),fill=(*p[3],255))
    atlas.paste(tile,(tx,ty))
atlas.save(OUT/'industrial_mine.png')
atlas.save(ASSETS/'textures/block/industrial_mine.png')

parts={}; all_cubes=[]; groups={}; hierarchy={}; placements=[]
def ident(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'ccec/industrial_mine/v2/'+s))
def save(path,obj):path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def group(name,parent=None,pivot=(0,0,0)):
    groups[name]={'name':name,'uuid':ident(name),'origin':[v*16 for v in pivot],
                  'rotation':[0,0,0],'export':True,'visibility':True,'isOpen':True}
    hierarchy[name]={'uuid':ident(name),'isOpen':True,'children':[]}
    if parent:hierarchy[parent]['children'].append(hierarchy[name])
group('industrial_mine')
group('base_structure','industrial_mine')
for g in ('machine_base','service_deck','control_cabinet'):group(g,'base_structure')
for g in ('gantry_frame','main_drive_housing','transmission'):group(g,'industrial_mine')
for g in ('nw','ne','sw','se'):
    x=0 if g.endswith('w') else 2; z=4 if g.startswith('n') else 6
    group('gearbox_'+g,'industrial_mine',(x,1.8,z))
    group('gearbox_rotor_'+g,'gearbox_'+g,(x,1.72,z))
    group('drill_group_'+g,'industrial_mine',(x,0,z))
group('main_drive','industrial_mine',(1,2.88,5))
group('indicators','industrial_mine')

def cube(part,name,a,b,mat,g=None):
    c={'name':name,'from':list(a),'to':list(b),'material':mat}
    parts.setdefault(part,[]).append(c)
    if g:place(c,g)
    return c

def place(c,g,offset=(0,0,0),prefix=''):
    c=dict(c,name=prefix+c['name'],**{'from':[a+b for a,b in zip(c['from'],offset)],'to':[a+b for a,b in zip(c['to'],offset)]})
    c['group']=g;all_cubes.append(c)
    return c

def fixed(g,name,a,b,mat):return cube('fixed_core',name,a,b,mat,g)

# Deep cast base: 84 modular plates surround the actual 4x4 drill positions.
for x in range(-4,6):
    for z in range(10):
        if -1<=x<=2 and 3<=z<=6:continue
        fixed('machine_base',f'cast_bed_{x}_{z}',(x,-1,z),(x+1,-.25,z+1),'steel')
        fixed('service_deck',f'deck_plate_{x}_{z}',(x+.025,-.25,z+.025),(x+.975,-.12,z+.975),
              'grate' if x in (-4,5) or z in (0,9) else 'plate')
# The machine occupies an 8x8 envelope. Side cheek castings ground the force path.
for x in (-3.3,3.8):
    for z in (1,3,5,7):
        fixed('machine_base',f'longitudinal_sill_{x}_{z}',(x,-.12,z),(x+1.5,.65,z+1.9),'steel')
        fixed('machine_base',f'sill_access_{x}_{z}',(x+.04,.16,z+.15),(x+1.46,.56,z+1.75),'plate')
for z in (2.45,7.15):
    fixed('machine_base',f'drill_bed_crossmember_{z}',(-1.5,.1,z),(3.5,.55,z+.4),'edge')
for x in (-1.5,3.15):
    fixed('machine_base',f'drill_bed_runner_{x}',(x,.1,2.5),(x+.35,.55,7.5),'edge')
# Functional mass around the drill bed: front lubrication pumps, rear reduction
# sump and side bearing beds. These occupy the former empty apron, not the perimeter.
for x in (-2,0,2):
    fixed('machine_base',f'lubrication_pump_housing_{x}',(x,.02,1),(x+1.95,.72,2.85),'steel')
    fixed('machine_base',f'pump_inspection_plate_{x}',(x+.12,.22,.97),(x+1.82,.62,1.02),'plate')
    fixed('machine_base',f'pump_cooling_grille_{x}',(x+.22,.72,1.2),(x+1.72,.77,2.2),'grate')
    fixed('machine_base',f'reduction_sump_{x}',(x,.02,7.15),(x+1.95,1.15,9),'steel')
    fixed('machine_base',f'rear_cooling_plate_{x}',(x+.15,.3,8.97),(x+1.8,1.02,9.04),'grate')
for x in (-1.8,3):
    for z in (2.85,4.25,5.65):
        fixed('machine_base',f'bearing_bed_{x}_{z}',(x,.02,z),(x+.8,.48,z+1.4),'steel')
        fixed('machine_base',f'bearing_bed_rib_{x}_{z}',(x+.08,.48,z+.2),(x+.7,.66,z+.43),'edge')
# Four broad internal pillars, never perimeter corner towers.
for x in (-2.75,4):
    for z in (3.05,6.3):
        fixed('gantry_frame',f'portal_foot_{x}_{z}',(x-.15,.5,z-.15),(x+.9,.85,z+1),'edge')
        fixed('gantry_frame',f'portal_column_{x}_{z}',(x,.7,z),(x+.7,2.6,z+.7),'steel')
        fixed('gantry_frame',f'portal_web_{x}_{z}',(x+.2,.9,z-.05),(x+.5,2.55,z+.75),'edge')
for z in (3.1,6.2):
    for x in (-2.75,-.3,2.15):
        fixed('gantry_frame',f'portal_lintel_{x}_{z}',(x,2.3,z),(x+2.5,2.78,z+.65),'steel')
        fixed('gantry_frame',f'portal_flange_{x}_{z}',(x,2.72,z-.06),(x+2.5,2.85,z+.71),'edge')
# A visible longitudinal transmission spine and four lateral gearbox branches.
for z in (2,3.5,5,6.5):
    fixed('transmission',f'transmission_spine_{z}',(.77,1.97,z),(1.23,2.35,z+1.5),'shaft')
for z in (4,6):
    fixed('transmission',f'transmission_branch_{z}',(-.3,1.94,z-.19),(2.3,2.3,z+.19),'shaft')
    for x in (0,1,2):
        fixed('transmission',f'coupling_{x}_{z}',(x-.25,1.88,z-.28),(x+.25,2.38,z+.28),'brass')

for gi,g in enumerate(('nw','ne','sw','se')):
    gx=0 if gi%2==0 else 2;gz=4 if gi<2 else 6
    fixed('gearbox_'+g,'gearbox_cradle_'+g,(gx-.98,1.12,gz-.98),(gx+.98,1.5,gz+.98),'steel')
    for x in (gx-.93,gx+.7):
        fixed('gearbox_'+g,f'gearbox_support_{g}_{x}',(x,.4,gz-.85),(x+.23,1.15,gz+.85),'edge')
    for x in (gx-.8,gx+.8):
        for z in (gz-.8,gz+.8):
            fixed('gearbox_'+g,f'pod_fastener_{g}_{x}_{z}',(x-.09,1.5,z-.09),(x+.09,1.59,z+.09),'brass')
    # Keep the gearbox top open so its large rotating wheel reads immediately.
    for x in (gx-.95,gx+.8):
        fixed('gearbox_'+g,f'gearbox_guard_{g}_{x}',(x,1.5,gz-.95),(x+.15,1.78,gz+.95),'steel')

# Fixed upper main-drive housing: stepped, massive, with exposed central rotor.
for x in (-.75,2.35):
    fixed('main_drive_housing',f'drive_bearing_pedestal_{x}',(x,2.25,4.25),(x+.4,3.18,5.75),'steel')
    fixed('main_drive_housing',f'drive_pedestal_cap_{x}',(x-.06,3.1,4.19),(x+.46,3.28,5.81),'edge')
fixed('main_drive_housing','upper_motor_body',(-.3,2.4,7.2),(2.3,3.25,8.7),'steel')
fixed('main_drive_housing','upper_motor_cap',(-.18,3.25,7.3),(2.18,3.45,8.6),'edge')
fixed('main_drive_housing','motor_reduction',(.65,2.04,6.7),(1.35,2.7,7.5),'brass')
fixed('transmission','main_drive_vertical_shaft',(.82,2.23,4.82),(1.18,2.9,5.18),'shaft')
for x in (-.2,1.8):
    fixed('main_drive_housing',f'motor_saddle_{x}',(x,.6,7.4),(x+.4,2.4,8.4),'steel')
    fixed('main_drive_housing',f'motor_saddle_foot_{x}',(x-.1,.1,7.3),(x+.5,.7,8.5),'edge')
for x in (-.1,.3,.7,1.1,1.5,1.9):
    fixed('main_drive_housing',f'motor_cooling_fin_{x}',(x,2.6,8.68),(x+.14,3.18,8.8),'edge')
# Maintenance access is local and secondary, not a high outer fence.
for x in (-3.5,4.85):
    for z in (3,4,5,6):
        fixed('service_deck',f'maintenance_step_{x}_{z}',(x,1.95,z),(x+.65,2.02,z+.94),'grate')
    fixed('service_deck',f'local_grab_rail_{x}',(x+.25,2.02,4.7),(x+.36,2.45,5.4),'edge')
for z in (2.5,7.4):
    fixed('service_deck',f'caution_strip_{z}',(-1.45,.56,z),(3.45,.62,z+.12),'warning')
# Fixed controller skin follows its real one-block hitbox at local (0,0,0).
fixed('control_cabinet','control_cabinet',(0,.02,.02),(1,.94,1),'steel')
fixed('control_cabinet','console_brow',(-.03,.9,-.05),(1.03,1.02,.42),'edge')
fixed('control_cabinet','control_screen_bezel',(.08,.43,-.025),(.67,.84,.04),'brass')
fixed('control_cabinet','control_screen',(.12,.47,-.035),(.63,.80,-.026),'screen')
fixed('control_cabinet','service_door',(.12,.09,-.025),(.9,.38,.02),'plate')
fixed('control_cabinet','selector',(.73,.46,-.09),(.87,.58,-.03),'brass')

def rotor(part,radius,height):
    cube(part,'rotor_hub',(-.22,-height/2,-.22),(.22,height/2,.22),'shaft')
    cube(part,'wheel_web',(-radius*.72,-height*.3,-radius*.72),(radius*.72,height*.3,radius*.72),'gear')
    for i in range(8):
        a=i*math.tau/8;x=math.cos(a)*radius*.83;z=math.sin(a)*radius*.83
        cube(part,f'tooth_{i}',(x-radius*.18,-height/2,z-radius*.18),(x+radius*.18,height/2,z+radius*.18),'gear')
    cube(part,'shaft_collar',(-.28,height*.3,-.28),(.28,height*.8,.28),'brass')
rotor('main_drive',1.24,.36)
rotor('gearbox',.73,.25)
for c in parts['main_drive']:place(c,'main_drive',(1,2.88,5),'drive_')
for gi,g in enumerate(('nw','ne','sw','se')):
    gx=0 if gi%2==0 else 2;gz=4 if gi<2 else 6
    for c in parts['gearbox']:place(c,'gearbox_rotor_'+g,(gx,1.72,gz),g+'_')
    for dx in (-.5,.5):
        for dz in (-.5,.5):
            group(f'drill_{g}_{dx}_{dz}','drill_group_'+g,(gx+dx,0,gz+dz))
# Dense thick shafts and stepped cutting crowns, not a few thin decorative sticks.
cube('drill_shaft','shaft',(-.15,-.67,-.15),(.15,1.18,.15),'shaft')
for y in (-.48,.12,.72):
    cube('drill_shaft',f'shaft_collared_{y}',(-.24,y,-.24),(.24,y+.16,.24),'edge')
cube('drill_shaft','drill_crown',(-.41,-.55,-.41),(.41,-.30,.41),'steel')
cube('drill_shaft','drill_tip',(-.23,-.83,-.23),(.23,-.55,.23),'shaft')
for x,z in ((-.43,-.24),(.19,-.24),(-.12,-.43),(-.12,.19)):
    cube('drill_shaft',f'cutter_{x}_{z}',(x,-.68,z),(x+.24,-.42,z+.24),'edge')
for gi,g in enumerate(('nw','ne','sw','se')):
    gx=0 if gi%2==0 else 2;gz=4 if gi<2 else 6
    for dx in (-.5,.5):
        for dz in (-.5,.5):
            for c in parts['drill_shaft']:place(c,f'drill_{g}_{dx}_{dz}',(gx+dx,0,gz+dz),f'{g}_{dx}_{dz}_')
# Single interchangeable lens asset at the actual controller front.
for color in ('green','amber','red'):
    cube('indicator_'+color,'status_lens',(.75,.7,-.055),(.91,.86,-.025),color)
place(parts['indicator_amber'][0],'indicators')
# Dynamic north-facing modules fit real interface hitboxes and leave wire cells clear.
cube('power_module','intake_casting',(.02,.02,.02),(.98,.98,.98),'steel')
cube('power_module','intake_flange',(-.02,.12,-.05),(1.02,.9,.05),'edge')
cube('power_module','intake_recess',(.1,.22,-.08),(.9,.8,-.05),'dark')
for x in (.25,.75):
    cube('power_contacts',f'ceramic_terminal_{x}',(x-.08,.32,-.12),(x+.08,.72,.02),'ceramic')
    cube('power_contacts',f'copper_lug_{x}',(x-.05,.43,-.03),(x+.05,.6,.02),'copper')
cube('power_module','heavy_busbar',(.25,.08,.16),(.75,.19,.86),'copper')
cube('power_module','power_label',(.37,.78,-.09),(.62,.9,-.05),'warning')
cube('output_module','discharge_body',(.02,.02,.02),(.98,.98,.98),'steel')
cube('output_module','discharge_throat',(.1,.18,-.04),(.9,.8,.04),'dark')
cube('output_module','chute_floor',(.08,.14,-.25),(.92,.23,.18),'edge')
for x in (.04,.85):cube('output_module',f'chute_cheek_{x}',(x,.16,-.23),(x+.11,.58,.18),'steel')
cube('output_module','chute_upper_lip',(.04,.78,-.08),(.96,.92,.06),'warning')

FACES=('north','east','south','west','up','down')
def faces(c,java=False):
    tx,ty=tiles[c['material']];dx,dy,dz=[b-a for a,b in zip(c['from'],c['to'])];result={}
    for face in FACES:
        w,h=(dx,dy) if face in ('north','south') else (dz,dy) if face in ('east','west') else (dx,dz)
        uv=[tx,ty,tx+min(16,max(2,w*16)),ty+min(16,max(2,h*16))]
        if c['material'] in ('green','amber','red','plate'):uv=[tx,ty,tx+16,ty+16]
        result[face]={'uv':[v/4 for v in uv] if java else uv,'texture':'#atlas' if java else 0}
    return result
for name,cubes in parts.items():
    result={'ambientocclusion':False,'textures':{'atlas':'createcityexpansioncore:block/industrial_mine',
             'particle':'createcityexpansioncore:block/industrial_mine'},'elements':[]}
    for c in cubes:
        el={'from':[v*16/SCALE for v in c['from']],'to':[v*16/SCALE for v in c['to']],'faces':faces(c,True)}
        assert all(-16<=v<=32 for v in el['from']+el['to'])
        result['elements'].append(el)
    save(PARTS/(name+'.json'),result)

texture={'name':'industrial_mine.png','uuid':ident('atlas'),'id':'0','mode':'bitmap','saved':True,
         'uv_width':64,'uv_height':64,'width':64,'height':64,
         'source':'data:image/png;base64,'+base64.b64encode((OUT/'industrial_mine.png').read_bytes()).decode()}
def bb_element(c):
    return {'name':c['name'],'type':'cube','uuid':ident(c['name']),'from':[v*16 for v in c['from']],
            'to':[v*16 for v in c['to']],'origin':[v*16 for v in (0,0,0)],'rotation':[0,0,0],
            'autouv':0,'box_uv':False,'faces':faces(c),'visibility':True,'export':True}
for c in all_cubes:hierarchy[c['group']]['children'].append(ident(c['name']))
model={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':'industrial_mine',
       'resolution':{'width':64,'height':64},'textures':[texture],'elements':[bb_element(c) for c in all_cubes],
       'groups':list(groups.values()),'outliner':[hierarchy['industrial_mine']]}
save(OUT/'industrial_mine.bbmodel',model)
for part in ('power_module','output_module'):
    component_cubes=parts[part]+(parts['power_contacts'] if part=='power_module' else [])
    asset={'meta':model['meta'],'name':part,'resolution':model['resolution'],'textures':[texture],
           'elements':[bb_element(c) for c in component_cubes],
           'groups':[{'name':part,'uuid':ident(part),'origin':[8,0,8]}],
           'outliner':[{'uuid':ident(part),'children':[ident(c['name']) for c in component_cubes]}]}
    if part=='power_module':
        asset['groups'].append({'name':'power_contacts','uuid':ident('power_contacts'),'origin':[8,0,8]})
        asset['outliner'][0]['children']=[ident(c['name']) for c in parts[part]]+[
            {'uuid':ident('power_contacts'),'children':[ident(c['name']) for c in parts['power_contacts']]}]
    save(OUT/(part+'.bbmodel'),asset)

save(OUT/'assembly_manifest.json',{'runtime_geometry_scale':SCALE,'controller':[0,0,0],
    'footprint':[[-4,-1,0],[6,2,10]],'visual_bounds':[[-4.25,-1, -.25],[6.25,3.5,10.25]],
    'main_drive':[1,2.88,5],'gearboxes':[[0,1.72,4],[2,1.72,4],[0,1.72,6],[2,1.72,6]],
    'drills':[[x+.5,0,z+.5] for z in range(3,7) for x in range(-1,3)],
    'dynamic_attachments':['power_module','power_contacts','output_module'],'attachment_rotation_pivot':[.5,0,.5],
    'parts':{name:len(cubes) for name,cubes in parts.items()},'core_cube_count':len(all_cubes)})

# Preview uses the same geometry/UVs, with example attachments only for presentation.
render_cubes=list(all_cubes)
for name,offset in (('power_module',(-2,0,0)),('power_contacts',(-2,0,0)),('output_module',(3,0,0))):
    for c in parts[name]:
        render_cubes.append(dict(c,**{'from':[a+b for a,b in zip(c['from'],offset)],'to':[a+b for a,b in zip(c['to'],offset)]}))
corners={'north':[0,1,2,3],'south':[5,4,7,6],'west':[4,0,3,7],'east':[1,5,6,2],'up':[3,2,6,7],'down':[4,5,1,0]}
def render(filename,azimuth,elevation):
    W,H=1200,880;scale=79
    a,e=math.radians(azimuth),math.radians(elevation)
    right=np.array([math.cos(a),0,math.sin(a)])
    toward=np.array([math.sin(a)*math.cos(e),math.sin(e),-math.cos(a)*math.cos(e)])
    up=np.cross(right,toward);center=np.array([1,.5,5])
    color=np.empty((H,W,3),np.uint8);color[:]=(29,35,39);depth=np.full((H,W),-np.inf)
    tex=np.array(atlas)[:,:,:3];light=np.array([-.35,.85,-.5]);light/=np.linalg.norm(light)
    for c in render_cubes:
        x,y,z=c['from'];X,Y,Z=c['to'];verts=np.array([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]])
        for face,ids in corners.items():
            vs=verts[ids];normal=-np.cross(vs[1]-vs[0],vs[2]-vs[0]);normal/=np.linalg.norm(normal)
            if normal@toward<=0:continue
            shade=.55+.45*max(0,normal@light)
            p=vs-center;proj=np.stack([W/2+p@right*scale,H/2-p@up*scale,p@toward],axis=1)
            u,v,U,V=faces(c)[face]['uv'];uv=np.array([[u,V],[U,V],[U,v],[u,v]])
            for inds in ([0,1,2],[0,2,3]):
                tri=proj[inds];tuv=uv[inds]
                xmin=max(0,int(np.floor(min(tri[:,0]))));xmax=min(W-1,int(np.ceil(max(tri[:,0]))))
                ymin=max(0,int(np.floor(min(tri[:,1]))));ymax=min(H-1,int(np.ceil(max(tri[:,1]))))
                if xmin>xmax or ymin>ymax:continue
                xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
                x0,y0=tri[0,:2];x1,y1=tri[1,:2];x2,y2=tri[2,:2];den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
                if abs(den)<1e-8:continue
                w0=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den
                w1=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den;w2=1-w0-w1
                zz=w0*tri[0,2]+w1*tri[1,2]+w2*tri[2,2];section=depth[ymin:ymax+1,xmin:xmax+1]
                mask=(w0>=0)&(w1>=0)&(w2>=0)&(zz>section)
                uu=np.clip((w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0]).astype(int),0,63)
                vv=np.clip((w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]).astype(int),0,63)
                section[mask]=zz[mask];color[ymin:ymax+1,xmin:xmax+1][mask]=(tex[vv,uu]*shade).astype(np.uint8)[mask]
    im=Image.fromarray(color);d=ImageDraw.Draw(im)
    try:font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',20)
    except OSError:font=ImageFont.load_default()
    d.text((30,25),'INDUSTRIAL MINE / HEAVY DRILLING PLATFORM',font=font,fill=(201,184,144))
    d.text((30,H-44),'Actual asset geometry | 10 x 10 base | example interface positions',font=font,fill=(137,155,158))
    im.save(OUT/filename)
render('preview_isometric.png',35,30)
render('preview_front.png',0,13)
render('preview_top.png',0,89.9)
assert len({c['name'] for c in all_cubes})==len(all_cubes)
assert len([g for g in groups if g.startswith('drill_') and not g.startswith('drill_group_')])==16
for part,clearance in (('main_drive',1.35),('gearbox',.8)):
    radius=max(math.hypot(x,z) for c in parts[part]
               for x in (c['from'][0],c['to'][0]) for z in (c['from'][2],c['to'][2]))
    assert radius<clearance,(part,radius,clearance)
mask=np.zeros((200,200),bool)
for c in all_cubes:
    if c['to'][1]<=0 or c['group']=='service_deck':continue
    x,y,z=c['from'];X,Y,Z=c['to']
    mask[max(0,int((x+4)*20)):min(200,int((X+4)*20)),max(0,int(z*20)):min(200,int(Z*20))]=True
coverage=round(float(mask.mean()*100),2)
assert 65<=coverage<=75,coverage
save(OUT/'asset_validation.json',{'cube_count':len(all_cubes),'runtime_part_count':len(parts),
    'machine_projected_footprint_percent':coverage,'coverage_method':'5cm grid union, excludes base at/below zero and service deck',
    'checks':['unique cube identifiers','16 separate drill shaft groups','all Java geometry inside -16..32',
              'rotor swept envelopes clear stationary guards','machine footprint 65..75 percent'],
    'preview_is_game_screenshot':False})
print('Generated',len(all_cubes),'core cubes;',len(parts),'runtime parts; scale',SCALE)
