package tw.junba.reminder;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Durable local audit log. No access to the Clock app's private data. */
public final class NativeLinkStore {
    private NativeLinkStore() {}
    private static JSONArray read(Context c) throws JSONException {return new JSONArray(c.getSharedPreferences("junba",0).getString("native_links","[]"));}
    static synchronized JSONArray all(Context c){try{return read(c);}catch(JSONException e){return new JSONArray();}}
    private static void save(Context c,JSONArray a){
        if(!c.getSharedPreferences("junba",0).edit().putString("native_links",a.toString()).commit())throw new IllegalStateException("原廠核對紀錄未寫入。");
    }
    private static JSONObject find(JSONArray a,String key){
        for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&key.equals(r.optString("key")))return r;}return null;
    }
    static synchronized JSONObject find(Context c,String key){return find(all(c),key);}
    static synchronized void validate(Context c) throws JSONException {read(c);}
    static boolean has(Context c,String key){return find(c,key)!=null;}
    static synchronized void requested(Context c,JSONObject source,SimpleNote chosen) throws JSONException {
        JSONArray old=read(c);JSONObject request=NativeAlarmModel.requested(find(old,NativeAlarmModel.key(source)),source,chosen,System.currentTimeMillis());
        save(c,NativeAlarmModel.replace(old,request));
    }
    static synchronized void registered(Context c,JSONObject source,SimpleNote chosen) throws JSONException {
        JSONArray old=read(c);JSONObject request=NativeAlarmModel.registered(find(old,NativeAlarmModel.key(source)),source,chosen,System.currentTimeMillis());
        save(c,NativeAlarmModel.replace(old,request));
    }
    static synchronized void mark(Context c,String key,JSONObject source,JSONObject shown,String status) throws JSONException {
        JSONArray old=read(c);JSONObject current=find(old,key);
        if(!NativeAlarmModel.sameRequest(current,shown))throw new IllegalStateException("送交版本已變更，請重新核對。");
        JSONObject request=NativeAlarmModel.mark(current,source,status,System.currentTimeMillis());
        save(c,NativeAlarmModel.replace(old,request));
    }
    static synchronized void reconcile(Context c,List<CalendarModel.Event> events,Set<Long> selected,Set<String> desired,long now,long first,long until,boolean alertsComplete) throws JSONException {
        Map<String,CalendarModel.Event> byKey=new HashMap<>();for(CalendarModel.Event event:events)byKey.put(event.key(),event);
        JSONArray requests=read(c);boolean changed=false;
        for(int i=0;i<requests.length();i++){JSONObject request=requests.optJSONObject(i);if(request==null||request.optBoolean("needsReview"))continue;
            JSONObject snapshot=request.optJSONObject("sourceSnapshot");if(snapshot==null||!snapshot.has("eventId")||!selected.contains(snapshot.optLong("calendarId")))continue;
            if(snapshot.optLong("when")<=now||snapshot.optLong("when")>until||snapshot.optLong("eventBegin")<first||snapshot.optLong("eventBegin")>=until)continue;
            if(NativeAlarmModel.changed(snapshot,byKey.get(CalendarModel.recordEventKey(snapshot)),desired,alertsComplete)){request.put("needsReview",true);changed=true;}
        }
        if(changed)save(c,requests);
    }
}
