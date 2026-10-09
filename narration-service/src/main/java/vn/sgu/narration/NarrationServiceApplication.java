package vn.sgu.narration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NarrationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NarrationServiceApplication.class, args);
    }
}
