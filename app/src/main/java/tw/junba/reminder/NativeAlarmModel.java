package tw.junba.reminder;

import java.util.*;
import org.json.*;

/** User-reviewed Clock handoffs, never a readback of the manufacturer's alarm database. */
final class NativeAlarmModel {
    static String key(JSONObject source){
        if(source.has("noteId"))return "note:"+source.optInt("noteId");
        if(!source.optString("linkId").isEmpty())return source.optString("linkId");
        return "record:"+source.optString("key");
    }
    static String name(JSONObject source){return CalendarModel.safe(source.optString("eventTitle",source.optString("title").split("\n",2)[0]));}
    static JSONObject note(JSONObject note) throws JSONException {
        return new JSONObject().put("noteId",note.getInt("id")).put("title",note.optString("title","無訊息")+"\n"+note.optString("content","無訊息"))
            .put("when",note.optLong("time")-note.optInt("ahead")*60000L);
    }
    static JSONObject event(CalendarModel.Event event,long when) throws JSONException {
        LinkModel.Request r=new LinkModel.Request(event,when);
        return r.metadata().put("title",event.title+"\n"+event.calendar.label()).put("when",when);
    }
    static String basis(JSONObject source) throws JSONException {
        // Only source content/date changes invalidate confirmation; receipt/permission/status changes do not.
        JSONArray values=new JSONArray().put(key(source));
        for(String field:new String[]{"title","when","noteId","eventTitle","eventBegin","eventEnd","calendarId","accountName","calendarName","allDay"})
            values.put(source.has(field)?source.get(field):JSONObject.NULL);
        return values.toString();
    }
    static boolean current(JSONObject request,JSONObject source){
        if(request==null||source==null||!request.has("basis")||request.optBoolean("needsReview"))return false;
        try{return request.getString("basis").equals(basis(source));}catch(JSONException e){return false;}
    }
    static boolean confirmed(JSONObject request,JSONObject source){return current(request,source)&&"manual-confirmed".equals(request.optString("status"));}
    static String status(JSONObject request,JSONObject source){
        if(request==null)return "尚未送交原廠";
        if("manual-removed".equals(request.optString("status")))return "本人已標記在原廠關閉／刪除";
        if(request.optBoolean("needsReview"))return "Google 來源已變更，請在原廠調整或刪除舊鬧鐘";
        if(!request.has("basis"))return "舊版送交紀錄，需重新核對";
        if(!current(request,source))return "來源／連動已變更，原廠需重新核對";
        if(confirmed(request,source))return "✓ 本人已核對此版本並儲存";
        return "manual-registration".equals(request.optString("origin"))?"待重新核對：本人登記的原廠內容":"待核對：已送交原廠，尚未確認儲存";
    }
    static JSONObject requested(JSONObject previous,JSONObject source,SimpleNote chosen,long now) throws JSONException {
        JSONObject result=previous==null?new JSONObject():new JSONObject(previous.toString());
        if(previous!=null){
            JSONArray history=previous.optJSONArray("priorRequests");history=history==null?new JSONArray():new JSONArray(history.toString());
            JSONObject prior=new JSONObject(previous.toString());prior.remove("priorRequests");history.put(prior);result.put("priorRequests",history);
        }
        result.put("key",key(source)).put("basis",basis(source)).put("sourceSnapshot",new JSONObject(source.toString())).put("sourceTitle",source.optString("title"))
            .put("sourceWhen",source.optLong("when")).put("source",source.has("noteId")?"記事":"Google 日曆／通知")
            .put("title",chosen.title).put("label",chosen.label).put("when",chosen.time).put("repeat","不重複")
            .put("requested",now).put("origin","handoff").put("status","pending");
        result.remove("confirmed");result.remove("removed");result.remove("needsReview");result.remove("registered");return result;
    }
    static JSONObject registered(JSONObject previous,JSONObject source,SimpleNote chosen,long now) throws JSONException {
        JSONObject result=requested(previous,source,chosen,now);
        result.remove("requested");return result.put("origin","manual-registration").put("registered",now).put("confirmed",now).put("status","manual-confirmed");
    }
    static JSONObject mark(JSONObject request,JSONObject source,String status,long now) throws JSONException {
        if(request==null)throw new IllegalStateException("尚無原廠送交紀錄。");
        if("manual-confirmed".equals(status)&&(!current(request,source)||"manual-removed".equals(request.optString("status"))))throw new IllegalStateException("來源已變更或原廠已移除，請重新送交核對。");
        if(!Arrays.asList("manual-confirmed","pending","manual-removed").contains(status))throw new IllegalArgumentException("未知的核對狀態。");
        JSONObject result=new JSONObject(request.toString());result.put("status",status);
        result.remove("confirmed");result.remove("removed");
        if("manual-confirmed".equals(status))result.put("confirmed",now);
        if("manual-removed".equals(status))result.put("removed",now);
        return result;
    }
    static boolean sameRequest(JSONObject current,JSONObject shown){
        return current!=null&&shown!=null&&current.optString("key").equals(shown.optString("key"))
            &&current.optLong("requested")==shown.optLong("requested")&&current.optLong("when")==shown.optLong("when")
            &&current.optLong("registered")==shown.optLong("registered")
            &&current.optString("label").equals(shown.optString("label"))&&current.optString("status").equals(shown.optString("status"))
            &&current.optBoolean("needsReview")==shown.optBoolean("needsReview");
    }
    static boolean changed(JSONObject snapshot,CalendarModel.Event event,Set<String> desired,boolean alertsComplete){
        if(event==null)return true;
        if(!CalendarModel.recordEventKey(snapshot).equals(event.key())||!snapshot.optString("eventTitle").equals(event.title)
            ||snapshot.optLong("eventEnd")!=event.end||!snapshot.optString("accountName").equals(event.calendar.account)
            ||!snapshot.optString("calendarName").equals(event.calendar.name)||snapshot.optBoolean("allDay")!=event.allDay)return true;
        return snapshot.optInt("snoozeCount")==0&&snapshot.optString("linkId").startsWith("calendar:")&&!desired.contains(snapshot.optString("linkId"))
            &&(alertsComplete||(!snapshot.optBoolean("providerTime")&&!snapshot.optBoolean("allDay")));
    }
    static JSONArray replace(JSONArray previous,JSONObject request) throws JSONException {
        JSONArray next=new JSONArray(previous.toString());int at=-1;
        for(int i=0;i<next.length();i++){JSONObject r=next.optJSONObject(i);if(r!=null&&request.getString("key").equals(r.optString("key"))){at=i;break;}}
        if(at<0)next.put(request);else next.put(at,request);return next;
    }
    static List<JSONObject> candidates(JSONArray records,long now){
        List<JSONObject> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=records.length()-1;i>=0;i--){JSONObject r=records.optJSONObject(i);
            if(r!=null&&!LinkModel.test(r)&&r.has("linkId")&&r.optBoolean("linkEnabled")&&r.optBoolean("scheduleAccepted")
                &&AlarmRecord.pending(r)&&r.optLong("when")>now&&seen.add(key(r)))out.add(r);}
        out.sort(Comparator.comparingLong(r->r.optLong("when")));return out;
    }
}
