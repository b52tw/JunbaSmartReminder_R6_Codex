package tw.junba.reminder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent draft transitions; failures have no clear operation. */
final class AiDraftState {
    interface Storage { String read(String key); void write(Map<String,String> changes); }
    private final Storage storage;
    AiDraftState(Storage storage){this.storage=storage;}
    String raw(){return storage.read("voice_draft");}
    String token(){return storage.read("voice_creation_id");}
    String parsed(){return storage.read("ai_parsed_draft");}
    void replaceRaw(String text){
        if(!text.equals(raw())||token().isEmpty()){
            Map<String,String> changes=new HashMap<>();changes.put("voice_draft",text);
            changes.put("voice_creation_id",UUID.randomUUID().toString());changes.put("ai_parsed_draft",null);
            storage.write(changes);
        }
    }
    boolean rememberParsed(String transcript,String json){
        if(!transcript.equals(raw()))return false;
        Map<String,String> changes=new HashMap<>();changes.put("ai_parsed_draft",json);storage.write(changes);return true;
    }
    boolean clearAfterSave(String committedToken){
        if(committedToken.isEmpty()||!committedToken.equals(token()))return false;
        Map<String,String> changes=new HashMap<>();changes.put("voice_draft",null);
        changes.put("voice_creation_id",null);changes.put("ai_parsed_draft",null);storage.write(changes);return true;
    }
}
