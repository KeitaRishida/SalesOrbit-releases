package com.keitarishida.bbtea;

import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Search YouTube's publicly rendered results (no personal API key needed).
 *  YouTube may modify/block this page; failures are reported and a public backup feed is used.
 */
final class YouTubeDiscovery {
    static final int SEASON = 8;
    static final String BACKUP_URL = "https://raw.githubusercontent.com/KeitaRishida/SalesOrbit-releases/bigg-boss-malayalam-app/feed/videos.json";
    static final String[][] CHANNELS = {
        {"UCp_r6Z-Oh0YTf-ym71z5Nqg", "Asianet", "official"},
        {"UCEMJD5tkA88xBDM1Or7wWAw", "BIG BOSS MALLU TALKS", "discussion"}
    };
    static final String[] QUERIES = {
        "Bigg Boss Malayalam Season 8",
        "BBM8 Bigg Boss Malayalam latest",
        "ബിഗ് ബോസ് മലയാളം സീസൺ 8",
        "Bigg Boss Malayalam 8 review eviction"
    };
    static final long EARLIEST_MS = Instant.parse("2026-08-01T00:00:00Z").toEpochMilli();
    static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{11}");
    static final Pattern OLD = Pattern.compile("(?i)(?:\\b(?:season|seasons|s)\\s*[-:#]?\\s*[1-7]\\b|\\bbbms?[1-7]\\b|സീസ[ൺണ്\\u200d\\u200c]*\\s*[1-7])");
    static final Pattern NEW = Pattern.compile("(?i)(?:\\b(?:season|s)\\s*[-:#]?\\s*8\\b|\\bbbms?8[a-z]*\\b|\\bbig\\s*g?\\s*boss\\s*(?:malayalam\\s*)?8\\b|\\bbig\\s*g?\\s*boss\\s*malayalam\\b|#biggbossmalayalam|#bbm8|#bbms8|സീസ[ൺണ്\\u200d\\u200c]*\\s*8)");
    static final Pattern MALAYALAM = Pattern.compile("(?i)(?:malayalam|mallu|കേരള|മലയാള|ബിഗ്.?\\s*ബോസ്|ബിഗ്.?ബോസ്|bbm8|bbms8|asianet)");
    static final Pattern OTHER_LANGUAGE = Pattern.compile("(?i)(?:bigg?\\s*boss\\s*(?:hindi|kannada|tamil|telugu|marathi|bengali)|\\bbb(?:k|t|h)\\s*8\\b)");
    private YouTubeDiscovery() { }

