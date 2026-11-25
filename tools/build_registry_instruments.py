"""Generate the access remote, its assembly stage and the vehicle registry cabinet."""
from pathlib import Path
import base64, copy, io, json, math, random, uuid
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/createcityexpansioncore'
ART = ROOT / 'art/registry_instruments'
NS = 'createcityexpansioncore:'
def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf8')

def material(color, seed):
    rng = random.Random(seed)
    tile = Image.new('RGB',(64,64))
    d = ImageDraw.Draw(tile)
    for y in range(64):
        tone = rng.choice([-2,-1,0,0,1,2])
        d.line((0,y,63,y),fill=tuple(v+tone for v in color))
    return tile

colors = {'iron':(58,61,62),'edge':(87,90,84),'black':(31,33,32),
          'brass':(179,133,63),'bright':(210,172,97),'copper':(158,92,56),
          'teal':(40,93,90),'cream':(202,191,157),'leather':(92,57,42)}
tiles = {k:material(v,i) for i,(k,v) in enumerate(colors.items())}
# Handled materials pick up wear differently: rubbed brass, hammered enamel
# and short creases in hide. Keep the fine marks below the contrast of controls.
for index, (key, color) in enumerate(colors.items()):
    rng = random.Random(812 + index)
    draw = ImageDraw.Draw(tiles[key])
    for mark in range(160 if key == 'leather' else 75):
        x, y = rng.randrange(64), rng.randrange(64)
        delta = rng.choice((-9, -6, 4, 8))
        shade = tuple(max(0, min(255, c + delta)) for c in color)
        length = rng.randrange(1, 6) if key in ('iron','brass','bright','copper') else 2
        draw.line((x, y, x+length, y+(mark%2 if key=='leather' else 0)), fill=shade)
for key, base in [('hatch','teal'), ('cover','iron'), ('cap','brass')]:
    tile=tiles[base].copy()
    d=ImageDraw.Draw(tile)
    rgb=colors[base]
    dark=tuple(c-19 for c in rgb)
    light=tuple(c+17 for c in rgb)
    d.line([(3,54),(3,8),(8,3),(54,3)],fill=light)
    d.line([(60,9),(60,55),(55,60),(9,60)],fill=dark,width=2)
    for x,y in ((8,8),(56,55)):
        d.rectangle((x-1,y-1,x+1,y+1),fill=dark)
        d.point((x-1,y-1),fill=light)
    tiles[key]=tile
screw=tiles['brass'].copy()
d=ImageDraw.Draw(screw)
d.polygon([(8,20),(20,8),(44,8),(56,20),(56,44),(44,56),(20,56),(8,44)],fill='#c59b58',outline='#795d38',width=3)
d.line((20,44,44,20),fill='#57442f',width=6)
tiles['screw']=screw
d=ImageDraw.Draw(tiles['leather'])
for y in [5,58]:
    for x in range(2,64,8): d.line((x,y,x+4,y),fill='#ae8160',width=2)
for opened in (False,True):
    t=tiles['iron'].copy(); d=ImageDraw.Draw(t)
    d.rectangle((17,29,46,50),fill='#dbcda5')
    d.line([(22,30),(22,19),(26,14),(36,14),(40,19),(40,20 if opened else 30)],fill='#dbcda5',width=4)
    d.rectangle((30,34,33,42),fill='#4c4c44')
    tiles['open' if opened else 'closed']=t
screen=Image.new('RGB',(96,72),'#153735'); d=ImageDraw.Draw(screen)
for x in range(4,94,8): d.line((x,3,x,68),fill='#1b4543')
for y in range(4,69,8): d.line((2,y,93,y),fill='#1c4745')
ink='#81d5c4'
d.line([(10,36),(22,24),(37,19),(60,18),(76,23),(86,32),(79,40),(64,46),(34,47),(19,43),(10,36)],fill=ink,width=2)
d.line((10,36,85,32),fill=ink)
for x in (28,42,57,69):
    d.line([(x,21),(x-3,34),(x,44)], fill='#60b3aa')
