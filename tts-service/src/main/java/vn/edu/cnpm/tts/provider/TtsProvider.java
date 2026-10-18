package vn.edu.cnpm.tts.provider;
public interface TtsProvider {
    byte[] synthesize(String text,String lang,String voiceId);
}
