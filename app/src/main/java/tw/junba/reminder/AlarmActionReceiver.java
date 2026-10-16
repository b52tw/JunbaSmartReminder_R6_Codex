package tw.junba.reminder;

import android.content.*;
import android.widget.Toast;

/** Notification actions do not start a new audio service just to stop an old one. */
public final class AlarmActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null)return;
        try{
            if(AlarmAudioService.STOP.equals(i.getAction())){AlarmStore.stopNow(c);return;}
            if(AlarmAudioService.SNOOZE.equals(i.getAction())){
                String key=i.getStringExtra("alarm_key");org.json.JSONObject record=AlarmStore.find(c,key);
                if(record==null||!"ringing".equals(record.optString("status")))return;
                AlarmStore.snooze(c,key,i.getIntExtra("minutes",10));
                Toast.makeText(c,"已延後提醒，可在 Junba 查看或取消",Toast.LENGTH_LONG).show();
            }
        }catch(Exception e){Toast.makeText(c,"未成功延後，請開啟 Junba 檢查權限；仍可按停止響鈴",Toast.LENGTH_LONG).show();}
    }
}
