package vn.edu.cnpm.runner;

import java.sql.*;

public class InvestigateJob {
    public static void main(String[] args) throws Exception {
        String nDb = "jdbc:h2:file:D:/CNPM/Software-technology-project/.local-data/h2/narrationdb;AUTO_SERVER=TRUE;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";
        String tDb = "jdbc:h2:file:D:/CNPM/Software-technology-project/.local-data/h2/translationdb;AUTO_SERVER=TRUE;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";
        String ttsDb = "jdbc:h2:file:D:/CNPM/Software-technology-project/.local-data/h2/ttsdb;AUTO_SERVER=TRUE;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";

        System.out.println("=== NARRATION TARGETS FOR b1418c63 ===");
        try (Connection conn = DriverManager.getConnection(nDb, "sa", "")) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM job_targets WHERE job_id = 'b1418c63-cd71-4551-8178-29b67940689d'");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                System.out.printf("Target: %s, lang: %s, status: %s, translation_id: %s, audio_id: %s, updated_at: %s%n",
                    rs.getString("target_id"), rs.getString("lang"), rs.getString("status"),
                    rs.getString("translation_id"), rs.getString("audio_id"), rs.getString("updated_at"));
            }
        }

        System.out.println("=== TRANSLATIONS FOR CONTENT 72c89f0e ===");
        try (Connection conn = DriverManager.getConnection(tDb, "sa", "")) {
            PreparedStatement ps = conn.prepareStatement("SELECT translation_id, lang, length(text_content) as len, text_content FROM translations WHERE content_id = '72c89f0e-bad7-4d47-a35c-38a10ef17297'");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String text = rs.getString("text_content");
                StringBuilder hex = new StringBuilder();
                if (text != null) {
                    for (int i = 0; i < Math.min(20, text.length()); i++) {
                        hex.append(String.format("\\u%04x", (int) text.charAt(i)));
                    }
                }
                System.out.printf("Translation: %s, lang: %s, len: %d, hex: %s%n",
                    rs.getString("translation_id"), rs.getString("lang"), rs.getInt("len"), hex.toString());
            }
        }

        System.out.println("=== AUDIOS IN TTSDB ===");
        try (Connection conn = DriverManager.getConnection(ttsDb, "sa", "")) {
            PreparedStatement ps = conn.prepareStatement("SELECT TOP 20 * FROM audios ORDER BY created_at DESC");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                System.out.printf("Audio: %s, trans_id: %s, voice: %s, key: %s, duration: %d, size: %d, created: %s%n",
                    rs.getString("audio_id"), rs.getString("translation_id"), rs.getString("voice_id"),
                    rs.getString("object_key"), rs.getInt("duration_seconds"), rs.getLong("size_bytes"), rs.getString("created_at"));
            }
        }
    }
}
