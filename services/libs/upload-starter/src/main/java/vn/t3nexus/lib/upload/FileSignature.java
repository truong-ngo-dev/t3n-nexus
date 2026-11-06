package vn.t3nexus.lib.upload;

import java.util.Optional;

/**
 * Tiện ích nhận diện chữ ký tệp (magic bytes).
 */
public final class FileSignature {

    public static final int HEAD_LENGTH = 12;

    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
    private static final byte[] RIFF_MAGIC = new byte[]{'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = new byte[]{'W', 'E', 'B', 'P'};

    private FileSignature() {
    }

    /**
     * Nhận diện loại nội dung thật dựa trên các byte đầu.
     *
     * @param head mảng byte đầu tệp
     * @return loại nội dung thật hoặc rỗng nếu không nhận diện được
     */
    public static Optional<String> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }

        // JPEG: 3 byte đầu là FF D8 FF
        if (head.length >= 3 && matches(head, 0, JPEG_MAGIC)) {
            return Optional.of("image/jpeg");
        }

        // PNG: 8 byte đầu là 89 50 4E 47 0D 0A 1A 0A
        if (head.length >= 8 && matches(head, 0, PNG_MAGIC)) {
            return Optional.of("image/png");
        }

        // WebP: byte 0..3 là "RIFF" và byte 8..11 là "WEBP" (cần đủ 12 byte)
        if (head.length >= 12 && matches(head, 0, RIFF_MAGIC) && matches(head, 8, WEBP_MAGIC)) {
            return Optional.of("image/webp");
        }

        return Optional.empty();
    }

    private static boolean matches(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
