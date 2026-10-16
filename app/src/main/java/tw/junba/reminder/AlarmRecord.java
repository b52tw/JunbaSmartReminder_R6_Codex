package tw.junba.reminder;

import org.json.*;

/** State transitions shared by the scheduler, audio service and visible reminder history. */
final class AlarmRecord {
    static JSONObject create(String key,String title,String source,String sourceKey,long now,long when) throws JSONException {
        return new JSONObject().put("key",key).put("title",title==null||title.trim().isEmpty()?"無訊息":title)
            .put("source",source).put("sourceKey",sourceKey).put("receivedAt",now).put("when",when)
            .put("status","calendar_setup".equals(source)?"reference":"scheduled").put("generation",0).put("snoozeCount",0);
    }
    static boolean pending(JSONObject record){return "scheduled".equals(record.optString("status"))||"snoozed".equals(record.optString("status"));}
    static JSONObject snoozed(JSONObject old,int minutes,long now) throws JSONException {
        if(minutes!=10&&minutes!=30&&minutes!=60)throw new IllegalArgumentException("請選擇 10 分鐘、30 分鐘或 1 小時。");
        JSONObject next=new JSONObject(old.toString());
        int generation=old.optInt("generation");
        if(generation==Integer.MAX_VALUE)throw new IllegalArgumentException("請重新建立提醒。");
        return next.put("status","snoozed").put("when",Math.addExact(now,minutes*60000L))
            .put("generation",generation+1).put("snoozeCount",old.optInt("snoozeCount")+1)
            .put("changedAt",now).put("error","");
    }
    static JSONObject ringing(JSONObject old,int generation,long now,long duration) throws JSONException {
        if(!pending(old)||old.optInt("generation")!=generation||old.optLong("when")>now+2000)return null;
        return new JSONObject(old.toString()).put("status","ringing").put("firedAt",now)
            .put("activeUntil",Math.addExact(now,duration)).put("error","");
    }
    static JSONObject finished(JSONObject old,String status,long now) throws JSONException {
        if(!"ringing".equals(old.optString("status")))return old;
        return new JSONObject(old.toString()).put("status",status).put("changedAt",now);
    }
}
