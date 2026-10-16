package tw.junba.reminder;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;

/** Full date kept by Junba; stock Clock receives time-of-day and a date label only. */
final class SimpleNote {
    final String content,title,label;
    final long time;
    final int hour,minute;
    private SimpleNote(String name,String content,long time,ZoneId zone){
        this.content=content.trim().isEmpty()?"無訊息":content.trim();
        this.title=name.trim().isEmpty()?"無訊息":name.trim();
        this.time=time;ZonedDateTime date=Instant.ofEpochMilli(time).atZone(zone);
        hour=date.getHour();minute=date.getMinute();
        label=this.title+"｜"+date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))+("無訊息".equals(this.content)?"":"\n"+this.content);
    }
    static SimpleNote create(String name,String content,int year,int month,int day,int hour,int minute,long now,ZoneId zone){
        LocalDateTime date=LocalDateTime.of(year,month,day,hour,minute);
        if(zone.getRules().getValidOffsets(date).size()!=1)throw new IllegalArgumentException("此時間遇到時區調整，請另選時間。");
        long time=date.atZone(zone).toInstant().toEpochMilli();
        if(time<=now)throw new IllegalArgumentException("請選擇之後的日期與時間。");
        return new SimpleNote(name==null?"":name,content==null?"":content,time,zone);
    }
    static SimpleNote fromStored(JSONObject note,long now,ZoneId zone){
        long time=note.optLong("time")-note.optInt("ahead")*60000L;
        ZonedDateTime d=Instant.ofEpochMilli(time).atZone(zone);
        String body=note.optString("content","");
        if(!note.has("content"))body=note.optString("title","無訊息");
        return create(note.optString("title","無訊息"),body,d.getYear(),d.getMonthValue(),d.getDayOfMonth(),d.getHour(),d.getMinute(),now,zone);
    }
    static final class Change {
        final JSONArray notes;final JSONObject note;final int id,nextId;final boolean created;
        Change(JSONArray notes,JSONObject note,int id,int nextId,boolean created){this.notes=notes;this.note=note;this.id=id;this.nextId=nextId;this.created=created;}
    }
    static Change prepare(JSONArray old,int nextId,Integer editingId,String token,SimpleNote draft) throws JSONException {
        if(token==null||token.isEmpty())throw new IllegalArgumentException("草稿尚未準備完成，請重新開啟。");
        JSONArray notes=new JSONArray(old.toString());long id=Math.max(10000L,nextId);int index=-1;
        for(int i=0;i<notes.length();i++){
            JSONObject o=notes.optJSONObject(i);if(o==null)continue;
            if(token.equals(o.optString("creationId")))return new Change(old,o,o.optInt("id"),nextId,false);
            if(editingId!=null&&o.optInt("id")==editingId)index=i;
            if(o.optInt("id")>=id)id=(long)o.optInt("id")+1;
        }
        if(editingId!=null&&index<0)throw new IllegalArgumentException("找不到原記事，草稿仍保留。");
        if(index<0&&id>=Integer.MAX_VALUE)throw new IllegalArgumentException("記事編號已達上限。");
        JSONObject note=index<0?new JSONObject():notes.getJSONObject(index);
        int assigned=index<0?(int)id:editingId;
        note.put("id",assigned).put("title",draft.title).put("content",draft.content).put("time",draft.time)
            .put("ahead",0).put("repeat","不重複").put("remind",false).put("creationId",token);
        if(index<0)notes.put(note);
        return new Change(notes,note,assigned,(int)Math.min(Integer.MAX_VALUE,index<0?id+1:id),true);
    }
}
