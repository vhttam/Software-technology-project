package vn.edu.cnpm.narration.messaging;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;
@Component
public class CallbackClient {
    private final RestClient gateway;private final String token;
    public CallbackClient(Environment env){
        gateway=RestClient.builder().baseUrl(env.getProperty("API_GATEWAY_URL","http://localhost:8080")).build();
        token=env.getProperty("NARRATION_SERVICE_TOKEN","");
    }
    public void notify(String jobId,String correlationId,Map<String,Object> job){
        try{
            Map<String,Object> data=Map.of("jobId",job.get("jobId"),"status",job.get("status"),
                    "updatedAt",job.get("updatedAt"),"targets",job.get("targets"));
            gateway.post().uri("/internal/callbacks/job-progress")
                    .header("X-Service-Token",token).header("X-Correlation-ID",correlationId==null?"":correlationId)
                    .body(Map.of("event","job.progress","correlationId",correlationId==null?"":correlationId,"data",data))
                    .retrieve().toBodilessEntity();
        }catch(Exception ignored){/* Callback is best-effort; GET /jobs/{jobId} remains authoritative. */}
    }
}
