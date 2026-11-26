"""Native pixel menu geometry and numeric glyph atlas; no scaled concept art."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json
import re
from recycle_preview_models import export
from recycle_name_font import export as export_name_font
root=Path(__file__).resolve().parents[1]
out=root/'server-assets/CraftEngine/resources/starmarket-recycle';a=out/'resourcepack/assets/starmarket'
for folder in ['textures/font','textures/item','models/item','items','font']:(a/folder).mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype('C:/Users/32633/Desktop/leaf/plugins/Xerr/generated/resource_pack/assets/xerr_font/font/fusion_pixel_quest_12_sc.ttf',12)
small=ImageFont.truetype('C:/Users/32633/Desktop/leaf/plugins/Xerr/generated/resource_pack/assets/xerr_font/font/fusion_pixel.ttf',8)
im=Image.new('RGBA',(176,126),'#B4AEA1');d=ImageDraw.Draw(im)
def rect(box,color):d.rectangle(box,fill=color)
def text(x,y,value,color,large=False):
 mask=Image.new('L',im.size);ImageDraw.Draw(mask).text((x,y),value,font=font if large else small,fill=255,anchor='lt')
 im.paste(color,(0,0),mask.point(lambda v:255 if v>=128 else 0))
rect((0,0,175,125),'#493E2B');rect((2,2,173,123),'#C9B06D');rect((4,4,171,123),'#B4AEA1')
rect((5,5,170,5),'#ECE1BA');rect((5,6,5,122),'#D5C8A2');rect((170,6,170,122),'#817457')
# Unboxed item preview: no inset tile touching the section boundary.
rect((12,20,163,52),'#BDB8AC')
rect((53,24,53,49),'#A29A89')
text(64,6,'系统回收','#302E27',True)
text(64,54,'出售数量','#302E27',True);text(34,92,'预计获得','#34362E',True);text(145,92,'金币','#34362E',True)
# Click target row y72-89: narrow continuous visual track centered at y81.
rect((25,77,151,87),'#6F654A');rect((26,78,150,86),'#8F9183')
for x0,x1,label in [(7,79,'返回'),(97,169,'确认回收')]:
 rect((x0,106,x1,122),'#4C402D');rect((x0+1,107,x1-1,120),'#B49243');rect((x0+2,109,x1-2,119),'#827D6D');rect((x0+2,108,x1-2,108),'#E0C482')
 text((x0+x1)//2-len(label)*6,108,label,'#F2EEDF',True)
im.save(a/'textures/font/recycle.png')
for name in ['bar_filled','bar_empty','bar_handle']:
 tile=Image.new('RGBA',(16,16));td=ImageDraw.Draw(tile)
 td.rectangle((0,5,15,12),fill='#7F826F');td.rectangle((0,5,15,5),fill='#565A48')
 if name!='bar_empty':
  td.rectangle((0,6,15,11),fill='#4EAE43');td.rectangle((0,6,15,7),fill='#78D25B');td.rectangle((0,11,15,12),fill='#378939')
 if name=='bar_handle':
  td.rectangle((11,3,15,14),fill='#5B4B2B');td.rectangle((12,4,14,13),fill='#D4C59B');td.line((12,4,14,4),fill='#F0DEAF')
 tile.save(a/f'textures/item/{name}.png')
Image.new('RGBA',(16,16)).save(a/'textures/item/gui_blank.png')
for name in ['gui_blank','bar_filled','bar_empty','bar_handle']:
 model={'parent':'minecraft:item/generated','textures':{'layer0':f'starmarket:item/{name}'}}
 if name!='gui_blank':model['display']={'gui':{'scale':[1.125,1,1]}}
 (a/f'models/item/{name}.json').write_text(json.dumps(model,indent=2))
 (a/f'items/{name}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':f'starmarket:item/{name}'}},indent=2))
patterns=['01110/10001/10011/10101/11001/10001/01110','00100/01100/00100/00100/00100/00100/11111','01110/10001/00001/00010/00100/01000/11111','11110/00001/00001/01110/00001/00001/11110','00010/00110/01010/10010/11111/00010/00010','11111/10000/10000/11110/00001/00001/11110','01110/10000/10000/11110/10001/10001/01110','11111/00001/00010/00100/01000/01000/01000','01110/10001/10001/01110/10001/10001/01110','01110/10001/10001/01111/00001/00001/01110','00000/00000/00000/00000/00000/00001/00001','00001/00001/00010/00100/01000/10000/10000','11001/11010/00100/00100/01000/10110/00110']
atlas=Image.new('RGBA',(65,7));ad=ImageDraw.Draw(atlas)
for i,pattern in enumerate(patterns):
 for y,row in enumerate(pattern.split('/')):
  for x,c in enumerate(row):
   if c=='1':ad.point((i*5+x,y),fill='white')
 # Bitmap font width is rightmost alpha column; retain a fixed six-pixel advance.
 ad.point((i*5+4,6),fill=(255,255,255,1))
atlas.save(a/'textures/font/numbers.png')
providers=[{'type':'space','advances':{chr(0xFA980+b):2**b for b in range(10)}|{chr(0xFA990+b):-(2**b) for b in range(10)}},{'type':'bitmap','file':'starmarket:font/recycle.png','height':126,'ascent':13,'chars':[chr(0xFA900)]}]
# Separate default-font glyphs keep the name positioned when InventoryView.setTitle
# serializes the title through legacy text (named font components would be lost).
name_chars='?选择物品圆石橡木原铁锭'
names=export_name_font(name_chars,a)
price_chars='?单价 金币0123456789.'
prices=export_name_font(price_chars,a,'price_line',True)
providers[0]['advances'].update({chr(0xFA9A0):0.5,chr(0xFA9A1):-0.5,chr(0xFAB00+price_chars.index(' ')):4})
providers.append({'type':'bitmap','file':'starmarket:font/price_line.png','height':8,'ascent':13-41,'chars':[''.join(chr(0xFAB00+i) for i in range(len(price_chars)))]})
providers.append({'type':'bitmap','file':'starmarket:font/item_names.png','height':10,'ascent':13-25,'chars':[''.join(chr(0xFAA00+i) for i in range(len(name_chars)))]})
for base,y in [(0xFA910,66),(0xFA930,37),(0xFA950,94)]:providers.append({'type':'bitmap','file':'starmarket:font/numbers.png','height':7,'ascent':13-y,'chars':[''.join(chr(base+i) for i in range(13))]})
# CE merges this supplementary provider list with existing minecraft:default providers.
f=out/'resourcepack/assets/minecraft/font';f.mkdir(parents=True,exist_ok=True);(f/'default.json').write_text(json.dumps({'providers':providers},ensure_ascii=True,indent=2))
# The earlier private-font draft is no longer referenced; remove only this task's generated file.
old=a/'font/recycle.json'
if old.exists():old.unlink()
(out/'pack.yml').write_text("schema-version: 1\nname: 'StarMarket 系统回收'\nauthor: 'TuZiz'\nversion: '1.3.10'\nnamespace: starmarket\ndescription: '176×126原生像素界面；连续外观数量条，分段点击选择'\n",encoding='utf-8')
def shift(n):return ''.join(chr((0xFA980 if n>=0 else 0xFA990)+b) for b in range(10) if abs(n)&(1<<b))
s=(root/'src/main/resources/gui/recycle.yml').read_text(encoding='utf-8')
title='<#FFFFFF>'+shift(-8)+chr(0xFA900)+shift(-177)+'<#30372F>{pixel-name}<#245329>{pixel-quantity}<#655F51>{pixel-price-prefix}<#76551B>{pixel-price}<#655F51>{pixel-price-suffix}<#B18A32>{pixel-total}'
s=s.replace("title: '<#CDA56F>系统回收 <#7C8A85>· 免税'","pixel-title: true\ntitle: '"+title+"'")
s=s.replace('pixel-title: true\n',"pixel-title: true\n# 与CE item_names.png字形顺序一致；变更名称字符时同步更新资源包。\npixel-name-characters: '"+name_chars+"'\npixel-empty-name: '选择物品'\npixel-price-format: '单价 {price} 金币'\npixel-price-characters: '"+price_chars+"'\n")
for key in ['#','R','S']:s=s.replace(f"  '{key}':\n",f"  '{key}':\n    item-model: 'starmarket:gui_blank'\n")
# Earnings are visible in the title overlay, so leave E decorative and transparent.
s=s.replace("  'E':\n","  'E':\n    item-model: 'starmarket:gui_blank'\n")
for key,model in [('bar-filled','bar_filled'),('bar-empty','bar_empty'),('bar-handle','bar_handle')]:s=s.replace(key+':\n',key+":\n  item-model: 'starmarket:"+model+"'\n")
s=s.replace("  'I':\n","  'I':\n"+export(root,a))
s=re.sub(r'(?m)^empty:\n',"empty:\n  item-model: 'starmarket:preview/chest'\n",s)
config=root/'server-assets/StarMarket/gui/recycle.yml';config.parent.mkdir(parents=True,exist_ok=True);config.write_text(s,encoding='utf-8')
preview=Image.new('RGBA',(176,222),'#C6C6C6');preview.paste(im,(0,0));pd=ImageDraw.Draw(preview)
for row in range(4):
 y=140+row*18+(4 if row==3 else 0)
 for col in range(9):
  x=8+col*18;pd.rectangle((x-1,y-1,x+16,y+16),fill='#FFFFFF');pd.rectangle((x-1,y-1,x+15,y+15),fill='#373737');pd.rectangle((x,y,x+15,y+15),fill='#8B8B8B')
for i in range(7):
 tile=Image.open(a/('textures/item/bar_handle.png' if i==6 else 'textures/item/bar_filled.png')).resize((18,16),Image.Resampling.NEAREST)
 preview.alpha_composite(tile,(25+i*18,72))
for value,y,center in [('256/256',66,88),('25.60',94,110)]:
 x=center-len(value)*3
 for c in value:
  idx='0123456789./%'.index(c);part=atlas.crop((idx*5,0,idx*5+5,7));color=(36,83,41) if y==66 else (177,138,50);part.putdata([(*color,p[3]) for p in part.getdata()]);preview.alpha_composite(part,(x,y));x+=6
def name_preview(target,scale):
 value='圆石';left=109-(len(value)*11-1)/2
 for i,c in enumerate(value):
  part=names.crop((name_chars.index(c)*16,0,name_chars.index(c)*16+16,16))
  part.putdata([(48,55,47,p[3]) for p in part.get_flattened_data()])
  target.alpha_composite(part.resize((10*scale,10*scale),Image.Resampling.NEAREST),(round((left+i*11)*scale),25*scale))
 value='单价 0.1 金币';advance=lambda c:4 if c==' ' else 5 if ord(c)<128 else 9
 x=109-(sum(map(advance,value))-1)/2
 for c in value:
  if c!=' ':
   part=prices.crop((price_chars.index(c)*16,0,price_chars.index(c)*16+16,16))
   color=(118,85,27) if c in '0123456789.' else (101,95,81)
   part.putdata([(*color,p[3]) for p in part.get_flattened_data()])
   target.alpha_composite(part.resize((8*scale,8*scale),Image.Resampling.NEAREST),(round(x*scale),41*scale))
  x+=advance(c)
folder=root/'design/recycle-slider'
large=preview.resize((704,888),Image.Resampling.NEAREST);name_preview(large,4);large.save(folder/'recycle-native-skin-4x.png')
large.crop((57*4,18*4,163*4,53*4)).save(folder/'recycle-nameplate-preview-4x.png')
name_preview(preview,1);preview.save(folder/'recycle-native-skin-176x222.png')
print('Exported native 176x126 skin and 16x16 bar textures')
