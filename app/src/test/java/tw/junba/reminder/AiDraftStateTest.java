package tw.junba.reminder;

import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiDraftStateTest {
    private static class Memory implements AiDraftState.Storage {
        final Map<String,String> data=new HashMap<>();
        public String read(String key){return data.getOrDefault(key,"");}
        public void write(Map<String,String> changes){
            for(Map.Entry<String,String> change:changes.entrySet()){
                if(change.getValue()==null)data.remove(change.getKey());else data.put(change.getKey(),change.getValue());
            }
        }
    }
    @Test public void timeoutKeepsRawAndIdentityForRetry() throws Exception {
        Memory disk=new Memory();AiDraftState state=new AiDraftState(disk);state.replaceRaw("明天兩點開會");String token=state.token();
        try{
            JSONObject parsed=GeminiNoteParser.parse((m,p,b)->{throw new SocketTimeoutException();},"gemini-selected",state.raw(),ZonedDateTime.now());
            state.rememberParsed(state.raw(),parsed.toString());fail();
        }catch(SocketTimeoutException expected){}
        AiDraftState reopened=new AiDraftState(disk);assertEquals("明天兩點開會",reopened.raw());assertEquals(token,reopened.token());assertTrue(reopened.parsed().isEmpty());
    }
    @Test public void parsedPreviewSurvivesReopenWithoutAnotherApiCall(){
        Memory disk=new Memory();AiDraftState first=new AiDraftState(disk);first.replaceRaw("原文");first.rememberParsed("原文","{\"title\":\"記事\"}");
        AiDraftState reopened=new AiDraftState(disk);assertEquals(first.parsed(),reopened.parsed());assertEquals(first.token(),reopened.token());
    }
    @Test public void editingRawInvalidatesOldPreviewAndCreationIdentity(){
        AiDraftState state=new AiDraftState(new Memory());state.replaceRaw("舊原文");state.rememberParsed("舊原文","{}");String token=state.token();
        state.replaceRaw("修正後原文");assertNotEquals(token,state.token());assertTrue(state.parsed().isEmpty());
    }
    @Test public void lateReplyCannotOverwriteANewerDraft(){
        AiDraftState state=new AiDraftState(new Memory());state.replaceRaw("舊原文");state.replaceRaw("新原文");
        assertFalse(state.rememberParsed("舊原文","old-json"));assertTrue(state.parsed().isEmpty());assertEquals("新原文",state.raw());
    }
    @Test public void retrySameRawKeepsIdempotencyToken(){
        AiDraftState state=new AiDraftState(new Memory());state.replaceRaw("原文");String token=state.token();state.replaceRaw("原文");assertEquals(token,state.token());
    }
    @Test public void committingAnOldNoteCannotEraseANewerDraft(){
        AiDraftState state=new AiDraftState(new Memory());state.replaceRaw("舊原文");String old=state.token();state.replaceRaw("新原文");
        assertFalse(state.clearAfterSave(old));assertEquals("新原文",state.raw());
    }
    @Test public void successfulCommitClearsOnlyTheMatchingDraft(){
        Memory disk=new Memory();disk.data.put("unrelated-setting","preserve");AiDraftState state=new AiDraftState(disk);
        state.replaceRaw("原文");state.rememberParsed("原文","{}");assertTrue(state.clearAfterSave(state.token()));
        assertTrue(state.raw().isEmpty());assertTrue(state.parsed().isEmpty());assertTrue(state.token().isEmpty());assertEquals("preserve",disk.read("unrelated-setting"));
    }
}
