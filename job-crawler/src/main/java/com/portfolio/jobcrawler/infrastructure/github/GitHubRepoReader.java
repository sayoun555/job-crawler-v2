package com.portfolio.jobcrawler.infrastructure.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * GitHub API를 활용한 레포지토리 분석기.
 * README, 빌드 파일, 소스 구조, 핵심 소스 코드를 읽어 AI 분석 소스 데이터를 제공한다.
 */
@Slf4j
@Component
public class GitHubRepoReader {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_BASE = "https://api.github.com";

    private static final List<String> BUILD_FILES = List.of(
            "build.gradle", "build.gradle.kts", "pom.xml", "package.json",
            "job-crawler/build.gradle", "job-frontend/package.json"
    );

    private static final List<String> CONFIG_FILES = List.of(
            "docker-compose.yml", "Dockerfile", "nginx.conf",
            "ecosystem.config.js", "deploy.sh"
    );

    public String parseOwnerRepo(String githubUrl) {
        if (githubUrl == null) return null;
        String url = githubUrl.replace("https://github.com/", "").replace("http://github.com/", "");
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        return url;
    }

    @SuppressWarnings("unchecked")
    public String readRepoForAnalysis(String githubUrl) {
        String ownerRepo = parseOwnerRepo(githubUrl);
        if (ownerRepo == null || !ownerRepo.contains("/")) {
            return "GitHub URL을 파싱할 수 없습니다: " + githubUrl;
        }

        StringBuilder result = new StringBuilder();

        // 1) README.md
        appendFile(result, ownerRepo, "README.md", 4000);

        // 2) 빌드 파일 (루트 + 하위 모듈)
        for (String buildFile : BUILD_FILES) {
            appendFile(result, ownerRepo, buildFile, 3000);
        }

        // 3) 설정 파일
        for (String configFile : CONFIG_FILES) {
            appendFile(result, ownerRepo, configFile, 1000);
        }

        // 4) Spring 설정 파일
        appendFile(result, ownerRepo,
                "job-crawler/src/main/resources/application.properties", 1500);
        appendFile(result, ownerRepo,
                "job-crawler/src/main/resources/application-prod.properties", 1500);

        // 5) 프로젝트 구조 (파일 트리)
        try {
            String tree = readTree(ownerRepo);
            if (tree != null) {
                result.append("[프로젝트 구조]\n").append(truncate(tree, 3000)).append("\n\n");
            }
        } catch (Exception e) {
            log.debug("트리 읽기 실패: {}", e.getMessage());
        }

        // 6) 핵심 소스 파일 (컨트롤러/서비스 목록에서 주요 파일 읽기)
        appendKeySourceFiles(result, ownerRepo);

        // 7) 추가 .md 문서
        appendExtraMarkdownFiles(result, ownerRepo);

        if (result.length() == 0) {
            return "레포지토리에서 분석할 파일을 찾지 못했습니다.";
        }

        return result.toString();
    }

    private void appendFile(StringBuilder result, String ownerRepo, String path, int maxLength) {
        try {
            String content = readFile(ownerRepo, path);
            if (content != null && !content.isEmpty()) {
                result.append("[").append(path).append("]\n")
                        .append(truncate(content, maxLength)).append("\n\n");
            }
        } catch (Exception e) {
            log.debug("{} 읽기 실패: {}", path, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void appendKeySourceFiles(StringBuilder result, String ownerRepo) {
        try {
            String tree = readTree(ownerRepo);
            if (tree == null) return;

            String[] lines = tree.split("\n");
            int totalRead = 0;

            for (String line : lines) {
                if (totalRead >= 5) break;

                // 컨트롤러, 서비스 인터페이스, 엔티티 등 핵심 파일만
                if (line.endsWith("Controller.java") || line.endsWith("Service.java")
                        || (line.contains("/entity/") && line.endsWith(".java") && !line.contains("Base"))) {
                    try {
                        String content = readFile(ownerRepo, line);
                        if (content != null && !content.isEmpty()) {
                            result.append("[").append(line).append("]\n")
                                    .append(truncate(content, 1500)).append("\n\n");
                            totalRead++;
                        }
                    } catch (Exception e) { /* skip */ }
                }
            }
        } catch (Exception e) {
            log.debug("핵심 소스 파일 읽기 실패: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void appendExtraMarkdownFiles(StringBuilder result, String ownerRepo) {
        try {
            List<Map<String, Object>> contents = restTemplate.getForObject(
                    API_BASE + "/repos/" + ownerRepo + "/contents", List.class);
            if (contents != null) {
                for (Map<String, Object> item : contents) {
                    String name = (String) item.get("name");
                    if (name != null && name.endsWith(".md") && !name.equalsIgnoreCase("README.md")) {
                        appendFile(result, ownerRepo, name, 1500);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("contents 목록 조회 실패: {}", e.getMessage());
        }
    }

    private String readFile(String ownerRepo, String path) {
        try {
            String url = API_BASE + "/repos/" + ownerRepo + "/contents/" + path;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("content")) {
                String encoded = ((String) response.get("content")).replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(encoded));
            }
        } catch (Exception e) { /* file not found */ }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String readTree(String ownerRepo) {
        try {
            String url = API_BASE + "/repos/" + ownerRepo + "/git/trees/main?recursive=1";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                url = API_BASE + "/repos/" + ownerRepo + "/git/trees/master?recursive=1";
                response = restTemplate.getForObject(url, Map.class);
            }
            if (response != null && response.containsKey("tree")) {
                List<Map<String, Object>> tree = (List<Map<String, Object>>) response.get("tree");
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> item : tree) {
                    String treePath = (String) item.get("path");
                    String type = (String) item.get("type");
                    if ("blob".equals(type) && treePath != null
                            && !treePath.contains("node_modules")
                            && !treePath.contains(".git/")
                            && !treePath.contains("build/")
                            && !treePath.contains(".next/")) {
                        sb.append(treePath).append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.debug("Git tree 읽기 실패: {}", e.getMessage());
        }
        return null;
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
