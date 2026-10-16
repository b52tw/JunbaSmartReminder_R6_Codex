package tw.junba.reminder;

import java.util.*;
import org.json.*;

/** Linked means a real Junba schedule was accepted; receipt and tests are independent. */
final class LinkModel {
    static boolean test(JSONObject r){String source=r.optString("source");return source.equals("test")||source.endsWith("_test");}
    static boolean calendar(JSONObject r){return "calendar".equals(r.optString("source"))||"calendar_link".equals(r.optString("source"));}
    static boolean managed(JSONObject r){return "calendar_link".equals(r.optString("source"))&&r.optBoolean("managed");}
    static boolean ringing(JSONObject r,long now){return "ringing".equals(r.optString("status"))&&r.optLong("activeUntil")>now;}
    static boolean linked(JSONObject r,long now,boolean permissions){
        if(test(r)||!r.optBoolean("linkEnabled")||!r.optBoolean("scheduleAccepted"))return false;
        if(ringing(r,now))return true;
        if(AlarmRecord.pending(r))return permissions&&r.optBoolean("systemScheduled")&&r.optLong("when")>now-2000L;
        return Arrays.asList("stopped","timed_out","superseded").contains(r.optString("status"));
    }
    static int eventLamp(JSONArray records,CalendarModel.Event event,long now,boolean permissions){
        int result=0;
        for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r==null||test(r)||!CalendarModel.belongs(r,event))continue;
            if(ringing(r,now))return 2;if(linked(r,now,permissions))result=1;}
        return result;
    }
    static int noteLamp(JSONArray records,int noteId,long now,boolean permissions){
        int result=0;for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);
            if(r==null||test(r)||r.optInt("noteId",-1)!=noteId)continue;
            if(ringing(r,now))return 2;if(linked(r,now,permissions))result=1;}
        return result;
    }
    static final class Request {
        final String id;final long when;final CalendarModel.Event event;
        Request(CalendarModel.Event event,long when){this.event=event;this.when=when;id="calendar:"+event.key()+":"+when;}
        JSONObject metadata() throws JSONException {return event.metadata("prelinked").put("linkId",id).put("managed",true).put("providerTime",event.alertTimes.contains(when));}
    }
    static List<Request> plan(List<CalendarModel.Event> events,long now,long until){
        Map<String,Request> out=new LinkedHashMap<>();
        for(CalendarModel.Event event:events){Set<Long> times=new TreeSet<>();
            times.addAll(event.alertTimes);
            if(!event.allDay)for(int minutes:event.minutes)if(minutes>=0)times.add(event.begin-minutes*60000L);
            for(long when:times)if(when>now&&when<=until){Request r=new Request(event,when);out.put(r.id,r);}
        }
        List<Request> result=new ArrayList<>(out.values());result.sort(Comparator.comparingLong(r->r.when));return result;
    }
    static Set<String> schedulable(List<Request> requests,Set<String> disabled,int limit){
        Set<String> result=new LinkedHashSet<>();
        for(Request request:requests)if(!disabled.contains(request.id)&&result.size()<limit)result.add(request.id);
        return result;
    }
    static boolean retire(JSONObject record,Set<Long> selected,Set<String> desired,Set<String> liveEvents,long now,long until,boolean alertsComplete){
        if(!managed(record)||!AlarmRecord.pending(record)||record.optInt("snoozeCount")>0)return false;
        if(!selected.contains(record.optLong("calendarId")))return true;
        long original=record.optLong("originalWhen",record.optLong("when"));
        if(original<=now||original>until||desired.contains(record.optString("linkId")))return false;
        if(!liveEvents.contains(CalendarModel.recordEventKey(record)))return true;
        return alertsComplete||(!record.optBoolean("providerTime")&&!record.optBoolean("allDay"));
    }
    static boolean missed(JSONObject record,long now){return AlarmRecord.pending(record)&&record.optLong("when")<now-120000L;}
    static boolean sameVersion(JSONObject r,long when,String title){return r.optLong("when")==when&&r.optString("title").equals(title);}
    static int notificationRoute(JSONArray records,String title,long postTime,Set<String> disabled){
        boolean covered=false;
        for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r==null||!coversNotification(r,title,postTime))continue;
            if(disabled.contains(r.optString("linkId")))return 2;
            if(r.optBoolean("linkEnabled")&&r.optBoolean("scheduleAccepted")&&(AlarmRecord.pending(r)||r.has("firedAt")))covered=true;
        }
        return covered?1:0;
    }
    static boolean coversNotification(JSONObject r,String title,long postTime){
        if(!"calendar_link".equals(r.optString("source")))return false;
        return CalendarModel.normalized(r.optString("eventTitle")).equals(CalendarModel.normalized(title))
            &&!CalendarModel.normalized(title).isEmpty()&&Math.abs(r.optLong("originalWhen",r.optLong("when"))-postTime)<=120000L;
    }
}
