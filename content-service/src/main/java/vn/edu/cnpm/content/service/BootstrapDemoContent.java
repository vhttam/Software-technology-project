package vn.edu.cnpm.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

/**
 * Khởi tạo dữ liệu điểm tham quan và nội dung thuyết minh mặc định.
 * Lưu giữ 3 địa điểm do người dùng cấu hình:
 * 1. Lăng Bác
 * 2. Bến Bạch Đằng & Công viên bờ sông
 * 3. Phố đi bộ Nguyễn Huệ
 */
@Configuration
public class BootstrapDemoContent {
    private static final Logger log = LoggerFactory.getLogger(BootstrapDemoContent.class);

    @Bean
    ApplicationRunner seedDemoContent(JdbcTemplate jdbc, Environment environment) {
        return args -> {
            Integer poiCount = jdbc.queryForObject("SELECT COUNT(*) FROM points_of_interest WHERE is_deleted = 0", Integer.class);
            if (poiCount != null && poiCount > 0) {
                log.info("Points of interest already present (count={}), skipping seed.", poiCount);
                return;
            }

            String now = Instant.now().toString();
            String adminId = "096bf79a-7b13-4272-83ae-5e3efc3db9bb";

            // 1. Lăng Bác
            String poi1 = "41570eeb-8718-4e1c-9bcd-6e3bfb1b0a62";
            String qr1 = "qZJFJSop-GOByQL-fwbRaW_8";
            String c1 = "07b7ba8e-bf25-4fdf-a3be-7d2c76cefa56";
            jdbc.update("INSERT INTO points_of_interest(poi_id, name, description, qr_code, created_at, updated_at, is_deleted) VALUES(?, ?, ?, ?, ?, ?, 0)",
                    poi1, "Lăng Bác", "Địa chỉ", qr1, now, now);
            jdbc.update("INSERT INTO contents(content_id, poi_id, current_version, is_deleted, created_by, created_at, updated_at) VALUES(?, ?, 1, 0, ?, ?, ?)",
                    c1, poi1, adminId, now, now);
            jdbc.update("INSERT INTO content_versions(content_id, version, title, source_lang, text_content, created_at) VALUES(?, 1, ?, 'vi', ?, ?)",
                    c1, "Đặc điểm", "Công trình Lăng Bác được khởi công xây dựng vào ngày 2/9/1973 và chính thức khánh thành vào ngày 29/8/1975, với sự hỗ trợ chí tình của các chuyên gia Liên Xô cùng sức người, sức của từ mọi miền Tổ quốc.", now);

            // 2. Bến Bạch Đằng & Công viên bờ sông
            String poi2 = "b39ea376-9d93-47c5-b7b5-3e5e94222d90";
            String qr2 = "-yej5vPt97i2Cwuyj8yGhhyo";
            String c2 = "f8d5fa1f-1f16-453d-abc4-245eccdf0b3c";
            jdbc.update("INSERT INTO points_of_interest(poi_id, name, description, qr_code, created_at, updated_at, is_deleted) VALUES(?, ?, ?, ?, ?, ?, 0)",
                    poi2, "Bến Bạch Đằng & Công viên bờ sông", "Miêu tả", qr2, now, now);
            jdbc.update("INSERT INTO contents(content_id, poi_id, current_version, is_deleted, created_by, created_at, updated_at) VALUES(?, ?, 1, 0, ?, ?, ?)",
                    c2, poi2, adminId, now, now);
            jdbc.update("INSERT INTO content_versions(content_id, version, title, source_lang, text_content, created_at) VALUES(?, 1, ?, 'vi', ?, ?)",
                    c2, "Miêu tả", "Tọa lạc ven sông Sài Gòn với không gian mở thoáng đãng, là điểm dạo mát ngắm tàu thuyền, ngắm cảnh bờ Thủ Thiêm lung linh ánh đèn và trải nghiệm tuyến xe buýt đường sông (Saigon Waterbus).", now);

            // 3. Phố đi bộ Nguyễn Huệ
            String poi3 = "5b0d0ff4-48b8-4065-ba47-538b591880a2";
            String qr3 = "R1YE9EDNanO4_ACqLdmJz9f_";
            String c3 = "a566a538-f623-4559-99b0-d1b6a1f7f51c";
            jdbc.update("INSERT INTO points_of_interest(poi_id, name, description, qr_code, created_at, updated_at, is_deleted) VALUES(?, ?, ?, ?, ?, ?, 0)",
                    poi3, "Phố đi bộ Nguyễn Huệ", "Miêu tả", qr3, now, now);
            jdbc.update("INSERT INTO contents(content_id, poi_id, current_version, is_deleted, created_by, created_at, updated_at) VALUES(?, ?, 1, 0, ?, ?, ?)",
                    c3, poi3, adminId, now, now);
            jdbc.update("INSERT INTO content_versions(content_id, version, title, source_lang, text_content, created_at) VALUES(?, 1, ?, 'vi', ?, ?)",
                    c3, "Miêu tả", "Trục đường đi bộ hiện đại, sầm uất trải dài từ Trụ sở UBND Thành phố đến bờ sông Sài Gòn; không gian tập trung nhiều hoạt động vui chơi, quán cà phê view đẹp và biểu diễn nghệ thuật đường phố về đêm.", now);

            log.info("Bootstrap content: Seeded 3 points of interest and initial contents.");
        };
    }
}
