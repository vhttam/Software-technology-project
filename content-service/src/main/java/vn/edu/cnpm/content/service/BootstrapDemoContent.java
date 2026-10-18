package vn.edu.cnpm.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;

/**
 * Tự động nạp dữ liệu mẫu điểm tham quan và thuyết minh khi hệ thống chạy ở chế độ DEMO.
 * Đảm bảo tính idempotent: chỉ tạo nếu chưa tồn tại mã QR mẫu 'POI-DEMO-001'.
 * Hoàn toàn không chứa dữ liệu thực hay thông tin bí mật.
 */
@Configuration
public class BootstrapDemoContent {
    private static final Logger log = LoggerFactory.getLogger(BootstrapDemoContent.class);

    @Bean
    ApplicationRunner seedDemoContent(JdbcTemplate jdbc, Environment environment) {
        return args -> {
            boolean demoMode = "true".equalsIgnoreCase(environment.getProperty("DEMO_MODE", "false"));
            if (!demoMode) {
                return;
            }

            var existing = jdbc.queryForList("SELECT poi_id FROM points_of_interest WHERE qr_code=?", "POI-DEMO-001");
            if (existing.isEmpty()) {
                String now = Instant.now().toString();
                String poi1Id = UUID.randomUUID().toString();
                String poi2Id = UUID.randomUUID().toString();
                String contentId = UUID.randomUUID().toString();

                jdbc.update("INSERT INTO points_of_interest(poi_id, name, description, qr_code, created_at, updated_at, is_deleted) VALUES(?,?,?,?,?,?,0)",
                        poi1Id,
                        "Bảo tàng Lịch sử (Mẫu Demo)",
                        "Điểm tham quan mẫu phục vụ kiểm thử và trình diễn hệ thống thuyết minh tự động đa ngôn ngữ.",
                        "POI-DEMO-001",
                        now,
                        now);

                jdbc.update("INSERT INTO points_of_interest(poi_id, name, description, qr_code, created_at, updated_at, is_deleted) VALUES(?,?,?,?,?,?,0)",
                        poi2Id,
                        "Khu Di tích Hoàng thành (Mẫu Demo)",
                        "Khu di tích lịch sử mẫu dùng để minh họa tạo mã QR và quản lý nội dung đa ngôn ngữ.",
                        "POI-DEMO-002",
                        now,
                        now);

                jdbc.update("INSERT INTO contents(content_id, poi_id, current_version, is_deleted, created_by, created_at, updated_at) VALUES(?,?,1,0,?,?,?)",
                        contentId,
                        poi1Id,
                        "system-demo-admin",
                        now,
                        now);

                jdbc.update("INSERT INTO content_versions(content_id, version, title, source_lang, text_content, created_at) VALUES(?,1,?,?,?,?)",
                        contentId,
                        "Giới thiệu Bảo tàng Lịch sử",
                        "vi",
                        "Chào mừng quý khách đến với Bảo tàng Lịch sử. Nơi đây lưu giữ và trưng bày hàng ngàn hiện vật quý giá tái hiện các giai đoạn lịch sử hào hùng của dân tộc. Hệ thống thuyết minh tự động đa ngôn ngữ sẽ đồng hành cùng quý khách trong suốt hành trình tham quan.",
                        now);

                log.info("Initialized demo POIs and sample narration content successfully.");
            }
        };
    }
}