d.line([(34,47),(39,52),(59,52),(64,46)],fill=ink,width=2)
d.text((4,1),'REG / 03',fill=ink);d.line((5,61,52,61),fill=ink)
for x in range(65,91,4):d.rectangle((x,59,x+1,64),fill=ink)
tiles['glass']=screen.resize((64,64),Image.Resampling.NEAREST)
for n in '047319':
    t=material((196,183,149),1);d=ImageDraw.Draw(t)
    font=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',53)
    d.text((32,31),n,font=font,anchor='mm',fill='#332f27')
    d.line((0,34,63,34),fill='#938674')
    tiles['digit'+n]=t
atlas=Image.new('RGB',(320,320))
uv={}
for i,(k,t) in enumerate(tiles.items()):
    x=i%5*64;y=i//5*64;atlas.paste(t,(x,y));uv[k]=[v/20 for v in (x+1,y+1,x+63,y+63)]
# Bake turn, detent pause, and return of the selector handle into the round dial face.
frames=[]
for angle in [45,40,32,22,12,4,-5,-15,-25,-35,-45,-40,-32,-22,-12,-4,5,15,25,35]:
    im=Image.new('RGB',(64,64),'#353b38');d=ImageDraw.Draw(im)
    for r,col in [(30,'#4a4232'),(28,'#d0a364'),(25,'#997337'),(22,'#bd934e')]:
        d.ellipse((32-r,32-r,32+r,32+r),fill=col)
    a=math.radians(angle);u=(math.sin(a),-math.cos(a));v=(-u[1],u[0])
    def point(x,y):return (32+u[0]*x+v[0]*y,32+u[1]*x+v[1]*y)
    d.polygon([point(x,y) for x,y in [(-17,-5),(19,-5),(19,5),(-17,5)]],fill='#433d30')
    d.line([point(15,-2),point(20,-2)],fill='#efe0ad',width=3);frames.append(im)
dirs=('up','down','north','south','west','east')
parts=[]
def box(name,a,b,mat,**faces):
    fs={}
    for side in dirs:
        k=faces.get(side,mat)
        fs[side]={'texture':'#dial' if k=='dial' else '#case','uv':[0,0,16,16] if k=='dial' else uv[k]}
        if k=='glass':fs[side]['neoforge_data']={'block_light':12}
    parts.append(dict(name=name,**{'from':a,'to':b},faces=fs))

