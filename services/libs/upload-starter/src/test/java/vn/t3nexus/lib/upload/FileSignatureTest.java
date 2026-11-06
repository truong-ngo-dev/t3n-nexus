package vn.t3nexus.lib.upload;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileSignatureTest {

    @Test
    void detect_jpegDung() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11};
        assertThat(FileSignature.detect(jpeg)).contains("image/jpeg");
    }

    @Test
    void detect_pngDung() {
        byte[] png = new byte[]{
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
                0x00, 0x00
        };
        assertThat(FileSignature.detect(png)).contains("image/png");
    }

    @Test
    void detect_webpDung() {
        byte[] webp = new byte[]{
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P',
                0x12, 0x34
        };
        assertThat(FileSignature.detect(webp)).contains("image/webp");
    }

    @Test
    void detect_webpThieuWEBP_traVeRong() {
        // RIFF + 4 byte + "WAVE"
        byte[] wave = new byte[]{
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'A', 'V', 'E'
        };
        assertThat(FileSignature.detect(wave)).isEmpty();
    }

    @Test
    void detect_mangNganHonMucCan_traVeRong() {
        // 2 byte
        byte[] twoBytes = new byte[]{(byte) 0xFF, (byte) 0xD8};
        assertThat(FileSignature.detect(twoBytes)).isEmpty();

        // WebP 11 byte
        byte[] elevenBytes = new byte[]{
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B'
        };
        assertThat(FileSignature.detect(elevenBytes)).isEmpty();
    }

    @Test
    void detect_null_traVeRong() {
        assertThat(FileSignature.detect(null)).isEmpty();
    }

    @Test
    void detect_vanBanHelloWorld_traVeRong() {
        byte[] text = "hello world".getBytes(StandardCharsets.UTF_8);
        assertThat(FileSignature.detect(text)).isEmpty();
    }

    @Test
    void detect_pngSaiMotByte_traVeRong() {
        byte[] badPng = new byte[]{
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x00 // byte cuối sai
        };
        assertThat(FileSignature.detect(badPng)).isEmpty();
    }

    @Test
    void detect_gif_traVeRong() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        assertThat(FileSignature.detect(gif)).isEmpty();
    }
}
