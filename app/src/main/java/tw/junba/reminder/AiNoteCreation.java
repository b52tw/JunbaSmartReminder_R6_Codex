package tw.junba.reminder;

import org.json.*;

/** Builds an idempotent note insertion before the storage transaction commits. */
final class AiNoteCreation {
    final JSONArray notes;
    final JSONObject note;
    final int id,nextId;
    final boolean created;
    private AiNoteCreation(JSONArray notes,JSONObject note,int id,int nextId,boolean created){
        this.notes=notes;this.note=note;this.id=id;this.nextId=nextId;this.created=created;
    }
    static AiNoteCreation prepare(JSONArray existing,int nextId,String token,AiReminderDraft draft,boolean remind) throws JSONException {
        if(token==null||token.isEmpty())throw new IllegalArgumentException("缺少草稿識別，請重新開啟草稿。");
        int id=Math.max(10000,nextId);
        for(int i=0;i<existing.length();i++){
            JSONObject note=existing.optJSONObject(i);if(note==null)continue;
            if(token.equals(note.optString("creationId")))return new AiNoteCreation(existing,note,note.optInt("id"),nextId,false);
            if(note.optInt("id")>=id){
                if(note.optInt("id")==Integer.MAX_VALUE)throw new IllegalArgumentException("記事編號已達上限。");
                id=note.optInt("id")+1;
            }
        }
        if(id==Integer.MAX_VALUE)throw new IllegalArgumentException("記事編號已達上限。");
        JSONObject note=new JSONObject().put("id",id).put("title",draft.title).put("content",draft.content)
            .put("time",draft.eventTime).put("ahead",draft.advanceMinutes).put("repeat",draft.repeat)
            .put("remind",remind).put("creationId",token);
        JSONArray notes=new JSONArray(existing.toString());notes.put(note);
        return new AiNoteCreation(notes,note,id,id+1,true);
    }
}
