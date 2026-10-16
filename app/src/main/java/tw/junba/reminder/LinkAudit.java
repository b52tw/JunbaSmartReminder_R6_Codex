package tw.junba.reminder;

import java.util.*;
import org.json.*;

/** Explains missing links without inventing reminder times or treating tests as coverage. */
final class LinkAudit {
    static final class Report {
        int expected,covered,manual;
        final Map<String,Integer> missing=new LinkedHashMap<>();
        void reason(String text){missing.put(text,missing.getOrDefault(text,0)+1);}
        String text(){
            StringBuilder out=new StringBuilder("可讀未來提醒 "+expected+" 筆｜Junba 已連動 "+covered+" 筆");
            if(manual>0)out.append("｜自行指定連動 ").append(manual).append(" 筆");
            for(Map.Entry<String,Integer> reason:missing.entrySet())out.append("\n").append(reason.getKey()).append("（").append(reason.getValue()).append("）");
            return out.toString();
        }
    }
    static Report inspect(CalendarModel.Event event,JSONArray records,long now,long until,boolean enabled,boolean prelink,boolean capable,boolean complete,boolean eventOff,Set<String> disabled,JSONObject outcomes){
        Report result=new Report();Map<String,JSONObject> existing=new HashMap<>();
        for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r==null||LinkModel.test(r)||!CalendarModel.belongs(r,event))continue;
            if(r.has("linkId"))existing.put(r.optString("linkId"),r);
            if("manual_time".equals(r.optString("matchKind"))&&r.optLong("when")>now&&LinkModel.linked(r,now,enabled&&capable))result.manual++;
        }
        List<LinkModel.Request> requests=LinkModel.plan(Collections.singletonList(event),now,Long.MAX_VALUE);result.expected=requests.size();
        for(LinkModel.Request request:requests){
            JSONObject record=existing.get(request.id);
            if(record!=null&&LinkModel.linked(record,now,enabled&&capable)){result.covered++;continue;}
            if(eventOff||disabled.contains(request.id)){result.reason("本人已移除，刷新不會重建");continue;}
            if(!enabled){result.reason("日曆連動開關尚未啟用");continue;}
            if(!capable){result.reason("Junba 通知或精確鬧鐘權限未完成");continue;}
            if(!prelink){result.reason("自動預排已關閉，可手動連動");continue;}
            if(request.when>until){result.reason("超出自動預排範圍，可手動連動");continue;}
            if(!complete){result.reason("來源提醒資料讀取不完整，請刷新或指定時間");continue;}
            String reason=outcomes.optString(request.id);
            if("limit".equals(reason))result.reason("本次超過 250 筆自動預排上限，可手動連動");
            else if(record!=null&&!record.optString("error").isEmpty())result.reason("排程未成功："+record.optString("error"));
            else result.reason("尚未成功預排，請刷新或手動連動");
        }
        if(requests.isEmpty()){
            if(event.allDay&&event.alertTimes.isEmpty())result.reason("全天活動未提供確切提醒時間，可手動指定");
            else if(event.minutes.isEmpty()&&event.alertTimes.isEmpty())result.reason("手機未提供提醒設定，可手動指定");
            else if(event.minutes.contains(-1)&&event.alertTimes.isEmpty()&&event.minutes.stream().noneMatch(m->m>=0))result.reason("來源只提供預設提醒，未提供實際時間，可手動指定");
            else result.reason("可讀提醒時間已過，不自動補響");
        }
        return result;
    }
}
