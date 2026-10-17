"""Free public YouTube discovery cache for BB Tea. Retains last known good feed on failures."""
import datetime as dt
import html
import json
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

NOW=dt.datetime.now(dt.timezone.utc)
EARLIEST=dt.datetime(2026,8,1,tzinfo=dt.timezone.utc).timestamp()*1000
OUT=Path('feed/videos.json')
QUERIES=['Bigg Boss Malayalam Season 8','BBMS8 Malayalam','ബിഗ് ബോസ് മലയാളം സീസൺ 8','Bigg Boss Malayalam 8 review eviction']
CHANNELS=[('UCp_r6Z-Oh0YTf-ym71z5Nqg','Asianet','official'),('UCEMJD5tkA88xBDM1Or7wWAw','BIG BOSS MALLU TALKS','discussion')]
HEADERS={'User-Agent':'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36','Accept-Language':'en-IN,en;q=0.9'}
OLD=re.compile(r'(?:\b(?:season|s)\s*[-:#]?\s*[1-7]\b|\bbbms?[1-7]\b|സീസ[ൺണ്\u200d\u200c]*\s*[1-7])',re.I)
NEW=re.compile(r'(?:\b(?:season|s)\s*[-:#]?\s*8\b|\bbbms?8(?:promo)?\b|\bbigg?\s*boss\s*(?:malayalam\s*)?8\b|\bbigg?\s*boss\s*malayalam\b|#biggbossmalayalam|സീസ[ൺണ്\u200d\u200c]*\s*8)',re.I)
MAL=re.compile(r'(?:malayalam|mallu|asianet|കേരള|മലയാള|ബിഗ്.?ബോസ്|bbms?8)',re.I)
OTHER=re.compile(r'bigg?\s*boss\s*(?:hindi|tamil|telugu|kannada|marathi|bengali)',re.I)
AGE=re.compile(r'(\d+)\s*(second|minute|hour|day|week|month|year)s?\s*ago',re.I)
UNITS={'second':1,'minute':60,'hour':3600,'day':86400,'week':604800,'month':2629800,'year':31557600}

def text(obj):
    if not isinstance(obj,dict):return ''
    return obj.get('simpleText') or obj.get('content') or ''.join(r.get('text','') for r in obj.get('runs',[]))

def eligible(title,description,channel,ts):
    if ts<EARLIEST or ts>NOW.timestamp()*1000+86400000:return False
    if OLD.search(title) or OTHER.search(title):return False
    if not NEW.search(title+' '+description) or not MAL.search(title+' '+description+' '+channel):return False
    return True

def published(relative):
    if 'yesterday' in relative.lower():return int((NOW.timestamp()-86400)*1000)
    m=AGE.search(relative)
    if not m:return 0
    return int((NOW.timestamp()-int(m[1])*UNITS[m[2].lower()])*1000)

def traverse(node):
    if isinstance(node,dict):
        for key in ('videoRenderer','gridVideoRenderer'):
            if isinstance(node.get(key),dict):yield node[key]
        for value in node.values():yield from traverse(value)
    elif isinstance(node,list):
        for value in node:yield from traverse(value)

def search(query):
    params=urllib.parse.urlencode({'search_query':query,'sp':'CAI=','hl':'en','gl':'IN'})
    request=urllib.request.Request('https://www.youtube.com/results?'+params,headers=HEADERS)
    with urllib.request.urlopen(request,timeout=18) as response:page=response.read(7_000_000).decode('utf8','replace')
    m=re.search(r'(?:var\s+)?ytInitialData\s*=\s*',page)
    if not m:raise ValueError('YouTube returned no searchable JSON')
    obj,_=json.JSONDecoder().raw_decode(page[page.find('{',m.end()):])
    found=[]
    for raw in traverse(obj):
        video_id=raw.get('videoId','')
        if not re.fullmatch(r'[A-Za-z0-9_-]{11}',video_id):continue
        title=text(raw.get('title'));owner=text(raw.get('ownerText')) or text(raw.get('longBylineText')) or text(raw.get('shortBylineText'))
        desc=text(raw.get('descriptionSnippet'))+' '+' '.join(text(x.get('snippetText')) for x in raw.get('detailedMetadataSnippets',[]))
        when=text(raw.get('publishedTimeText'));time=published(when)
        if eligible(title,desc,owner,time):
            found.append({'id':video_id,'title':title,'channel':owner,'category':'official' if owner.lower().strip() in ('asianet','jiohotstar malayalam') else 'discussion','when':when,'published':time,'approximate':True,'source':'YouTube search'})
    return found

def rss(channel,owner,category):
    req=urllib.request.Request('https://www.youtube.com/feeds/videos.xml?channel_id='+channel,headers=HEADERS)
    with urllib.request.urlopen(req,timeout=15) as response:data=response.read(700_000)
    tree=ET.fromstring(data)
    ns={'a':'http://www.w3.org/2005/Atom','yt':'http://www.youtube.com/xml/schemas/2015','media':'http://search.yahoo.com/mrss/'}
    results=[]
    for item in tree.findall('a:entry',ns):
        def get(path):return item.findtext(path,'',namespaces=ns)
        title=get('a:title');description=get('media:group/media:description');published_dt=dt.datetime.fromisoformat(get('a:published').replace('Z','+00:00'))
        ts=int(published_dt.timestamp()*1000)
        if eligible(title,description,owner,ts):results.append({'id':get('yt:videoId'),'title':title,'channel':owner,'category':category,'when':'','published':ts,'approximate':False,'source':'YouTube channel'})
    return results

def main():
    results=[];ok=0
    for query in QUERIES:
        try:
            new=search(query);results.extend(new);ok+=1;print('search',query,len(new))
        except Exception as e:print('search unavailable',query,str(e),file=sys.stderr)
    for channel,name,category in CHANNELS:
        try:
            new=rss(channel,name,category);results.extend(new);ok+=1;print('rss',name,len(new))
        except Exception as e:print('rss unavailable',name,str(e),file=sys.stderr)
    old=[]
    if OUT.exists():
        try:old=json.loads(OUT.read_text()).get('videos',[])
        except (json.JSONDecodeError,OSError):pass
    results.extend(old)
    best={}
    for v in results:
        if not re.fullmatch(r'[A-Za-z0-9_-]{11}',v.get('id','')):continue
        if int(v.get('published') or 0)<EARLIEST:continue
        prev=best.get(v['id'])
        if not prev or (prev.get('approximate',True) and not v.get('approximate',True)):best[v['id']]=v
    selected=sorted(best.values(),key=lambda x:x['published'],reverse=True)[:180]
    if not selected:
        print('No matches: retain prior feed and signal failure, do not publish empty feed',file=sys.stderr)
        sys.exit(1)
    # Write only when discoveries changed; avoid pointless commits from slight shifts in relative-time estimates.
    def signature(items):return [(x['id'],x['title'],x['channel'],x['category']) for x in items]
    if signature(selected)==signature(old):
        print('No new videos, leaving cached timestamps unchanged');return
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps({'season':8,'updated':NOW.isoformat(),'videos':selected},ensure_ascii=False,indent=2)+'\n')
    print('Published',len(selected),'total videos, sources online:',ok)

if __name__=='__main__':main()