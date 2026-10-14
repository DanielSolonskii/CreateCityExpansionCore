from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[2]/'output/ftb-campaign'
cat={q['key']:q for q in json.loads((root/'quest-catalog.json').read_text(encoding='utf-8'))}
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',12)
for ch in ('create','survival'):
 c=json.loads((root/'quests/chapters'/('ccec_'+ch+'.snbt')).read_text(encoding='utf-8'))
 nodes=c['quests'];by={q['id']:q for q in nodes};labels={__import__('build_campaign').uid('quest:'+k):q['ru'] for k,q in cat.items()}
 lo=min(q['x'] for q in nodes);top=min(q['y'] for q in nodes)
 def pos(q):return (int((q['x']-lo)*95+100),int((q['y']-top)*95+65))
 im=Image.new('RGB',(int((max(q['x'] for q in nodes)-lo)*95+240),int((max(q['y'] for q in nodes)-top)*95+140)), '#152029');d=ImageDraw.Draw(im)
 for q in nodes:
  for dep in q['dependencies']:
   if dep not in by:continue
   a=pos(by[dep]);b=pos(q);dx=b[0]-a[0];dy=b[1]-a[1];l=math.hypot(dx,dy);ux=dx/l;uy=dy/l
   end=(b[0]-ux*16,b[1]-uy*16);d.line((a,end),fill='#627b8c',width=2)
   d.polygon([end,(end[0]-ux*8+uy*4,end[1]-uy*8-ux*4),(end[0]-ux*8-uy*4,end[1]-uy*8+ux*4)],fill='#a8bdc9')
 for q in nodes:
  x,y=pos(q);color='#728d9b' if q['optional'] else '#dbad65';d.ellipse((x-14,y-14,x+14,y+14),fill='#253746',outline=color,width=3)
  label=labels[q['id']];words=label.split();rows=['']
  for word in words:
   if len(rows[-1])+len(word)>17:rows.append(word)
   else:rows[-1]+=(' ' if rows[-1] else '')+word
  for i,row in enumerate(rows):d.text((x,y+18+i*13),row,font=font,fill='#e2e9ed',anchor='mt')
 im.save(root/(ch+'-layout.png'))
