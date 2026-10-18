package vn.edu.cnpm.tts.storage;
import java.io.IOException;
public interface ObjectStorage {
    String put(byte[] content) throws IOException;
    byte[] get(String objectKey) throws IOException;
    void delete(String objectKey) throws IOException;
}