    static class Item {
        String id="", title="", channel="", category="discussion", when="", source="", description="";
        long published=0;
        boolean approximate=true;
        JSONObject json() throws Exception {
            JSONObject j=new JSONObject();j.put("id",id);j.put("title",title);j.put("channel",channel);
            j.put("category",category);j.put("published",published);j.put("when",when);
            j.put("approximate",approximate);j.put("source",source);j.put("description",description);return j;
        }
        static Item fromJson(JSONObject j) {
            Item v=new Item();v.id=j.optString("id");v.title=j.optString("title");v.channel=j.optString("channel");
            v.category=j.optString("category","discussion");v.published=j.optLong("published",0);
            v.when=j.optString("when");v.description=j.optString("description");v.approximate=j.optBoolean("approximate",false);v.source=j.optString("source");return v;
        }
    }
    static class Batch {
        final ArrayList<Item> videos=new ArrayList<>();
        String message="";
    }
    static boolean relevant(String title,String description,String channel,long published) {
        if(title==null||title.trim().length()<3)return false;
        String t=title.toLowerCase(Locale.ROOT);
        String combined=(title+" "+description+" "+channel).toLowerCase(Locale.ROOT);
        if(OTHER_LANGUAGE.matcher(t).find()||OLD.matcher(t).find())return false;
        if(published>0 && (published<EARLIEST_MS || published>System.currentTimeMillis()+86400000L))return false;
        // A specific current-season signal in title, description or hashtags is required.
        boolean explicitSeason = NEW.matcher(title+" "+description).find();
        boolean malayalamContext = MALAYALAM.matcher(combined).find();
        // If title explicitly says "Bigg Boss Malayalam", that's already a current-show match.
        if(!explicitSeason || !malayalamContext)return false;
        // Exclude videos clearly talking about a different season even when hashtags mention 8.
        if(OLD.matcher(title+" "+description).find() && !Pattern.compile("(?i)(?:\\bbbms?8[a-z]*\\b|\\bseason\\s*8\\b|സീസൺ\\s*8)").matcher(title).find())return false;
        return true;
    }
    static String get(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7500);c.setReadTimeout(10000);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Language","en-IN,en;q=0.9");c.setRequestProperty("Accept-Encoding","gzip");
        try {int status=c.getResponseCode();if(status!=200)throw new Exception("HTTP "+status);
            InputStream stream=c.getInputStream();if("gzip".equalsIgnoreCase(c.getContentEncoding()))stream=new GZIPInputStream(stream);
            try(InputStream in=stream;ByteArrayOutputStream b=new ByteArrayOutputStream()){
                byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){b.write(buffer,0,n);if(b.size()>7000000)throw new Exception("Page too large");}
                return b.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
    static Batch search(String query) throws Exception {
        String url="https://www.youtube.com/results?search_query="+URLEncoder.encode(query,"UTF-8")+"&sp=CAI%3D&hl=en&gl=IN";
        String page=get(url);
        String json=embeddedData(page);
        if(json==null)throw new Exception(page.contains("consent.youtube")?"YouTube consent page":"YouTube returned no results data");
        Batch b=new Batch();scan(new JSONObject(json),b.videos,0);
        HashSet<String> ids=new HashSet<>();ArrayList<Item> filtered=new ArrayList<>();
        for(Item v:b.videos){if(!ids.add(v.id))continue;filtered.add(v);}
        b.videos.clear();b.videos.addAll(filtered);
        b.message="YouTube "+query+": "+filtered.size();return b;
    }
    /** Find the complete ytInitialData JSON object, respecting embedded JS quotes/escapes. */
    static String embeddedData(String html) {
        String[] tokens={"var ytInitialData =", "ytInitialData =", "window[\"ytInitialData\"] =", "window['ytInitialData'] ="};
        for(String token:tokens){int at=html.indexOf(token);if(at<0)continue;int start=html.indexOf('{',at+token.length());if(start<0 || start-at>250)continue;
            int depth=0;boolean inString=false,escaped=false;
            for(int i=start;i<html.length();i++){char ch=html.charAt(i);
                if(inString){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')inString=false;}
                else if(ch=='"')inString=true;else if(ch=='{')depth++;else if(ch=='}'){depth--;if(depth==0)return html.substring(start,i+1);}
            }
        }
        return null;
    }
    private static void scan(Object node,ArrayList<Item> out,int depth){
        if(depth>48 || out.size()>300)return;
        if(node instanceof JSONObject){JSONObject j=(JSONObject)node;
            JSONObject renderer=j.optJSONObject("videoRenderer");if(renderer==null)renderer=j.optJSONObject("gridVideoRenderer");
            if(renderer!=null){Item v=extractVideo(renderer);if(v!=null)out.add(v);}
            JSONObject lockup=j.optJSONObject("lockupViewModel");if(lockup!=null){Item v=extractLockup(lockup);if(v!=null)out.add(v);}
            JSONArray names=j.names();if(names!=null)for(int i=0;i<names.length();i++)scan(j.opt(names.optString(i)),out,depth+1);
        }else if(node instanceof JSONArray){JSONArray ar=(JSONArray)node;for(int i=0;i<ar.length();i++)scan(ar.opt(i),out,depth+1);}
    }
    static String richText(JSONObject o) {
        if(o==null)return "";
        String plain=o.optString("simpleText",o.optString("content",""));if(!plain.isEmpty())return plain;
        JSONArray runs=o.optJSONArray("runs");StringBuilder b=new StringBuilder();if(runs!=null)for(int i=0;i<runs.length();i++)b.append(runs.optJSONObject(i)==null?"":runs.optJSONObject(i).optString("text",""));
        return b.toString();
    }
    private static String description(JSONObject renderer){
        StringBuilder x=new StringBuilder(richText(renderer.optJSONObject("descriptionSnippet")));
        JSONArray snippets=renderer.optJSONArray("detailedMetadataSnippets");
        if(snippets!=null)for(int i=0;i<snippets.length();i++){JSONObject a=snippets.optJSONObject(i);if(a!=null)x.append(' ').append(richText(a.optJSONObject("snippetText")));}
        return x.toString();
    }
    static Item extractVideo(JSONObject r){
        String id=r.optString("videoId","");if(!ID.matcher(id).matches())return null;
        String title=richText(r.optJSONObject("title"));String owner=richText(r.optJSONObject("ownerText"));
        if(owner.isEmpty())owner=richText(r.optJSONObject("longBylineText"));
        if(owner.isEmpty())owner=richText(r.optJSONObject("shortBylineText"));
        String when=richText(r.optJSONObject("publishedTimeText"));
        long date=parseRelative(when);if(date==0)return null; // never fake an upload date
        String desc=description(r);if(!relevant(title,desc,owner,date))return null;
        Item v=new Item();v.id=id;v.title=title;v.channel=owner;v.when=when;
        v.published=date;v.description=desc;v.approximate=true;v.category=isOfficial(owner)?"official":"discussion";v.source="YouTube search";return v;
    }
    static Item extractLockup(JSONObject r){
        String id=r.optString("contentId","");if(!ID.matcher(id).matches())return null;
        String title=richText(r.optJSONObject("title"));if(title.isEmpty())title=r.optString("title","");
        StringBuilder metadata=new StringBuilder();ArrayList<String> tokens=new ArrayList<>();collectStrings(r.optJSONObject("metadata"),tokens,0);
        for(String s:tokens)metadata.append(s).append(' ');
        String when="";for(String s:tokens){if(s.matches("(?i).*\\b(ago|yesterday)\\b.*")){when=s;break;}}
        long date=parseRelative(when);if(date==0)return null;
        String meta=metadata.toString();if(!relevant(title,meta,"",date))return null;
        Item v=new Item();v.id=id;v.title=title;v.channel="YouTube creator";v.when=when;v.published=date;
        v.description=meta;v.category="discussion";v.source="YouTube search";return v;
    }
    private static void collectStrings(Object node,ArrayList<String> out,int depth){if(node==null||depth>12||out.size()>50)return;
        if(node instanceof JSONObject){JSONObject j=(JSONObject)node;String content=j.optString("content","");if(!content.isEmpty())out.add(content);
            JSONArray names=j.names();if(names!=null)for(int i=0;i<names.length();i++)collectStrings(j.opt(names.optString(i)),out,depth+1);
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectStrings(a.opt(i),out,depth+1);}
    }
    static long parseRelative(String value){
        if(value==null)return 0;String s=value.toLowerCase(Locale.ENGLISH).replace("streamed", "").replace("premiered", "").trim();
        if(s.contains("yesterday"))return System.currentTimeMillis()-86400000L;
        Matcher m=Pattern.compile("(\\d+)\\s*(second|minute|hour|day|week|month|year)s?\\s*ago").matcher(s);
        if(!m.find())return 0;long n=Long.parseLong(m.group(1));long unit;
        switch(m.group(2)){case "second":unit=1000L;break;case "minute":unit=60000L;break;case "hour":unit=3600000L;break;
            case "day":unit=86400000L;break;case "week":unit=604800000L;break;case "month":unit=2629800000L;break;default:unit=31557600000L;}
        return Math.max(1,System.currentTimeMillis()-n*unit);
    }
    static boolean isOfficial(String channel){String c=channel.toLowerCase(Locale.ROOT);return c.equals("asianet")||c.contains("jiohotstar")||c.contains("hotstar malayalam");}
    static ArrayList<Item> feed(String[] channel) throws Exception {
        ArrayList<Item> out=new ArrayList<>();String xml=get("https://www.youtube.com/feeds/videos.xml?channel_id="+channel[0]);
        XmlPullParser p=Xml.newPullParser();p.setInput(new java.io.StringReader(xml));int event;Item item=null;boolean author=false;String desc="";
        while((event=p.next())!=XmlPullParser.END_DOCUMENT){String tag=p.getName();
            if(event==XmlPullParser.START_TAG){if("entry".equals(tag)){item=new Item();item.channel=channel[1];item.category=channel[2];item.source="YouTube channel";desc="";}
                else if(item!=null){if("author".equals(tag))author=true;else if("videoId".equals(tag))item.id=p.nextText();
                    else if("title".equals(tag)&&!author&&item.title.isEmpty())item.title=p.nextText();
                    else if("name".equals(tag)&&author)item.channel=p.nextText();
                    else if("published".equals(tag)){String t=p.nextText();try{item.published=Instant.parse(t).toEpochMilli();}catch(Exception e){item.published=OffsetDateTime.parse(t).toInstant().toEpochMilli();}item.approximate=false;}
                    else if("description".equals(tag))desc=p.nextText();}
            }else if(event==XmlPullParser.END_TAG){if("author".equals(tag))author=false;
                if("entry".equals(tag)&&item!=null){if(ID.matcher(item.id).matches()&&relevant(item.title,desc,item.channel,item.published)){item.description=desc;out.add(item);}item=null;}}
        }
        return out;
    }
    static ArrayList<Item> backup() throws Exception {
        String s=get(BACKUP_URL+"?t="+System.currentTimeMillis()/3600000L);
        JSONObject root=new JSONObject(s);if(root.optInt("season",SEASON)!=SEASON)throw new Exception("Backup season mismatch");
        JSONArray array=root.optJSONArray("videos");ArrayList<Item> out=new ArrayList<>();
        if(array!=null)for(int i=0;i<array.length();i++){JSONObject j=array.optJSONObject(i);if(j==null)continue;Item v=Item.fromJson(j);
            if(ID.matcher(v.id).matches()&&relevant(v.title,v.description,v.channel,v.published))out.add(v);}
        return out;
    }
}