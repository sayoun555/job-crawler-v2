package com.portfolio.jobcrawler.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 파일 업로드 서비스.
 * 프로젝트 이미지, 지원 첨부 파일 등을 로컬 디스크에 저장한다.
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    /**
     * 파일을 저장하고 접근 URL을 반환한다.
     */
    public String storeFile(MultipartFile file, String subdirectory) throws IOException {
        Path uploadPath = Paths.get(uploadDir, subdirectory);
        Files.createDirectories(uploadPath);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID() + ext;

        Path targetPath = uploadPath.resolve(storedName);
        file.transferTo(targetPath.toFile());

        log.info("파일 저장 완료: {}", targetPath);
        return "/uploads/" + subdirectory + "/" + storedName;
    }

    /**
     * 프로젝트 이미지 저장.
     */
    public String storeProjectImage(MultipartFile file, Long projectId) throws IOException {
        return storeFile(file, "projects/" + projectId);
    }

    /**
     * 지원 첨부 파일 저장.
     */
    public String storeApplicationAttachment(MultipartFile file, Long applicationId) throws IOException {
        return storeFile(file, "applications/" + applicationId);
    }

    /**
     * 저장된 파일의 실제 시스템 경로 반환.
     */
    public Path getFilePath(String relativePath) {
        return Paths.get(uploadDir).resolve(relativePath.replaceFirst("^/uploads/", ""));
    }
}
