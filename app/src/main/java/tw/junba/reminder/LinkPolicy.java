package tw.junba.reminder;

import android.content.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Independent opt-outs survive removal of visible history and refreshes. */
final class LinkPolicy {
    static final String PREF="junba_link_policy";
    static boolean prelink(Context c){return c.getSharedPreferences(PREF,0).getBoolean("prelink",true);}
    static boolean noteEnabled(Context c,int id,boolean legacy){return c.getSharedPreferences(PREF,0).getBoolean("note_"+id,legacy);}
    static void note(Context c,int id,boolean enabled){if(!c.getSharedPreferences(PREF,0).edit().putBoolean("note_"+id,enabled).commit())throw new IllegalStateException("記事連動設定未保存。");}
    static boolean eventDisabled(Context c,String eventKey){return !eventKey.isEmpty()&&disabled(c,"event:"+eventKey);}
    static void event(Context c,String eventKey,boolean disabled){if(!eventKey.isEmpty())set(c,"event:"+eventKey,disabled);}
    static boolean recordDisabled(Context c,org.json.JSONObject record){return !LinkModel.test(record)&&(disabled(c,record.optString("linkId"))||eventDisabled(c,CalendarModel.recordEventKey(record)));}
    private static String key(String id){
        try{StringBuilder s=new StringBuilder("off_");for(byte b:MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static boolean disabled(Context c,String id){return !id.isEmpty()&&c.getSharedPreferences(PREF,0).getBoolean(key(id),false);}
    static void set(Context c,String id,boolean disabled){
        if(id.isEmpty())return;
        if(!c.getSharedPreferences(PREF,0).edit().putBoolean(key(id),disabled).commit())throw new IllegalStateException("連動選擇未保存，請重試。");
    }
}
