package vn.edu.cnpm.narration.messaging;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.cnpm.narration.service.NarrationStore;
import java.time.Duration;
import java.util.Map;

@Component
public class TimeoutScanner {
    private final NarrationStore store;
    private final CallbackClient callback;
    private final Duration translationTimeout;
    private final Duration ttsTimeout;

    public TimeoutScanner(NarrationStore store, CallbackClient callback, Environment env) {
        this.store = store;
        this.callback = callback;
        this.translationTimeout = Duration.ofMinutes(Math.max(1, env.getProperty("NARRATION_TRANSLATION_TIMEOUT_MINUTES", Integer.class, 5)));
        this.ttsTimeout = Duration.ofMinutes(Math.max(1, env.getProperty("NARRATION_TTS_TIMEOUT_MINUTES", Integer.class, 5)));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        expireStalledTargets();
    }

    @Scheduled(fixedDelay = 30000)
    public void expireStalledTargets() {
        for (Map<String, Object> candidate : store.jobsForTimeout()) {
            String id = String.valueOf(candidate.get("jobId"));
            Map<String, Object> job = store.timeoutJob(id, translationTimeout, ttsTimeout);
            if (job != null) {
                callback.notify(id, store.correlationId(id), job);
            }
        }
    }
}
