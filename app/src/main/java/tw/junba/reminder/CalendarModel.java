package tw.junba.reminder;

import java.time.*;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Dates, identities and conservative notification matching; no Android dependency. */
final class CalendarModel {
    static final class Source {
        final long id;final String account,name,type;
        Source(long id,String account,String name,String type){this.id=id;this.account=safe(account);this.name=safe(name);this.type=safe(type);}
        String label(){return account+" · "+name;}
    }
    static final class Event {
        final long id,begin,end;final Source calendar;final String title;final boolean allDay;
        final List<Integer> minutes=new ArrayList<>();
        final List<Long> alertTimes=new ArrayList<>();
        Event(long id,long begin,long end,Source calendar,String title,boolean allDay){
            this.id=id;this.begin=begin;this.end=end;this.calendar=calendar;this.title=safe(title);this.allDay=allDay;
        }
        String key(){return calendar.id+":"+id+":"+begin;}
        JSONObject metadata(String kind) throws JSONException {
            return new JSONObject().put("eventId",id).put("eventBegin",begin).put("eventEnd",end)
                .put("calendarId",calendar.id).put("accountName",calendar.account).put("accountType",calendar.type)
                .put("calendarName",calendar.name).put("eventTitle",title).put("allDay",allDay).put("matchKind",kind);
        }
    }
    static boolean selected(Source source,Boolean current,Boolean legacy){return current!=null?current:legacy!=null?legacy:"com.google".equals(source.type);}
    static boolean hasTitle(List<Event> candidates,String title){
        if(normalized(title).isEmpty())return false;
        for(Event event:candidates)if(normalized(event.title).equals(normalized(title)))return true;
        return false;
    }
    static Event match(List<Event> alerts,List<Event> instances,String title,long received){
        return hasTitle(alerts,title)?uniqueTitle(alerts,title):reminderMatch(instances,title,received);
    }
    static String safe(String text){return text==null||text.trim().isEmpty()?"無訊息":text.trim();}
    static String normalized(String text){return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFC).trim().replaceAll("\\s+"," ");}
    static String recordEventKey(JSONObject record){
        return record.has("eventId")&&record.has("eventBegin")&&record.has("calendarId")?record.optLong("calendarId")+":"+record.optLong("eventId")+":"+record.optLong("eventBegin"):"";
    }
    static Map<LocalDate,List<Event>> indexDays(List<Event> events,YearMonth month,ZoneId zone){
        Map<LocalDate,List<Event>> days=new HashMap<>();
        for(Event event:events){
            ZoneId dates=event.allDay?ZoneOffset.UTC:zone;
            LocalDate first=Instant.ofEpochMilli(event.begin).atZone(dates).toLocalDate();
            LocalDate last=Instant.ofEpochMilli(Math.max(event.begin,event.end-1)).atZone(dates).toLocalDate();
            if(first.isBefore(month.atDay(1)))first=month.atDay(1);
            if(last.isAfter(month.atEndOfMonth()))last=month.atEndOfMonth();
            for(LocalDate day=first;!day.isAfter(last);day=day.plusDays(1))if(onDay(event,day,zone))days.computeIfAbsent(day,k->new ArrayList<>()).add(event);
        }
        return days;
    }
    static String testStatus(JSONObject record){
        switch(record.optString("status")){
            case "scheduled":return "已排定測試";case "ringing":return "測試響鈴中";case "snoozed":return "測試已延後";
            case "timed_out":case "stopped":return "測試已結束";case "cancelled":return "測試已取消";
            case "failed":case "blocked":return "測試未成功，請查看紀錄與權限";default:return "測試未確認";
        }
    }
    static boolean onDay(Event event,LocalDate day,ZoneId zone){
        if(event.allDay){
            LocalDate first=Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate end=Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate();
            if(!end.isAfter(first))end=first.plusDays(1);
            return !day.isBefore(first)&&day.isBefore(end);
        }
        long start=day.atStartOfDay(zone).toInstant().toEpochMilli(),end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return event.end>event.begin?event.begin<end&&event.end>start:event.begin>=start&&event.begin<end;
    }
    static String notificationTitle(JSONObject record){
        if(record.has("notificationTitle"))return record.optString("notificationTitle");
        String old=record.optString("title");String prefix="Google 日曆提醒：";
        return old.startsWith(prefix)?old.substring(prefix.length()).split("\n",2)[0]:"";
    }
    static Event uniqueTitle(List<Event> candidates,String title){
        if(normalized(title).isEmpty())return null;Map<String,Event> matches=new LinkedHashMap<>();
        for(Event event:candidates)if(normalized(event.title).equals(normalized(title)))matches.put(event.key(),event);
        return matches.size()==1?matches.values().iterator().next():null;
    }
    static Event reminderMatch(List<Event> events,String title,long received){
        List<Event> candidates=new ArrayList<>();
        for(Event event:events){
            if(event.allDay)continue; // Calendar apps choose their own all-day reminder hour.
            for(int minutes:event.minutes)if(minutes>=0&&Math.abs(event.begin-minutes*60000L-received)<=120000L){candidates.add(event);break;}
        }
        return uniqueTitle(candidates,title);
    }
    static boolean belongs(JSONObject record,Event event){
        return record.has("eventId")&&record.has("eventBegin")&&record.optLong("eventId")==event.id&&record.optLong("eventBegin")==event.begin&&record.optLong("calendarId")==event.calendar.id;
    }
    /** Receipt inventory only; R10 connection lamps use LinkModel. */
    static boolean received(JSONArray records,Event event){
        for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);
            if(r!=null&&"calendar".equals(r.optString("source"))&&belongs(r,event))return true;}
        return false;
    }
    static long stockTime(Event event,long now,ZoneId zone){
        if(event.allDay){LocalDate day=Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate();return day.atTime(9,0).atZone(zone).toInstant().toEpochMilli();}
        long earliest=Long.MAX_VALUE;
        for(int minute:event.minutes)if(minute>=0){long time=event.begin-minute*60000L;if(time>now)earliest=Math.min(earliest,time);}
        return earliest==Long.MAX_VALUE?event.begin:earliest;
    }
}
