package vn.edu.cnpm.tts.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTtsProviderTest {

    @Test
    void cjkSentenceAndClauseSplittingRespectsDelimitersAndLength() {
        // Japanese text with sentence delimiters (。) and clause delimiters (、)
        String cjkText = "これはテスト文章です。博物館には多くの展示品があります、そして歴史的な価値があります。どうぞお楽しみください！";

        List<String> chunks = GoogleTtsProvider.splitTextForTts(cjkText, "ja", 25);
        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(25);
        }
        assertThat(String.join("", chunks).replace(" ", ""))
                .isEqualTo(cjkText.replace(" ", ""));
    }

    @Test
    void latinSentenceSplittingRespectsMaxChars() {
        String latinText = "Welcome to the national museum. This is the first gallery, featuring ancient artifacts. Please follow the tour guide!";
        List<String> chunks = GoogleTtsProvider.splitTextForTts(latinText, "en", 40);
        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(40);
        }
    }
}
