package vn.edu.cnpm.tts.storage;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
@Component
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;
    public LocalObjectStorage(Environment env) {
        root=Path.of(env.getProperty("OBJECT_STORAGE_PATH","data/audio")).toAbsolutePath().normalize();
    }
    @Override public String put(byte[] content) throws IOException {
        Files.createDirectories(root);
        String key="audio/"+UUID.randomUUID()+".mp3";
        Path target=resolve(key);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(temp, content);
        try {
            Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return key;
    }
    @Override public byte[] get(String key) throws IOException { return Files.readAllBytes(resolve(key)); }
    @Override public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
    private Path resolve(String key) {
        Path target=root.resolve(key).normalize();
        if(!target.startsWith(root))throw new IllegalArgumentException("Invalid object key");
        return target;
    }
}
