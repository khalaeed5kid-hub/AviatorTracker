package com.aviatortracker.app;

import java.util.regex.*;
public class RoundStateMachine {
    public enum State { WAITING, FLIGHT, COUNTDOWN_CANDIDATE }
    private State state=State.WAITING; private String last; private int countdownFrames;
    public interface Listener { void diagnostic(String state,String ocr,String last); void result(String value); }
    private final Listener listener; public RoundStateMachine(Listener l){listener=l;}
    public void accept(String raw){String text=raw==null?"":raw.replace(',', '.').trim(); Matcher m=Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*[x×]").matcher(text);
        if(m.find()){last=m.group(1)+"x"; state=State.FLIGHT; countdownFrames=0; listener.diagnostic(state.name(),text,last); return;}
        Matcher cd=Pattern.compile("^\\s*[0-7](?:\\.0+)?\\s*$").matcher(text);
        if(cd.matches() && last!=null){state=State.COUNTDOWN_CANDIDATE; countdownFrames++; listener.diagnostic(state.name(),text,last); if(countdownFrames>=2){listener.result(last); last=null; state=State.WAITING; countdownFrames=0;} return;}
        if(state==State.FLIGHT) listener.diagnostic("FLIGHT_WAITING_FOR_COUNTDOWN",text,last); else listener.diagnostic(state.name(),text,last);
    }
    public State state(){return state;}
}
