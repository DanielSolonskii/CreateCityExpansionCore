"""Check progression, translations, task safety and visual coordinates."""
from pathlib import Path
import json
from build_campaign import ROOT, OUT, uid

catalog=json.loads((OUT.parent/'quest-catalog.json').read_text(encoding='utf-8'))
chapters=[json.loads(p.read_text(encoding='utf-8')) for p in sorted((OUT/'chapters').glob('*.snbt'))]
nodes={q['id']:q for c in chapters for q in c['quests']}
bykey={q['key']:q for q in catalog}
assert len(nodes)==len(catalog)
ids=[]
for chapter in chapters:
    coords=set();ids.append(chapter['id'])
    for q in chapter['quests']:
        assert (q['x'],q['y']) not in coords;coords.add((q['x'],q['y']))
        ids.append(q['id']);assert not q['rewards']
        assert all(d in nodes for d in q['dependencies'])
        for t in q['tasks']:
            ids.append(t['id']);assert t['type'] in ('item','checkmark')
            if t['type']=='item':
                assert t['consume_items'] is False and t['only_from_crafting'] is False
assert len(ids)==len(set(ids))
ru=json.loads((OUT/'lang/ru_ru.snbt').read_text(encoding='utf-8'))
en=json.loads((OUT/'lang/en_us.snbt').read_text(encoding='utf-8'))
assert ru.keys()==en.keys()
for lang in (ru,en):
    for q in nodes.values():
        assert lang[f"quest.{q['id']}.title"].strip()
        assert len(lang[f"quest.{q['id']}.quest_desc"][0])>=80

# Complete only the main spine: optional content must not be required.
done=set()
while True:
    ready={q['key'] for q in catalog if not q['side'] and set(q['deps'])<=done}
    if ready<=done:break
    done |= ready
assert 'diagnose' in done
assert len(done)==sum(not q['side'] for q in catalog)
assert bykey['ponder']['deps']==['iron']
assert bykey['analyzer']['deps']==['tube']
assert bykey['cityintro']['deps']==['site']
assert bykey['mineplan']['deps']==['project']
assert all(q['side'] for q in catalog if q['ch']=='chunks')
assert not bykey['found']['items'] and not bykey['roles']['items']
assert not any('chunks'==bykey[d]['ch'] for q in catalog if q['ch']!='chunks' for d in q['deps'])
assert not any(c['filename'] in ('trade','oil','aviation','railways') for c in chapters)
result=dict(quests=len(nodes),main=len(done),optional=len(nodes)-len(done),
    translation_keys=len(ru),checks=['unique IDs','acyclic main progression','optional branches do not gate main path','joining city does not require pylon','no consumption','no rewards','RU/EN parity','nonoverlapping coordinates'])
(OUT.parent/'structural-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
lines=['# Каталог кампании / Campaign catalog','']
for chapter in chapters:
    lines += ['## '+ru[f"chapter.{chapter['id']}.title"]+' / '+en[f"chapter.{chapter['id']}.title"],'']
    for q in chapter['quests']:
        lines += ['### '+ru[f"quest.{q['id']}.title"]+' / '+en[f"quest.{q['id']}.title"],'',
            '**'+ru[f"quest.{q['id']}.quest_subtitle"]+'** · ID `'+q['id']+'`','',
            ru[f"quest.{q['id']}.quest_desc"][0],'',en[f"quest.{q['id']}.quest_desc"][0],'',
            'Задачи / Tasks: '+', '.join(t.get('item',{}).get('id','checkmark') for t in q['tasks']),'']
(OUT.parent/'CATALOG_RU_EN.md').write_text('\n'.join(lines),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))

old=json.loads((OUT.parent/'manifest-before-redesign.json').read_text(encoding='utf-8'))
new=json.loads((OUT.parent/'manifest.json').read_text(encoding='utf-8'))
assert set(old['ids'])<=set(new['ids']), 'Existing progress IDs changed'
assert bykey['tube']['deps']==['sheets','quartz']
assert bykey['brass']['deps']==['mixer','burner']
assert all(q.get('hide_dependency_lines') is False for q in nodes.values())
from presentation import ICONS
models=set(json.loads((ROOT/'tools/ftb-campaign/server-evidence.json').read_text(encoding='utf-8'))['item_models'])
for q in nodes.values():
 i=q['icon']['id'];assert i.startswith('minecraft:') or i in models,i
assert not any('CCEC' in str(v) for v in ru.values())
print('REDESIGN_OK stable IDs, actual icons, visible edges, independent tube branch')
