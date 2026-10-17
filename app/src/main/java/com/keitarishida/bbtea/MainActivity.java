package com.keitarishida.bbtea;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unofficial Malayalam Bigg Boss fan feed. No YouTube API key and no account required. */
public class MainActivity extends Activity {
    private static final int BACK = 0xFFFAF8F5, TEXT = 0xFF211B2F, MUTED = 0xFF79717B, PURPLE = 0xFF6754A4, EDGE = 0xFFECE6E8;
    private static final String UPDATES_URL = "https://raw.githubusercontent.com/KeitaRishida/SalesOrbit-releases/bigg-boss-malayalam-app/updates/update.json";
    private final ExecutorService workers = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> imageCache = new LruCache<>(24);
    private final ArrayList<YouTubeDiscovery.Item> entries = new ArrayList<>();
    private LinearLayout cards, chips;
    private TextView info, empty, updateStatus;
    private boolean loading = false;
    private String filter = "all";

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi(); restore(); refresh(); checkUpdate(false);
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int fill, int stroke, int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value, int sp, int color, boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setIncludeFontPadding(false); return t;}
    private void buildUi(){
        ScrollView scroll = new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BACK);
        LinearLayout page=vertical();page.setPadding(dp(20),dp(22),dp(20),dp(36));scroll.addView(page);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(com.keitarishida.bbtea.R.drawable.ic_launcher);logo.setBackground(shape(0xFF071A31,0,16));logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(62),dp(62));lp.rightMargin=dp(12);head.addView(logo,lp);
        LinearLayout names=vertical();TextView name=text("BB Tea",30,TEXT,true);names.addView(name);TextView sub=text("മലയാളം  ·  SEASON 8",12,PURPLE,true);sub.setPadding(0,dp(5),0,0);names.addView(sub);
        head.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView reload=text("↻",29,PURPLE,true);reload.setGravity(Gravity.CENTER);reload.setContentDescription("Refresh latest YouTube uploads");reload.setBackground(shape(0xFFF0EBF7,0,22));head.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));reload.setOnClickListener(v->refresh());page.addView(head);
        TextView tag=text("What's the latest tea? ☕",23,TEXT,true);tag.setPadding(0,dp(28),0,dp(6));page.addView(tag);
        TextView summary=text("Season 8 videos and gossip from YouTube. Newest first. Tap to watch.",14,MUTED,false);summary.setLineSpacing(dp(3),1f);page.addView(summary);
        info=text("Checking YouTube…",12,MUTED,false);info.setPadding(0,dp(16),0,dp(14));page.addView(info);
        chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);page.addView(chips);drawFilters();
        cards=vertical();LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);clp.topMargin=dp(15);page.addView(cards,clp);
        empty=text("Finding the latest updates…",16,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(18),dp(45),dp(18),dp(45));cards.addView(empty);
        LinearLayout foot=vertical();foot.setPadding(0,dp(18),0,0);page.addView(foot);
        TextView search=text("Search more on YouTube  ↗",14,PURPLE,true);search.setGravity(Gravity.CENTER);search.setPadding(dp(16),dp(16),dp(16),dp(16));search.setBackground(shape(0xFFF0EBF7,0,18));foot.addView(search);search.setOnClickListener(v->open("https://www.youtube.com/results?search_query=Bigg+Boss+Malayalam+Season+8+latest&sp=CAI%3D"));
        updateStatus=text("Check for app updates",12,MUTED,false);updateStatus.setGravity(Gravity.CENTER);updateStatus.setPadding(0,dp(22),0,dp(10));foot.addView(updateStatus);updateStatus.setOnClickListener(v->checkUpdate(true));
        TextView note=text("Unofficial fan app · Search timestamps are approximate; channel-feed timestamps are exact. Rumours and opinions are not official announcements.",11,MUTED,false);note.setGravity(Gravity.CENTER);note.setLineSpacing(dp(3),1f);foot.addView(note);
        setContentView(scroll);
    }
    private void drawFilters(){
        if(chips==null)return;chips.removeAllViews();String[][] types={{"all","All updates"},{"official","Official"},{"discussion","Tea & reviews"}};
        for(String[] type:types){boolean selected=type[0].equals(filter);TextView b=text(type[1],12,selected?Color.WHITE:PURPLE,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(13),dp(12),dp(13),dp(12));b.setBackground(shape(selected?PURPLE:0xFFF0EBF7,0,24));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),type[0].equals("discussion")?1.3f:1f);p.rightMargin=dp(6);chips.addView(b,p);b.setOnClickListener(v->{filter=type[0];drawFilters();render();});}
    }
    private void restore(){try{String saved=getPreferences(MODE_PRIVATE).getString("feed","");if(saved.isEmpty())return;JSONArray a=new JSONArray(saved);for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);YouTubeDiscovery.Item v=YouTubeDiscovery.Item.fromJson(j);if(v.published>=YouTubeDiscovery.EARLIEST_MS)entries.add(v);}sort();render();info.setText("Saved updates · refreshing…");}catch(Exception ignored){}}
    private void refresh(){
        if(loading)return;loading=true;info.setText("Searching YouTube for Season 8 videos…");
        workers.execute(()->{
            ArrayList<YouTubeDiscovery.Item> found=new ArrayList<>();
            ArrayList<String> errors=new ArrayList<>();int successful=0;
            java.util.concurrent.CompletionService<ArrayList<YouTubeDiscovery.Item>> jobs = new java.util.concurrent.ExecutorCompletionService<>(workers);
            int submitted=0;
            for(String query:YouTubeDiscovery.QUERIES){
                jobs.submit(()->YouTubeDiscovery.search(query).videos);submitted++;
            }
            jobs.submit(YouTubeDiscovery::backup);submitted++;
            for(String[] ch:YouTubeDiscovery.CHANNELS){jobs.submit(()->YouTubeDiscovery.feed(ch));submitted++;}
            for(int i=0;i<submitted;i++){
                try{found.addAll(jobs.take().get());successful++;}
                catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();errors.add(cause.getMessage()==null?"Unavailable":cause.getMessage());}
            }
            int sourceCount=successful;int freshCount=found.size();String failure=errors.isEmpty()?"":errors.get(0);
            main.post(()->{
                if(isFinishing())return;loading=false;
                HashSet<String> seen=new HashSet<>();ArrayList<YouTubeDiscovery.Item> merged=new ArrayList<>();
                found.addAll(entries);
                // Prefer exact RSS timestamps over approximate YouTube search timestamps.
                for(YouTubeDiscovery.Item v:found)if(!v.approximate && v.published>=YouTubeDiscovery.EARLIEST_MS && seen.add(v.id))merged.add(v);
                for(YouTubeDiscovery.Item v:found)if(v.published>=YouTubeDiscovery.EARLIEST_MS && seen.add(v.id))merged.add(v);
                entries.clear();entries.addAll(merged);sort();while(entries.size()>180)entries.remove(entries.size()-1);
                if(!entries.isEmpty()){
                    try{JSONArray arr=new JSONArray();for(YouTubeDiscovery.Item v:entries)arr.put(v.json());getPreferences(MODE_PRIVATE).edit().putString("feed",arr.toString()).apply();}catch(Exception ignored){}
                    info.setText(entries.size()+" videos · newest first · checked "+timeNow()+ (freshCount==0?" · cached":""));
                }else if(sourceCount==0){
                    info.setText("YouTube couldn't be reached. Check internet and tap ↻. "+failure);
                }else{
                    info.setText("No Season 8 matches found. Search YouTube below or retry. "+failure);
                }
                render();
            });
        });
    }
    private void sort(){Collections.sort(entries,(a,b)->Long.compare(b.published,a.published));}
    private String dateOnly(long time){SimpleDateFormat f=new SimpleDateFormat("d MMM yyyy",Locale.ENGLISH);f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));return f.format(new Date(time));}
    private String timeNow(){return new SimpleDateFormat("h:mm a",Locale.ENGLISH).format(new Date());}
    private String date(long time){SimpleDateFormat f=new SimpleDateFormat("d MMM yyyy · h:mm a",Locale.ENGLISH);f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));return f.format(new Date(time))+" IST";}
    private void render(){
        if(cards==null)return;cards.removeAllViews();int count=0;
        for(YouTubeDiscovery.Item v:entries){if(!filter.equals("all")&&!v.category.equals(filter))continue;card(v);count++;}
        if(count==0){String message=loading?"Looking for the latest tea…":entries.isEmpty()?"No matching videos loaded. Tap ↻ or use YouTube search below.":"No videos in this category yet.";TextView t=text(message,15,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(50),dp(14),dp(50));cards.addView(t);}
    }
    private void card(YouTubeDiscovery.Item v){
        LinearLayout outer=vertical();outer.setBackground(shape(Color.WHITE,EDGE,20));outer.setPadding(dp(12),dp(12),dp(12),dp(13));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);outer.addView(row);
        ImageView picture=new ImageView(this);picture.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.setImageResource(R.drawable.ic_launcher);picture.setBackground(shape(0xFF101F43,0,13));picture.setClipToOutline(true);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(120),dp(84));ip.rightMargin=dp(12);row.addView(picture,ip);loadImage(v.id,picture);
        LinearLayout details=vertical();row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        String label=v.category.equals("official")?"OFFICIAL CLIP":"TEA / REVIEW";
        TextView kind=text(label,10,v.category.equals("official")?0xFF346B5B:PURPLE,true);details.addView(kind);
        TextView title=text(v.title,14,TEXT,true);title.setMaxLines(3);title.setEllipsize(TextUtils.TruncateAt.END);title.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(6);details.addView(title,tp);
        TextView channel=text(v.channel,11,MUTED,false);channel.setMaxLines(1);channel.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(8);details.addView(channel,cp);
        TextView posted=text("Uploaded " + (v.approximate ? "about " + (v.when.isEmpty()?dateOnly(v.published):v.when) : date(v.published)) + "   ›",11,MUTED,false);posted.setPadding(0,dp(12),0,0);outer.addView(posted);
        outer.setOnClickListener(w->open("https://www.youtube.com/watch?v="+v.id));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(11);cards.addView(outer,p);
    }
    private void loadImage(String id,ImageView target){Bitmap cached=imageCache.get(id);if(cached!=null){target.setImageBitmap(cached);return;}
        workers.execute(()->{try{HttpURLConnection conn=(HttpURLConnection)new URL("https://i.ytimg.com/vi/"+id+"/mqdefault.jpg").openConnection();conn.setConnectTimeout(5000);conn.setReadTimeout(5000);try(InputStream in=conn.getInputStream()){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){imageCache.put(id,b);main.post(()->{if(!isFinishing())target.setImageBitmap(b);});}}finally{conn.disconnect();}}catch(Exception ignored){}
        });
    }
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"No app available to open this video",Toast.LENGTH_SHORT).show();}}
    private String readString(String url) throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7500);c.setReadTimeout(7500);try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toString(StandardCharsets.UTF_8.name());}finally{c.disconnect();}}
    private void checkUpdate(boolean showNoUpdate){workers.execute(()->{try{JSONObject j=new JSONObject(readString(UPDATES_URL+"?ts="+System.currentTimeMillis()));int latest=j.getInt("versionCode");String apk=j.getString("apkUrl");String sha=j.getString("sha256");if(latest>BuildConfigVersion()){main.post(()->{updateStatus.setText("⬇  New app update available · tap to install");updateStatus.setTextColor(PURPLE);updateStatus.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Update BB Tea?").setMessage("Download and install the latest signed version.").setNegativeButton("Not now",null).setPositiveButton("Update",(d,w)->downloadUpdate(apk,sha)).show());});}else if(showNoUpdate){main.post(()->Toast.makeText(this,"You're up to date!",Toast.LENGTH_SHORT).show());}}catch(Exception ex){if(showNoUpdate)main.post(()->Toast.makeText(this,"Could not check for updates",Toast.LENGTH_SHORT).show());}});}
    private int BuildConfigVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionCode;}catch(Exception e){return 1;}}
    private void downloadUpdate(String url,String sha){updateStatus.setText("Downloading update…");workers.execute(()->{HttpURLConnection c=null;try{
        c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);
        if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());
        MessageDigest digest=MessageDigest.getInstance("SHA-256");File f=new File(getCacheDir(),"bbtea-update.apk");
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0){out.write(b,0,n);digest.update(b,0,n);}}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));if(!hex.toString().equalsIgnoreCase(sha)){f.delete();throw new Exception("SHA mismatch");}
        main.post(()->launchInstaller());
    }catch(Exception e){main.post(()->{updateStatus.setText("Update failed · tap to retry");Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}finally{if(c!=null)c.disconnect();}});}
    private void launchInstaller(){
        try{
            if(android.os.Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
                Toast.makeText(this,"Please allow BB Tea to install its update, then tap update again.",Toast.LENGTH_LONG).show();
                Intent request=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));startActivity(request);return;
            }
            Uri uri=Uri.parse("content://com.keitarishida.bbtea.installer/update.apk");Intent install=new Intent(Intent.ACTION_VIEW);install.setDataAndType(uri,"application/vnd.android.package-archive");install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(install);
        }catch(Exception e){Toast.makeText(this,"Installer unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onDestroy(){workers.shutdownNow();super.onDestroy();}
}