arttex='item/registry_instruments/'
td=ASSETS/'textures'/arttex;td.mkdir(parents=True,exist_ok=True)
atlas.save(td/'enamel.png')
strip=Image.new('RGB',(64,64*len(frames)))
for i,t in enumerate(frames):strip.paste(t,(0,64*i))
strip.save(td/'selector.png')
save(td/'selector.png.mcmeta',{'animation':{'frames':[{'index':i,'time':26 if i in (0,10) else 2} for i in range(len(frames))]}})
def export(key,model,imgs):
    save(ASSETS/'models'/(key+'.json'),model)
    name=key.split('/')[-1];els=copy.deepcopy(model['elements']); textures=[]
    def uid(k):return str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/'+str(k)))
    for i,(k,img) in enumerate(imgs.items()):
        buf=io.BytesIO();img.save(buf,format='PNG')
        textures.append(dict(id=str(i),uuid=uid(k),name=k+'.png',width=img.width,height=img.height,uv_width=320,uv_height=320,source='data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode()))
    for i,e in enumerate(els):
        e.update(type='cube',uuid=uid(i),origin=[8,8,8],box_uv=False,autouv=0)
        for f in e['faces'].values():
            f['texture']=list(imgs).index(f['texture'][1:]);f['uv']=[v*20 for v in f['uv']];f.pop('neoforge_data',None)
    save(ART/(name+'.bbmodel'),dict(meta=dict(format_version='4.10',model_format='java_block',box_uv=False),name=name,resolution=dict(width=320,height=320),elements=els,outliner=[e['uuid'] for e in els],textures=textures,display=model.get('display',{})))
tex={'case':NS+arttex+'enamel','particle':'#case'}
display={'gui':{'rotation':[32,220,0],'translation':[0,0,0],'scale':[.94]*3},
         'firstperson_righthand':{'rotation':[90,0,0],'translation':[1,2.5,1],'right_rotation':[0,0,90]},
         'thirdperson_righthand':{'rotation':[90,0,0],'translation':[0,2,.5],'right_rotation':[0,0,90]},
         'ground':{'translation':[0,3,0]},'fixed':{'rotation':[-90,0,0]}}
box('Forged lower tray',[4,5.8,1.12],[12,7.1,13.92],'iron')
for x in [3.8,11.6]:
    box('Stitched grip',[x,6.15,2],[x+.6,8.2,13],'leather')
for z in [1,13]:
    box('Forged collar crown',[4.12,5.94,z],[11.88,8.66,z+.83],'brass',up='cap')
    for x in (3.61,11.88):
        box('Stepped collar shoulder',[x,6.15,z+.10],[x+.51,8.40,z+.73],'brass')
    for x in (4.13,11.46):
        box('Protective corner horn',[x,8.66,z+.12],[x+.4,8.9,z+.72],'bright')
for x in [6,9.4]:
    box('Copper conductor',[x,7.1,2],[x+.55,7.65,13],'copper')
box('Slide guide',[7.7,7.1,2],[8.3,7.94,13],'edge')
for z in [4,4.5,5,5.5,6]:box('Return spring winding',[7.4,7.25,z],[8.6,8,z+.2],'bright')
box('Sliding contact bridge',[5.8,7.65,8],[10.2,8.12,9],'brass')
for x in [5.7,9.7]:box('Lanyard lug',[x,6.3,13.8],[x+.6,7,15.3],'brass')
box('Lanyard bridge',[5.7,6.3,15.3],[10.3,7,15.85],'bright')
for x in (3.54,12.19):
    box('Leather palm swell',[x,6.42,3],[x+.27,8.02,11.92],'leather')
    for z in (2.44,11.98):
        box('Grip ferrule',[x-.02,6.38,z],[x+.30,8.12,z+.52],'cap')
for x in (5.28,10.1):
    for z in (2.1,11.2):
        box('Insulating terminal boss',[x,7.12,z],[x+.6,7.8,z+.85],'black')
        box('Conductor clamp',[x-.06,7.8,z+.14],[x+.68,8.03,z+.68],'copper',up='screw')
for z in (7.7,9.05):
    box('Bridge saddle',[6.45,7.5,z],[7,7.99,z+.38],'edge')
for x in (6.18,9.52):
    box('Loop socket',[x,6.16,13.66],[x+.34,7.19,14.65],'cap')
def remote():return dict(textures=tex,elements=copy.deepcopy(parts),display=display)
export('item/incomplete_access_control_remote',remote(),{'case':atlas})
box('Face plate',[4.3,8.15,1.86],[11.7,8.55,12.96],'iron',up='cover')
for x in [5,10.65]:box('Raised side rail',[x,8.55,2.4],[x+.35,8.85,12.4],'brass')
for z,k in [(2.15,'open'),(10.9,'closed')]:
    box('Enamel lock symbol',[6.98,8.55,z],[9.02,8.59,z+1.8],'iron',up=k)
for x in [4.51,11.06]:
    for z in [2.2,12.2]:box('Brass screw',[x,8.55,z],[x+.37,8.68,z+.37],'bright',up='screw')
box('Travel slot',[6.9,8.55,4.2],[9.1,8.62,10.7],'black')
base=len(parts);variants=[]
for i in range(7):
    del parts[base:];z=8.6-i*.62
    box('Slider shoe',[6.25,8.62,z],[9.75,9.08,z+2.05],'brass')
    box('Chamfered thumb pad',[6.47,9.08,z+.08],[9.53,9.33,z+1.98],'cap')
    for j in range(4):box('Milled thumb rib',[6.63,9.33,z+.15+j*.45],[9.37,9.49,z+.38+j*.45],'bright')
    if i:
        key='item/registry_instruments/access_slider_'+str(i)
        save(ASSETS/'models'/(key+'.json'),remote())
        variants.append({'predicate':{NS+'access_slider':round(i/7,6)},'model':NS+key})
    else:idle=remote()
idle['overrides']=variants
export('item/access_control_remote',idle,{'case':atlas})
parts.clear()
box('Enamel cabinet',[.65,1,2.4],[15.35,14.85,14.7],'teal')
for x in [1.1,12]:
    box('Rubber foot',[x,0,3],[x+2.9,1,13.8],'black')
for y in [1.05,14.35]:box('Folded steel bumper',[.5,y,2.2],[15.5,y+.65,14.85],'iron')
for x in [.95,14.55]:
    box('Enamel pinstripe',[x,1.7,2.34],[x+.32,14.3,2.41],'cream')
for x in [1.2,13.9]:
    for z in [3,12.9]:box('Lid bolt',[x,15,z],[x+.65,15.24,z+.65],'brass')
box('CRT bezel',[1.35,6.2,1.5],[10.8,13.8,2.4],'brass')
box('Glass gasket',[1.67,6.52,1.37],[10.48,13.48,1.51],'black')
box('Airship trace',[1.9,6.75,1.30],[10.25,13.25,1.38],'black',north='glass')
box('Counter surround',[1.4,3.3,1.7],[10.83,5.78,2.4],'brass')
box('Counter aperture',[1.7,3.57,1.56],[10.53,5.52,1.71],'black')
for i,n in enumerate('047319'):
    x=2.02+i*1.4
    box('Serial wheel '+str(i),[x,3.72,1.48],[x+1.27,5.37,1.57],'cream',north='digit'+n)
box('Selector plate',[11.03,5,1.7],[14.72,11.7,2.41],'iron')
box('Recessed dial',[11.28,6.8,1.07],[14.48,10,1.71],'brass',north='dial')
for x,y in [(11.45,10.2),(12.65,10.65),(13.9,10.2)]:
    box('Detent marker',[x,y,1.59],[x+.34,y+.38,1.71],'cream')
for y in [2.05,2.5]:box('Lower grille',[3,y,2.22],[9,y+.19,2.4],'black')
for x in [.48,15.35]:
    for y in [5.2,10]:box('Hinge knuckle',[x,y,3.2],[x+.17,y+1.9,4],'brass')
box('Rear service hatch',[2.2,2.55,14.7],[13.8,13.4,14.9],'iron')
for y in [7,8,9,10,11]:box('Pressed ventilation louvre',[4,y,14.9],[12,y+.38,15.16],'edge')
# Cast lifting eyes: the opening runs through the part, visible above the lid.
for x in (1.0,13.55):
    box('Lifting eye foot',[x,15,3.95],[x+1.44,15.17,4.62],'cap')
    for offset in (0,1.10):
        box('Lifting eye cheek',[x+offset,15.17,4.04],[x+offset+.34,15.78,4.53],'brass')
    box('Lifting eye bow',[x+.09,15.78,4.08],[x+1.35,15.98,4.49],'bright')
box('Stamped top cover',[2.3,15,5.15],[13.7,15.14,13.72],'teal',up='hatch')
for x in (.87,14.36):
    for y in (2.12,12.82):
        box('Cast corner protector',[x,y,2.04],[x+.78,y+1.05,3.06],'iron',north='cover')
# A raised frame around the glass creates a shadow line around the recessed CRT.
for x in (1.38,10.35):
    box('Monitor side moulding',[x,6.46,1.04],[x+.42,13.54,1.51],'brass')
for y in (6.24,13.48):
    box('Monitor visor lip',[1.73,y,.98],[10.42,y+.30,1.51],'bright')
for x in (11.22,14.13):
    for y in (5.19,11.15):
        box('Selector housing captive screw',[x,y,1.55],[x+.34,y+.34,1.70],'edge',north='screw')
for x in (1.53,10.28):
    box('Drum axle bearer',[x,3.48,1.39],[x+.39,5.62,1.71],'cap')
for x in (.39,15.35):
    box('Enamel side access door',[x,2.55,4.4],[x+.26,13.82,13.45],'teal',west='hatch',east='hatch')
    for z in (4.7,12.82):
        for y in (3.08,12.85):
            box('Panel quarter-turn fastener',[x-.04,y,z],[x+.30,y+.4,z+.42],'edge',east='screw',west='screw')
    for y in (4.35,4.98,5.61,6.24):
        box('Side cooling louvre',[x-.10,y,6.1],[x+.36,y+.28,11.69],'iron')
for z in (4.65,11.4):
    for x in (1.40,12.25):
        box('Reinforced foot flange',[x,.40,z],[x+2.35,1.04,z+1.18],'edge')
# The cabinet front faces north, so west is on the viewer's right.
for element in parts:
    left, right = element['from'][0], element['to'][0]
    element['from'][0], element['to'][0] = round(16-right, 3), round(16-left, 3)
terminal=dict(parent='minecraft:block/block',textures={**tex,'dial':NS+arttex+'selector'},elements=parts)
export('block/transport_registration_terminal',terminal,{'case':atlas,'dial':frames[0]})
