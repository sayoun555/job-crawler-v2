package com.portfolio.jobcrawler.global.util;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이미지 URL에서 텍스트를 추출한다 (Tesseract OCR).
 * 한국어 + 영어를 동시에 인식한다.
 */
@Slf4j
public final class ImageOcrUtil {

    private ImageOcrUtil() {}

    /**
     * 여러 이미지 URL에서 텍스트를 추출하여 합친다.
     * 실패한 이미지는 스킵한다.
     */
    public static String extractTextFromImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return "";

        return imageUrls.stream()
                .limit(3) // 최대 3장만
                .map(ImageOcrUtil::extractTextFromImage)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 단일 이미지 URL에서 텍스트를 추출한다.
     */
    public static String extractTextFromImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("http")) return "";

        Path tempFile = null;
        try {
            // 이미지 다운로드
            try (InputStream in = URI.create(imageUrl).toURL().openStream()) {
                tempFile = Files.createTempFile("ocr_", ".png");
                Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Tesseract 실행 (한국어 + 영어)
            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract", tempFile.toString(), "stdout",
                    "-l", "kor+eng", "--psm", "6"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes()).trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.debug("[OCR] Tesseract 실행 실패 (exit:{}): {}", exitCode, imageUrl);
                return "";
            }

            // 의미 없는 결과 필터링 (너무 짧거나 특수문자만)
            if (output.length() < 10) return "";

            log.debug("[OCR] 추출 완료: {}자 ({})", output.length(), imageUrl);
            return output;

        } catch (Exception e) {
            log.debug("[OCR] 이미지 처리 실패: {} - {}", imageUrl, e.getMessage());
            return "";
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }
}
