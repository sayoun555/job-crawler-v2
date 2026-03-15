package com.portfolio.jobcrawler.infrastructure.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * GitHub API를 활용한 레포지토리 분석기.
 * 레포 README, 빌드 파일, 소스 구조를 읽어 AI 분석 소스 데이터를 제공한다.
 */
@Slf4j
@Component
public class GitHubRepoReader {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_BASE = "https://api.github.com";

    /**
     * GitHub URL에서 owner/repo를 추출한다.
     * 예: https://github.com/user/repo → "user/repo"
     */
    public String parseOwnerRepo(String githubUrl) {
        if (githubUrl == null)
            return null;
        String url = githubUrl.replace("https://github.com/", "").replace("http://github.com/", "");
        if (url.endsWith("/"))
            url = url.substring(0, url.length() - 1);
        if (url.endsWith(".git"))
            url = url.substring(0, url.length() - 4);
        return url;
    }

    /**
     * 레포의 주요 파일들을 읽어서 분석용 텍스트를 생성한다.
     * 우선순위: README.md → build.gradle/pom.xml/package.json → src/ 구조
     */
    @SuppressWarnings("unchecked")
    public String readRepoForAnalysis(String githubUrl) {
        String ownerRepo = parseOwnerRepo(githubUrl);
        if (ownerRepo == null || !ownerRepo.contains("/")) {
            return "GitHub URL을 파싱할 수 없습니다: " + githubUrl;
        }

        StringBuilder result = new StringBuilder();

        // 1) README.md
        try {
            String readme = readFile(ownerRepo, "README.md");
            if (readme != null && !readme.isEmpty()) {
                result.append("[README.md]\n").append(truncate(readme, 3000)).append("\n\n");
            }
        } catch (Exception e) {
            log.debug("README.md 읽기 실패: {}", e.getMessage());
        }

        // 2) 빌드 파일
        for (String buildFile : List.of("build.gradle", "pom.xml", "package.json", "build.gradle.kts")) {
            try {
                String content = readFile(ownerRepo, buildFile);
                if (content != null && !content.isEmpty()) {
                    result.append("[").append(buildFile).append("]\n").append(truncate(content, 2000)).append("\n\n");
                    break;
                }
            } catch (Exception e) {
                /* skip */ }
        }

        // 3) 소스 디렉토리 구조
        try {
            String tree = readTree(ownerRepo);
            if (tree != null) {
                result.append("[프로젝트 구조]\n").append(truncate(tree, 2000)).append("\n\n");
            }
        } catch (Exception e) {
            log.debug("트리 읽기 실패: {}", e.getMessage());
        }

        // 4) 추가 .md 문서
        try {
            List<Map<String, Object>> contents = restTemplate.getForObject(
                    API_BASE + "/repos/" + ownerRepo + "/contents", List.class);
            if (contents != null) {
                for (Map<String, Object> item : contents) {
                    String name = (String) item.get("name");
                    if (name != null && name.endsWith(".md") && !name.equalsIgnoreCase("README.md")) {
                        try {
                            String md = readFile(ownerRepo, name);
                            if (md != null) {
                                result.append("[").append(name).append("]\n").append(truncate(md, 1000)).append("\n\n");
                            }
                        } catch (Exception e) {
                            /* skip */ }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("contents 목록 조회 실패: {}", e.getMessage());
        }

        if (result.length() == 0) {
            return "레포지토리에서 분석할 파일을 찾지 못했습니다.";
        }

        return result.toString();
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
        } catch (Exception e) {
            /* file not found */ }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String readTree(String ownerRepo) {
        try {
            String url = API_BASE + "/repos/" + ownerRepo + "/git/trees/main?recursive=1";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                // main 브랜치가 없으면 master 시도
                url = API_BASE + "/repos/" + ownerRepo + "/git/trees/master?recursive=1";
                response = restTemplate.getForObject(url, Map.class);
            }
            if (response != null && response.containsKey("tree")) {
                List<Map<String, Object>> tree = (List<Map<String, Object>>) response.get("tree");
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> item : tree) {
                    String treePath = (String) item.get("path");
                    String type = (String) item.get("type");
                    if ("blob".equals(type) && treePath != null && !treePath.contains("node_modules")
                            && !treePath.contains(".git") && !treePath.contains("build/")) {
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
