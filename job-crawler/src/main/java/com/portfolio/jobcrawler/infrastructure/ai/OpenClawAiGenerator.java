package com.portfolio.jobcrawler.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationRequest;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenClaw HTTP API (OpenAI Compatible) 연동 구현체
 */
@Slf4j
@Component
public class OpenClawAiGenerator implements AiTextGenerator {

    @Value("${openclaw.api.url:http://localhost:18789/v1/chat/completions}")
    private String apiUrl;

    @Value("${openclaw.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // AI API 동시 요청 제한 (서버 과부하 방지, application.properties에서 조정 가능)
    @Value("${ai.max-concurrent-requests:15}")
    private int maxConcurrentRequests;

    @Value("${ai.temperature:0.3}")
    private double aiTemperature;

    private java.util.concurrent.Semaphore aiSemaphore;

    @jakarta.annotation.PostConstruct
    private void initSemaphore() {
        aiSemaphore = new java.util.concurrent.Semaphore(maxConcurrentRequests);
        log.info("[AI] Semaphore 초기화: 동시 요청 최대 {}개", maxConcurrentRequests);
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        try {
            String prompt = buildPrompt(request);
            String draft = callHttpApi(prompt, request.getImageUrls());

            // 자소서/포트폴리오는 2차 다듬기 호출
            if (needsPolishing(request.getType())) {
                String polished = polishDraft(draft, request);
                return AiGenerationResult.success(polished);
            }

            return AiGenerationResult.success(draft);
        } catch (Exception e) {
            log.error("AI 생성 실패: {}", e.getMessage());
            return AiGenerationResult.failure(e.getMessage());
        }
    }

    private boolean needsPolishing(AiGenerationRequest.GenerationType type) {
        return type == AiGenerationRequest.GenerationType.COVER_LETTER
                || type == AiGenerationRequest.GenerationType.CUSTOM_COVER_LETTER
                || type == AiGenerationRequest.GenerationType.PORTFOLIO
                || type == AiGenerationRequest.GenerationType.CUSTOM_PORTFOLIO;
    }

    private String polishDraft(String draft, AiGenerationRequest request) {
        boolean isPortfolio = request.getType() == AiGenerationRequest.GenerationType.PORTFOLIO
                || request.getType() == AiGenerationRequest.GenerationType.CUSTOM_PORTFOLIO;
        if (isPortfolio) {
            return polishPortfolioDraft(draft, request);
        }
        return polishCoverLetterDraft(draft, request);
    }

    private String polishPortfolioDraft(String draft, AiGenerationRequest request) {
        String companyName = "";
        String jobRole = "";
        if (request.getJobDescription() != null) {
            String jobDesc = request.getJobDescription();
            if (jobDesc.contains("회사: ")) {
                int start = jobDesc.indexOf("회사: ") + 4;
                int end = jobDesc.indexOf("\n", start);
                companyName = end > start ? jobDesc.substring(start, end).trim() : "";
            }
            if (jobDesc.contains("직무: ")) {
                int start = jobDesc.indexOf("직무: ") + 4;
                int end = jobDesc.indexOf("\n", start);
                jobRole = end > start ? jobDesc.substring(start, end).trim() : "";
            }
        }

        // 프로젝트 원본 데이터 (기술명 복원용)
        String projectData = request.getMatchedProjects() != null ? request.getMatchedProjects() : "";

        String polishPrompt = String.format("""
                %s %s 지원용 포트폴리오를 제출형으로 다듬어라.
                초안의 내용과 구조를 유지하면서 아래 규칙만 적용하라. 내용을 삭제하거나 크게 재구성하지 마라.

                편집 규칙:
                - 수치와 결과는 반드시 유지.
                - 같은 메시지를 다른 표현으로 반복하는 문장은 하나만 남기고 삭제.
                - "관리", "조정", "설계", "구성" 같은 포장 단어가 기술명 없이 쓰였으면, [프로젝트 원본]에서 해당 기술명을 찾아 구체적으로 교체.
                - 순수 텍스트만. 마크다운, HTML 절대 금지.
                - 초안에서 추상화된 기술 표현은 [프로젝트 원본]을 참고해 원본 기술명으로 복원.
                - GitHub URL, 대학명, 학력, 학점 등 개인정보가 있으면 삭제.
                - "판단했습니다"→"봤습니다/생각했습니다", "적용했습니다"→"넣었습니다/썼습니다", "구현했습니다"→"만들었습니다", "확보했습니다"→"갖췄습니다"로 교체.

                === 문체 교정 (가장 중요) ===
                초안은 "~했습니다. ~했습니다. ~했습니다." 리듬이 반복될 수 있다. 아래 규칙으로 교정하라:

                1. 연속 3문장이 같은 종결 어미("~습니다.")로 끝나면, 중간 문장을 접속사로 이어붙여라.
                   BAD: "분리했습니다. 적용했습니다. 줄였습니다."
                   GOOD: "분리했는데, 그 과정에서 ~ 적용하면서 결국 ~를 줄일 수 있었습니다."

                2. 문단마다 시작 방식을 바꿔라:
                   - 배경부터: "초기에는 ~"
                   - 결과부터: "수집 실패율을 0%%로 낮춘 뒤에야 ~"
                   - 대비로: "단순히 ~ 하는 것으로는 부족했고"
                   - 질문으로: "왜 같은 문제가 반복되는지 보니"

                3. 시니어 개발자가 후배에게 경험을 설명하듯 자연스럽게. 보고서가 아니라 대화체에 가깝게.

                레퍼런스 (이 톤을 따라라):
                "초기에는 소스마다 HTML 구조와 접근 방식이 달라 동일 파서 적용 시 특정 페이지에서 바로 실패했습니다. 운영자는 한 건만 실패해도 다음 등록을 미루게 되었고, 수집 기능이 있어도 실제 운영은 수작업에 가까웠습니다."
                "batch-enrich 적용 전에는 여러 locationId를 동시에 보강할 때 동일 대상이 중복 처리됐고, 늦게 끝난 요청이 최신 값을 덮는 문제가 있었습니다."

                [프로젝트 원본]
                %s

                [초안]
                %s
                """, companyName, jobRole, projectData, draft);

        log.info("[AI] 2차 포트폴리오 다듬기 호출 시작 (회사: {})", companyName);
        try {
            String polished = callHttpApi(polishPrompt);
            log.info("[AI] 2차 포트폴리오 다듬기 완료 (초안 {}자 → 최종 {}자)", draft.length(), polished.length());
            return polished;
        } catch (Exception e) {
            log.warn("[AI] 2차 포트폴리오 다듬기 실패, 1차 초안 반환: {}", e.getMessage());
            return draft;
        }
    }

    private String polishCoverLetterDraft(String draft, AiGenerationRequest request) {
        String companyName = "";
        String jobRole = "";
        String charLimit = "1500~2000";

        // 공고 정보에서 회사명, 직무 추출
        if (request.getJobDescription() != null) {
            String jobDesc = request.getJobDescription();
            if (jobDesc.contains("회사: ")) {
                int start = jobDesc.indexOf("회사: ") + 4;
                int end = jobDesc.indexOf("\n", start);
                companyName = end > start ? jobDesc.substring(start, end).trim() : "";
            }
            if (jobDesc.contains("직무: ")) {
                int start = jobDesc.indexOf("직무: ") + 4;
                int end = jobDesc.indexOf("\n", start);
                jobRole = end > start ? jobDesc.substring(start, end).trim() : "";
            }
        }

        // 사이트별 글자수
        if ("LINKAREER".equals(request.getSourceSite())) {
            charLimit = "900~1000";
        }

        // 공고에서 자격요건 키워드 추출
        String jdKeywords = "";
        if (request.getJobDescription() != null) {
            String jd = request.getJobDescription();
            if (jd.contains("기술스택: ")) {
                int start = jd.indexOf("기술스택: ") + 6;
                int end = jd.indexOf("\n", start);
                jdKeywords = end > start ? jd.substring(start, end).trim() : "";
            }
        }

        String polishPrompt = String.format("""
                너는 자기소개서 편집자다.
                아래 초안을 %s %s 지원용 제출형으로 압축/정리하라.

                편집 규칙:
                - 반복 표현 줄이고, 문단별 역할 분명히 나눠라.
                - 수치와 결과는 반드시 유지.
                - 톤은 과장 없이 논리적이고 단정하게.
                - 새 사실 추가 금지. 초안에 없는 내용을 만들지 마라.
                - 순수 텍스트만. 마크다운, HTML 절대 금지.
                - 문단을 독립적으로 시작하지 마라. "이런 관점에서", "~도 있습니다", "~에서도" 같은 연결어로 전체가 하나의 흐름으로 읽히게 하라.
                - "~에서는", "~에서는" 패턴으로 각 문단을 나열하지 마라.
                - 각 문단 시작에 [소제목]을 반드시 유지하라. 초안에 소제목이 있으면 그대로 두고, 없으면 해당 문단의 핵심 메시지를 [대괄호 소제목]으로 추가하라.

                삭제 대상:
                - 회사명만 바꿔도 성립하는 문장.
                - "성장하겠다", "배우겠다", "기여하겠다" 식 상투 포부.
                - 구체적 사례 없이 "소통을 중시합니다" 같은 협업 미화.
                - 같은 메시지를 다른 표현으로 반복하는 문장. 특히 "운영이 중요하다", "안정성이 중요하다", "유지보수가 중요하다"가 여러 문단에 걸쳐 반복되면 하나만 남기고 삭제.
                - 핵심 메시지는 첫 문단에서 한 번만 말하고, 이후 문단에서는 구체적 경험으로 증명만 하라.
                - 학력, 학교명, 대학명, 학점, 졸업 연도, 전공 언급. "XX대학교", "졸업 이후" 같은 표현이 있으면 삭제하라.
                - "판단했습니다", "적용했습니다", "구현했습니다", "확보했습니다", "활용했습니다" → "생각했습니다/봤습니다/넣었습니다/만들었습니다/썼습니다"로 교체하라.

                유지 대상:
                - 아래 자격요건 키워드와 직접 연결된 경험.
                - 정량적 수치 (퍼센트, 시간, 건수 등).
                - 지원자만의 구체적 에피소드와 교훈.

                회사 맞춤 규칙:
                - 회사 설명을 길게 하지 마라. 내 경험이 이 회사 업무에 왜 맞는지 한 문장으로 연결하라.
                - 지원동기에서 회사 소개를 나열하지 말고, 내 경험과 회사 업무의 접점만 쓰라.
                - "이 회사의 업무와 맞닿아 있다" 같은 추상 연결 대신, 이 회사가 하는 구체적 일(공고 키워드)과 내 경험이 왜 맞는지 한 줄로 써라.
                - 마지막 문단에서 공고 키워드의 구체적 업무와 내 경험을 직접 연결하라.

                출력 조건:
                - 6문단 이하. 문단별 역할 명확.
                - %s자 이내 (공백 포함).
                - 마지막 문단은 "성장" 대신 이 회사의 구체적 업무에서 내가 할 수 있는 일.

                [공고 자격요건 키워드]
                %s

                [초안]
                %s
                """, companyName, jobRole, charLimit, jdKeywords, draft);

        log.info("[AI] 2차 다듬기 호출 시작 (회사: {}, 글자수: {})", companyName, charLimit);
        try {
            String polished = callHttpApi(polishPrompt);
            log.info("[AI] 2차 다듬기 완료 (초안 {}자 → 최종 {}자)", draft.length(), polished.length());
            return polished;
        } catch (Exception e) {
            log.warn("[AI] 2차 다듬기 실패, 1차 초안 반환: {}", e.getMessage());
            return draft;
        }
    }

    @Override
    public int calculateMatchScore(String userProfile, String jobDescription) {
        try {
            String prompt = String.format("""
                    사용자 프로필과 채용 공고의 적합도를 분석하세요.
                    가장 중요한 기준은 '기술스택 매칭'입니다.

                    반드시 아래 JSON으로만 응답. JSON 외 텍스트 금지.

                    {
                      "totalScore": 0-100,
                      "matched": ["매칭된 기술"],
                      "missing": ["부족한 기술"],
                      "summary": "적합도 요약 1문장"
                    }

                    === 핵심 규칙 ===
                    1. 기술스택 매칭이 점수의 60%% 차지. 사용자 기술과 공고 기술이 얼마나 겹치는지가 핵심.
                    2. 같은 분야(백엔드↔백엔드, 프론트↔프론트)면 기본 50점 이상.
                    3. 다른 분야(백엔드↔미디어, 개발↔마케팅)면 30점 이하.
                    4. 경력은 부족해도 기술이 맞으면 감점 적게.
                    5. 관련 기술도 인정 (Spring Boot ↔ Spring 90%%, Java ↔ Kotlin 80%%).

                    === 점수 기준 ===
                    - 80+: 기술 대부분 매칭, 같은 직무
                    - 60-79: 기술 절반 이상 매칭
                    - 40-59: 일부 기술 매칭, 다른 분야지만 관련 있음
                    - 20-39: 기술 거의 안 맞음
                    - 0-19: 완전 다른 직무

                    [사용자 프로필]
                    %s

                    [채용 공고]
                    %s
                    """, userProfile, jobDescription);

            String response = callHttpApi(prompt).trim();

            // JSON에서 totalScore 추출
            try {
                String jsonStr = response;
                if (jsonStr.contains("```json")) {
                    jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                    jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
                } else if (jsonStr.contains("```")) {
                    jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                    jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
                }
                jsonStr = jsonStr.trim();
                if (jsonStr.startsWith("{")) {
                    var node = objectMapper.readTree(jsonStr);
                    if (node.has("totalScore")) {
                        return Math.min(100, Math.max(0, node.get("totalScore").asInt()));
                    }
                }
            } catch (Exception ignored) {
            }

            // JSON 파싱 실패 시 숫자만 추출
            String numOnly = response.replaceAll("[^0-9]", "");
            return numOnly.isEmpty() ? -1
                    : Math.min(100, Integer.parseInt(numOnly.substring(0, Math.min(3, numOnly.length()))));
        } catch (Exception e) {
            log.error("적합률 계산 실패: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> calculateMatchScoreWithReason(String userProfile, String jobDescription) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalScore", -1);

        try {
            String prompt = String.format("""
                    사용자 프로필과 채용 공고의 적합도를 분석하세요.
                    가장 중요한 기준은 '기술스택 매칭'입니다.

                    반드시 아래 JSON으로만 응답. JSON 외 텍스트 금지.

                    {
                      "totalScore": 0-100,
                      "matched": ["매칭된 기술"],
                      "missing": ["부족한 기술"],
                      "summary": "적합도 요약 1문장"
                    }

                    === 핵심 규칙 ===
                    1. 기술스택 매칭이 점수의 60%%%% 차지. 사용자 기술과 공고 기술이 얼마나 겹치는지가 핵심.
                    2. 같은 분야(백엔드↔백엔드, 프론트↔프론트)면 기본 50점 이상.
                    3. 다른 분야(백엔드↔미디어, 개발↔마케팅)면 30점 이하.
                    4. 경력은 부족해도 기술이 맞으면 감점 적게.
                    5. 관련 기술도 인정 (Spring Boot ↔ Spring 90%%%%, Java ↔ Kotlin 80%%%%).

                    === 점수 기준 ===
                    - 80+: 기술 대부분 매칭, 같은 직무
                    - 60-79: 기술 절반 이상 매칭
                    - 40-59: 일부 기술 매칭, 다른 분야지만 관련 있음
                    - 20-39: 기술 거의 안 맞음
                    - 0-19: 완전 다른 직무

                    [사용자 프로필]
                    %s

                    [채용 공고]
                    %s
                    """, userProfile, jobDescription);

            String response = callHttpApi(prompt).trim();
            String jsonStr = extractJson(response);

            if (jsonStr != null) {
                var node = objectMapper.readTree(jsonStr);
                if (node.has("totalScore")) {
                    result.put("totalScore", Math.min(100, Math.max(0, node.get("totalScore").asInt())));
                }
                if (node.has("matched")) {
                    result.put("matched", objectMapper.convertValue(node.get("matched"), List.class));
                }
                if (node.has("missing")) {
                    result.put("missing", objectMapper.convertValue(node.get("missing"), List.class));
                }
                if (node.has("summary")) {
                    result.put("summary", node.get("summary").asText());
                }
            }
        } catch (Exception e) {
            log.error("적합률 계산 실패: {}", e.getMessage());
        }
        return result;
    }

    private String extractJson(String response) {
        String jsonStr = response;
        if (jsonStr.contains("```json")) {
            jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
            jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
        } else if (jsonStr.contains("```")) {
            jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
            jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
        }
        jsonStr = jsonStr.trim();
        return jsonStr.startsWith("{") ? jsonStr : null;
    }

    @Override
    public int calculateMatchScore(String userProfile, String jobDescription, List<String> imageUrls) {
        return calculateMatchScore(userProfile, jobDescription);
    }

    @Override
    public String summarizeProject(String projectDescription, String techStack) {
        try {
            String prompt = String.format("""
                    너는 대기업 채용 면접관 출신 포트폴리오 컨설턴트이다.
                    아래 프로젝트 정보를 바탕으로, 채용 담당자가 읽었을 때 "이 사람 면접 보고 싶다"고 느낄 수준으로 정리하라.
                    마크다운 형식으로 가독성 좋게 작성. (##, -, **볼드**, `코드` 등 활용)
                    "다음 단계", "원하시면", "추가로" 같은 후속 제안 문구는 절대 넣지 마세요.

                    [프로젝트 설명]
                    %s

                    [기술 스택]
                    %s

                    === 핵심 원칙 ===
                    0. 어투: 반드시 "~합니다/~했습니다/~됩니다" 존댓말. "~했다/~이다" 반말 금지.
                    1. STAR 기법 적용: 상황(S) → 과제(T) → 행동(A) → 결과(R)
                    2. 추상적 표현 금지: "효율적으로 처리" → "Redis 캐시로 중복 체크하여 DB 쿼리 90%% 감소"
                    3. 정량적 수치 우선: 성과가 있으면 반드시 숫자로. 없으면 추정하지 말 것
                    4. 기술 선택 이유 필수: "왜 이 기술을?" 에 답할 수 있도록
                    5. 면접 질문 대비: 면접관이 "여기서 가장 어려웠던 점은?" 물었을 때 답이 될 내용 포함
                    6. 클래스명(CrawlerServiceImpl 등) 직접 노출 금지. 기능적 역할로 설명

                    === 작성 형식 ===

                    ## 한 줄 소개
                    (이 프로젝트가 뭔지 + 핵심 임팩트를 한 문장으로)

                    ## 배경
                    - 누가 어떤 문제를 겪고 있었는지
                    - 기존 방법의 한계는 무엇이었는지

                    ## 목표
                    - 이 프로젝트로 어떻게 해결하려 했는지

                    ## 구현
                    (각 기능을 **기능명**: WHAT + HOW + WHY 형태로 5~8개)
                    - BAD: "크롤링 기능 구현"
                    - GOOD: "**4개 사이트 병렬 크롤링**: Playwright + Strategy Pattern으로 사이트별 파서를 분리. innerHTML로 HTML 구조를 보존하고 Jsoup으로 XSS 방지. Redis 중복 체크로 DB 부하 최소화"

                    ## 아키텍처
                    - 사용 기술을 **Backend / Frontend / Infra / AI** 카테고리로 정리
                    - 각 기술의 **선택 이유**를 한 줄씩 (면접에서 반드시 물어봄)
                    - 적용한 아키텍처 패턴과 설계 원칙

                    ## 기술적 도전과 해결
                    (STAR 형태로 3~5개. 면접 핵심 질문 대비)
                    - **[문제]**: 구체적 상황
                    - **[원인]**: 왜 발생했는지
                    - **[시도]**: 어떤 접근을 했는지
                    - **[해결]**: 최종 해결 방법 + 결과 수치

                    ## 성과 및 배운 점
                    - 정량적 성과 (있으면)
                    - 기술적 성장 포인트
                    - 다음에 다르게 할 점
                    """, projectDescription, techStack);
            return callHttpApi(prompt);
        } catch (Exception e) {
            log.error("프로젝트 정리 실패: {}", e.getMessage());
            return "";
        }
    }

    private String buildPrompt(AiGenerationRequest request) {
        return switch (request.getType()) {
            case COVER_LETTER -> buildCoverLetterPrompt(request);
            case CUSTOM_COVER_LETTER -> buildCustomCoverLetterPrompt(request);

            case PORTFOLIO -> buildPortfolioPrompt(request);
            case CUSTOM_PORTFOLIO -> buildCustomPortfolioPrompt(request);

            case COMPANY_ANALYSIS -> String.format("""
                    당신은 기업 리서치 전문가입니다.
                    아래 기업에 대해 지원자가 포트폴리오와 자기소개서를 작성할 때 참고할 수 있는 기업 정보를 정리해주세요.
                    웹 검색 결과가 있으면 그 데이터를 우선 활용하고, 당신이 알고 있는 정보도 추가로 활용하세요.
                    마크다운 형식으로 가독성 좋게 작성하세요. (##, -, **볼드**, `코드` 등 활용)
                    마지막에 "다음 단계", "원하시면", "추가로 정리해드릴게요" 같은 후속 제안 문구는 절대 넣지 마세요. 분석 내용만 작성하고 끝내세요.

                    다음 항목으로 정리해주세요:

                    ## 기업 개요
                    - 이 회사가 무슨 일을 하는 회사인지 (업종, 주요 사업/제품/서비스)
                    - 기업 규모 (직원수, 매출, 상장 여부)
                    - 설립 연도, 본사 위치

                    ## 비전과 핵심 가치
                    - 이 회사가 추구하는 방향성, 미션, 비전
                    - 핵심 가치나 경영 철학 (알려진 것이 있다면)

                    ## 주요 기술/사업 영역
                    - 어떤 기술을 쓰는지, 어떤 도메인에서 일하는지
                    - 경쟁사 대비 차별점이나 강점

                    ## 기업 문화 및 근무 환경
                    - 알려진 조직 문화, 워라밸, 복지 수준
                    - 직원 리뷰나 평판 (알고 있다면)

                    ## 포트폴리오/자소서 작성 시 참고 포인트
                    - 이 회사에 지원할 때 강조하면 좋을 역량이나 경험
                    - 이 회사의 사업/기술과 연결 지을 수 있는 키워드

                    [기업명] %s
                    [채용 공고 상세]
                    %s
                    """, request.getCompanyInfo(), request.getJobDescription());

            case PROJECT_SUMMARY -> String.format("""
                    너는 대기업 채용 면접관 출신 10년차 시니어 개발자이다.
                    GitHub 레포지토리의 소스 코드, 빌드 파일, 설정 파일, README, docs 폴더 속 기술 블로그(.md)를 꼼꼼히 읽고
                    채용 담당자가 "이 사람 면접 보고 싶다"고 느낄 수준의 포트폴리오 프로젝트 정보를 추출하라.

                    반드시 아래 JSON 형식으로만 응답하라. JSON 외 다른 텍스트는 절대 포함하지 마라.

                    {
                      "projectName": "실제 서비스명 (레포 slug가 아닌 README나 코드에서 확인한 이름)",
                      "description": "아래 STAR 구조로 작성한 설명",
                      "techStack": "쉼표로 구분된 기술 스택 (버전 포함)",
                      "keyFeatures": ["기능명: WHAT(무엇을) + HOW(어떻게) + WHY(왜)", ...],
                      "architecture": "아키텍처 설명",
                      "challenges": ["[S] 상황 [T] 과제 [A] 행동 [R] 결과", ...],
                      "architectureDiagramPrompt": "시스템 아키텍처 다이어그램용 프롬프트",
                      "featureDiagramPrompt": "주요 기능/사용자 흐름 다이어그램용 프롬프트"
                    }

                    ============================================================
                    공통 어투 규칙 (절대 준수)
                    ============================================================
                    - 반드시 "~합니다", "~했습니다", "~됩니다" 존댓말(합쇼체) 사용
                    - "~했다", "~이다", "~한다" 반말 절대 금지
                    - 클래스명(CrawlerServiceImpl 등) 직접 노출 금지. 기술적 역할로 설명
                    - "확장성을 확보했다" 같은 추상적 표현 대신 구체적 근거 제시

                    ============================================================
                    description 작성 규칙 (줄바꿈 \\n 필수)
                    ============================================================

                    [한 줄 요약] 핵심 가치 + 무엇을 하는 서비스인지 한 문장\\n
                    \\n
                    [배경]\\n
                    - 대상 사용자의 구체적 불편함 + 기존 솔루션 한계 2~3문장\\n
                    \\n
                    [목표]\\n
                    - 프로젝트 핵심 목표 1~2문장\\n
                    \\n
                    [구현]\\n
                    - 항목명(성능 수치): 문제 상황 → 왜 그 기술을 선택했는지(대안 비교) → 구체적 구현 방법(기술명+설정값) → 정량적 결과\\n
                    (5~7개)\\n
                    \\n
                    [성과]\\n
                    - 반드시 정량적 수치 포함. docs/ 블로그에서 수치를 찾아 사용\\n
                    - "확장성 확보" 같은 추상 표현 절대 금지\\n

                    === 구현 항목 작성 핵심 규칙 (가장 중요) ===

                    모든 구현 항목은 반드시 아래 4단 구조를 따라라:
                    1) 문제 정의 — 무엇이 문제였는지 수치와 함께 정의
                    2) 기술 선택 근거(Why) — 왜 이 기술/구조를 선택했는지. 대안을 검토했다면 왜 버렸는지
                    3) 구체적 구현 — 기술명, 라이브러리명, 설정값을 포함한 구현 방법
                    4) 정량적 결과 + 트레이드오프 — 개선 수치 + 부작용이 있었다면 어떻게 균형 잡았는지

                    === BAD (기능 나열형 — 이렇게 쓰면 실패) ===
                    - BAD: "소스별 수집기를 분리하고 HTML 파싱 3종, 브라우저 렌더링 1종, AI 대체 1종으로 오케스트레이션하여 수집하도록 구현했습니다. 결과적으로 AI 대체 경로로 운영을 이어갈 수 있게 했습니다."
                    → 문제: "결과적으로" 뻔한 결론, 왜 이 구조인지 없음, 수치 없음, 시행착오 없음

                    === GOOD (문제 해결형 — 반드시 이 수준으로) ===
                    - GOOD: "다중 소스 크롤링 안정성 확보(실패율 30%%→0%%): 5개 소스의 HTML 구조가 모두 달라 공통 파서로는 3개 소스에서 파싱 실패가 반복되었습니다. 처음에는 하나의 파이프라인으로 통합하려 했지만, 소스별 실패 원인이 달라 공통화보다 분리가 더 안정적이라 판단했습니다. Cheerio 기반 HTML 파싱 3종, Playwright 렌더링 1종, Google Gemini 2.5-flash AI 대체 1종으로 소스별 수집기를 분리하고, 한 경로 실패 시 AI가 대체 데이터를 생성하도록 오케스트레이션했습니다. 결과적으로 특정 소스 차단 시에도 수집이 중단되지 않았고, 대신 AI 대체 비용이 월 $2~3 추가되는 트레이드오프를 감수했습니다."

                    === 절대 금지 패턴 ===
                    - "결과적으로 ~할 수 있게 했습니다" 뻔한 결론 → 구체적 수치 + 트레이드오프로 끝낼 것
                    - "~를 구현했습니다" 만으로 끝나는 문장 → 왜(Why) + 결과(수치)가 반드시 있어야 함
                    - 개수만 있는 수치(5개, 30개) → 성능/개선 수치(30%%→0%%, 2초→0.3초)를 함께 넣을 것
                    - "오케스트레이션했습니다", "통합했습니다" 같은 포장 동사만으로 끝내기 → 구체적으로 무엇을 어떻게 연결했는지 기술명과 함께 서술

                    docs/ 블로그에서 찾을 수 있는 수치 예시:
                    - 크롤링 성공률 40%%→100%%
                    - 동시 접속 100명→300~500명
                    - DB 부하 80%% 감소
                    - 코드 412줄→250줄
                    - 보안 등급 C+(5.5/10)→B(7.5/10)
                    - 적합률 보정 (기술 50%% 일치 시 85%%→52%%)
                    - OCR 한글 인식 85~90%%

                    ============================================================
                    challenges 작성 규칙 (docs/ 기술 블로그 필수 참고)
                    ============================================================
                    - docs/ 폴더의 기술 블로그에서 문제 해결 과정을 찾아 반영
                    - 각 항목: "**항목명(수치):** 문제 → 원인 → 해결 → 결과"
                    - 5~8개 추출. 레퍼런스처럼 한 줄에 압축
                    - 정량적 수치 필수. 수치 없는 항목은 제외
                    - 존댓말

                    ============================================================
                    keyFeatures 작성 규칙
                    ============================================================
                    - 실제 구현된 기능만 추출 (README, 소스 구조 기반)
                    - 각 기능: "기능명: 무엇을 + 어떻게 + 왜"
                    - BAD: "채용 공고 크롤링"
                    - GOOD: "4개 사이트 동시 수집: Playwright로 사이트별 DOM 파서를 분리하고, Redis 중복 체크와 배치 저장으로 수천 건을 효율적으로 수집합니다"
                    - 5~8개

                    ============================================================
                    techStack 추출 규칙
                    ============================================================
                    - build.gradle, package.json, application.properties 등에서 추출
                    - 버전 포함 (Java 21, Spring Boot 3.4 등)
                    - 테스트/빌드 전용 도구 제외

                    ============================================================
                    architecture 작성 규칙 (가독성 핵심)
                    ============================================================
                    architecture를 한 덩어리 텍스트로 쓰지 말고, 줄바꿈(\\n)으로 카테고리별 분리:

                    [계층 구조] api→application→domain→infrastructure 한 줄 설명\\n
                    [디자인 패턴] 적용한 패턴과 이유 한 줄씩\\n
                    [데이터 저장] DB/Redis 역할 분리와 이유\\n
                    [성능] 커넥션 풀, 캐싱, Rate Limiting 수치\\n
                    [보안] 암호화, 인증, 헤더 한 줄씩\\n

                    각 카테고리는 2~3문장 이내. "왜 그렇게 했는지" 한 줄로.
                    BAD: "세션과 알림 이력은 DB를 원본 저장소로 두고 Redis를 캐시로 사용하는 Cache-Aside 패턴을 적용했습니다. Redis를 사용한 이유는 공고 통계 조회 부하를 줄이고..."
                    GOOD: "[데이터 저장] Cache-Aside: DB 원본 + Redis 캐시. Redis 장애 시 DB 복구로 중복 알림/세션 유실 방지"

                    마크다운 문법(#, *, ```) 사용 금지. 순수 텍스트만.

                    ============================================================
                    architectureDiagramPrompt 작성 규칙 (시스템 아키텍처)
                    ============================================================
                    Mermaid 문법으로 시스템 아키텍처 다이어그램 코드를 직접 생성하라.
                    자연어 프롬프트가 아니라 Mermaid 코드 자체를 출력해야 한다.

                    형식: graph TD 또는 graph LR (방향은 구조에 맞게 선택)

                    규칙:
                    - 실제 컴포넌트명 + 기술명 + 포트를 노드에 표시. 예: "Backend[Spring Boot :8080]"
                    - 화살표에 프로토콜/데이터 흐름 명시. 예: "-->|REST API|", "-->|WebSocket STOMP|"
                    - subgraph로 레이어 구분 (Client, Backend, Data, External 등)
                    - 색상 지정 금지. Mermaid 기본 스타일 사용.
                    - 노드 10~20개 수준. 너무 적으면 빈약하고, 너무 많으면 읽기 어려움.
                    - 설명 텍스트 없이 Mermaid 코드만 출력. ```mermaid 블록 없이 순수 코드만.

                    레퍼런스:
                    graph TD
                        subgraph Client
                            Browser[Next.js :3000]
                            Extension[Chrome Extension]
                        end
                        subgraph Backend
                            API[Spring Boot API :8080]
                            Crawler[Playwright Crawler]
                            AI[AI Generator]
                        end
                        subgraph Data
                            DB[(PostgreSQL :5432)]
                            Cache[(Redis :6379)]
                        end
                        Browser -->|REST API| API
                        Browser -->|WebSocket STOMP| API
                        API --> DB
                        API --> Cache
                        API --> Crawler
                        API --> AI
                        Crawler -->|Headless Browser| ExternalSites[채용 사이트 4곳]

                    ============================================================
                    featureDiagramPrompt 작성 규칙 (주요 기능/사용자 흐름)
                    ============================================================
                    Mermaid 문법으로 사용자 흐름도를 직접 생성하라.
                    자연어 프롬프트가 아니라 Mermaid 코드 자체를 출력해야 한다.

                    형식: flowchart TD 또는 flowchart LR

                    규칙:
                    - 사용자 행동을 시간순으로 나열. 조건 분기는 {}로 표현.
                    - 각 노드에 기능명 + 핵심 기술 표시. 예: "크롤링 시작[Playwright 4개 사이트 병렬]"
                    - 사용자 흐름과 백그라운드 프로세스를 subgraph으로 구분.
                    - 설명 텍스트 없이 Mermaid 코드만 출력. ```mermaid 블록 없이 순수 코드만.

                    레퍼런스:
                    flowchart TD
                        subgraph 사용자 흐름
                            Login[회원가입/로그인] --> Crawl[공고 크롤링 시작]
                            Crawl --> Browse[공고 목록 조회/필터]
                            Browse --> Apply{지원 준비}
                            Apply --> AI_CL[AI 자소서 생성]
                            Apply --> AI_PF[AI 포트폴리오 생성]
                            AI_CL --> Submit[원클릭 자동 지원]
                        end
                        subgraph 백그라운드
                            Schedule[스케줄러] --> AutoCrawl[자동 크롤링]
                            AutoCrawl --> Notify[Discord 알림]
                        end

                    공통 규칙:
                    - 소스 코드에서 확인된 실제 기능만 포함. 추측 금지.
                    - 복사해서 Mermaid Live Editor에 바로 붙여넣을 수 있는 완결된 코드.
                    - 줄바꿈(\\n)으로 코드 라인 구분.

                    [레포지토리 데이터]
                    %s
                    """, request.getMatchedProjects());

            case COVER_LETTER_ANALYSIS -> String.format(
                    """
                            너는 대기업 채용 컨설턴트이다. 합격 자기소개서를 분석하여 작성 패턴과 구조를 추출하라.

                            반드시 아래 JSON 형식으로만 응답하라. JSON 외 다른 텍스트는 절대 포함하지 마라.

                            {
                              "structure": ["문단1 역할 설명", "문단2 역할 설명", ...],
                              "pattern": "전체 자소서의 작성 패턴 요약 (2~3문장)",
                              "keywords": ["핵심 키워드1", "핵심 키워드2", ...],
                              "strengths": ["이 자소서의 강점1", "강점2", ...],
                              "template": "이 패턴을 따라 작성할 수 있는 템플릿. {{지원동기}}, {{직무역량}}, {{경험/성과}}, {{포부}} 같은 플레이스홀더 사용. 각 섹션에 작성 가이드를 포함."
                            }

                            === 분석 규칙 ===
                            - structure: 각 문단이 어떤 역할을 하는지 분석 (지원동기/직무역량/경험/성과/포부 등)
                            - pattern: STAR 기법, 두괄식, 미괄식 등 사용된 작성 기법 파악
                            - keywords: 합격에 기여했을 핵심 표현/키워드 10개 이내 추출
                            - strengths: 이 자소서가 왜 합격했는지 근거 3~5개
                            - template: 이 자소서의 구조를 그대로 따라할 수 있는 범용 템플릿 생성. 플레이스홀더와 작성 가이드 포함.
                            - 마크다운 문법 사용 금지. 순수 텍스트만.

                            [자소서 정보]
                            기업: %s
                            직무: %s
                            기업유형: %s

                            [자소서 본문]
                            %s
                            """,
                    request.getCompanyInfo(), request.getJobDescription(),
                    request.getSourceSite(), request.getMatchedProjects());

            case COVER_LETTER_PRESET_SEARCH -> """
                    너는 한국 대기업 취업 전문 컨설턴트이다.
                    2025~2026년 최신 대기업 자기소개서(자소서) 문항을 조사하여 정확한 정보를 제공하라.

                    반드시 아래 JSON 배열 형식으로만 응답하라. JSON 외 다른 텍스트는 절대 포함하지 마라.

                    [
                      {
                        "name": "기업명 (연도)",
                        "company": "기업 · 문항수 · 총글자수",
                        "questions": [
                          {
                            "number": 1,
                            "title": "실제 채용공고에 기재된 문항 원문",
                            "maxLength": 700,
                            "guide": "이 문항에서 묻는 핵심 포인트 1줄 요약"
                          }
                        ]
                      }
                    ]

                    === 조사 대상 기업 (가장 최근 채용공고 기준) ===
                    1. 삼성전자 - 2025~2026 상반기 공채 자소서 문항 (4문항, 약 4200자)
                    2. 현대자동차 - 가장 최근 신입 공채 문항
                    3. SK하이닉스 - 2025~2026 채용 문항 (필수3 + 선택1)
                    4. LG전자 - 가장 최근 채용 문항
                    5. 카카오 - 2026 신입크루 공채 문항 (Tech/비즈니스 직군)
                    6. 네이버 - 팀네이버 신입 공채 문항
                    7. CJ제일제당 - 가장 최근 채용 문항
                    8. 포스코 - 가장 최근 채용 문항
                    9. 한화에어로스페이스 - 2026 상반기 채용 문항
                    10. 공기업 NCS (한국전력공사 기준) - 가장 최근 문항

                    === 규칙 ===
                    - title 필드에는 실제 채용공고에 기재된 문항을 최대한 원문 그대로 작성
                    - maxLength는 해당 문항의 글자수 제한 (정확한 숫자)
                    - 확인할 수 없는 기업은 가장 최근 알려진 양식으로 작성하되 연도를 명시
                    - 각 기업별로 반드시 1개 이상의 질문을 포함
                    - 마크다운 문법 사용 금지
                    """;
        };
    }

    private String buildCoverLetterPrompt(AiGenerationRequest request) {
        String site = request.getSourceSite() != null ? request.getSourceSite() : "";
        String siteGuide = switch (site) {
            case "SARAMIN" -> """
                    [사이트 제약]
                    - 자유 양식 텍스트 입력. 글자수 제한 없음.
                    - 1500~2000자 내외로 작성.
                    """;
            case "JOBKOREA" -> """
                    [사이트 제약]
                    - 텍스트 입력. 글자수 제한 없음.
                    - 1500~2000자 내외로 작성.
                    """;
            case "JOBPLANET" -> """
                    [사이트 제약]
                    - 단일 텍스트 입력. 글자수 제한 없음.
                    - 1500~2000자 내외로 작성.
                    """;
            case "LINKAREER" -> """
                    [사이트 제약]
                    - 글자수 제한 1000자 (공백 포함). 반드시 900~1000자 이내. 절대 초과 금지.
                    """;
            default -> "";
        };

        return String.format("""
                당신은 한국 IT 업계 채용 담당자 출신 자소서 컨설턴트입니다.
                채용 담당자는 수백~수천 명의 서류를 정독이 아닌 스캐닝합니다.
                담당자의 시간을 절약해주는 '전략적 가독성'을 갖춘 자기소개서를 작성해주세요.

                ============================================================
                작성 원칙
                ============================================================
                - 순수 텍스트만. 마크다운(*, #, ` 등), HTML 태그 절대 금지.
                - "~합니다/~했습니다" 존댓말 통일. 자연스럽고 진정성 있는 톤.
                - AI스러운 표현 금지: "판단했습니다"→"생각했습니다/~라고 봤습니다", "적용했습니다"→"넣었습니다/썼습니다", "구현했습니다"→"만들었습니다", "확보했습니다"→"갖췄습니다", "활용했습니다"→"썼습니다"
                - "열정을 가지고 있습니다", "열심히 하겠습니다" 같은 추상적 표현 금지.
                - 후속 제안("원하시면", "버전", "이어서") 절대 넣지 말 것.
                - GitHub URL, 개인 링크, 이메일 등 개인정보를 본문에 절대 포함하지 말 것.
                - 학력, 학교명, 대학명, 학점, 졸업 연도, 전공 등 개인 스펙 정보를 자소서에 절대 넣지 말 것. "XX대학교", "졸업 이후" 같은 표현이 나오면 실패한 것이다. 이력서에서 확인할 내용이다.

                ============================================================
                소제목 법칙 (3줄 제어)
                ============================================================
                - 각 섹션은 500자 내외 문단으로 구성하고, 각 문단에 [소제목]을 달 것.
                - 소제목 = '결과 + 액션' 형태. 소제목만 연결해 읽어도 전체 서사가 완성되어야 함.
                - BAD: [지원동기], [직무역량] (단순 라벨)
                - GOOD: [4개 사이트 크롤링 자동화로 데이터 파이프라인 안정성 체득], [동시 접속 300명 처리: HikariCP + Redis 캐싱 설계]

                ============================================================
                JD 매핑 (채용 공고 1:1 대응)
                ============================================================
                - 자소서의 모든 내용은 아래 [채용 공고]의 자격요건/우대사항에 1:1로 대응해야 함.
                - 공고에 없는 기술이나 경험은 쓰지 말 것. 공고 키워드와 직접 연결되는 내용만.
                - 공고의 핵심 키워드를 추출하고, 각 키워드에 대응하는 내 경험을 배치할 것.

                ============================================================
                기승전결 스토리 구조
                ============================================================
                - 모든 에피소드는 단순 3단(상황→행동→결과)이 아닌 기승전결 흐름을 따를 것.
                - 기(起): 문제를 처음 인식한 상황과 배경
                - 승(承): 첫 번째 시도와 그 한계/실패
                - 전(轉): 새로운 접근법 발견, 관점 전환의 계기
                - 결(結): 최종 해결과 정량적 성과 + 교훈
                - 이 흐름이 '이 사람은 생각하는 개발자'라는 인상을 만듦.

                ============================================================
                핵심: 섹션 간 내용 중복 절대 금지
                ============================================================
                - [나는 이런 개발자입니다]: 가치관과 차별점만. 기술 나열/프로젝트 상세 쓰지 말 것.
                - [지원동기]: 이 회사에 왜 지원하는지만. 서비스 개선 제안 포함. 프로젝트 수치 쓰지 말 것.
                - [프로젝트 경험]: 문제 해결 과정만. 기술 나열이나 지원동기 반복 금지.
                - [협업과 성장]: 소프트스킬과 실패/극복만. 기술적 내용 반복 금지.
                - [입사 후 포부]: 구체적 기여 계획만. 다른 섹션 내용 반복 금지.

                ============================================================
                5개 섹션 작성 가이드
                ============================================================

                [나는 이런 개발자입니다] (3~5문장, 약 500자)
                - 나는 어떤 사람인지를 명쾌하게 한 줄로 정의하고 시작.
                - 개발자가 된 계기, 개발에 대한 가치관, 나만의 차별점.
                - 성격/성향 중 직무에 도움이 되는 것 (꼼꼼함, 끈기, 호기심 등)을 에피소드로 증명.
                - BAD: "성실하고 책임감이 강합니다"
                - GOOD: "저는 '왜 이렇게 동작하지?'라는 질문을 놓지 못하는 개발자입니다. 크롤링 실패율이 40%%일 때 단순히 재시도 로직을 넣는 대신, 브라우저 연결 구조 자체를 파고들어 원인을 찾아냈고, 이 습관이 제가 문제를 해결하는 방식의 핵심입니다."

                [지원동기] (5~7문장, 약 500자)
                - 회사의 구체적 사업/제품과 내 경험의 교집합. (아래 [기업 분석] 참고)
                - "왜 다른 회사가 아니라 이 회사인지" 명확한 이유.
                - 무조건적 찬양 금지. 실제 서비스 사용 경험 → 아쉬운 점(페인포인트) → 내 역량으로 개선할 방안 제시.
                - BAD: "귀사의 비전에 공감하여 지원합니다"
                - BAD: "서비스가 훌륭하여 평소 팬이었습니다" (무색무취)
                - GOOD: "귀사의 XX 플랫폼을 직접 사용하면서, 대량 데이터 조회 시 응답 지연이 체감되는 부분이 있었습니다. 제가 이끼잡 프로젝트에서 Redis 캐싱과 GIN 인덱스로 검색 응답을 3배 개선한 경험이 이 문제에 직접 기여할 수 있다고 생각했습니다."

                [프로젝트 경험] (핵심 2~3개, 각 500자, 기승전결 구조)
                - 각 항목에 [결과+액션 소제목]을 달 것.
                - 기승전결: 문제 인식 → 첫 시도와 실패 → 관점 전환 → 최종 해결 + 정량 성과.
                - 다층적 문제 해결 포함 (문제→해결→새문제→해결).
                - BAD: "크롤링 시스템을 개발했습니다. 여러 기술을 사용했습니다."
                - GOOD: "병렬 크롤링에서 3개 워커가 단일 WebSocket을 공유해 60%% 실패하는 문제가 발생했습니다. 처음엔 단순 재시도 로직을 넣었지만 근본 해결이 아니었습니다. 브라우저 연결 구조를 분석한 끝에 워커별 독립 인스턴스로 분리하여 성공률 100%%를 달성했지만, 메모리가 200MB→500MB로 증가하는 새로운 문제가 나타났습니다. concurrency 설정으로 환경별 균형을 잡을 수 있게 만들었고, 이 경험으로 성능과 리소스 사이의 트레이드오프를 체감했습니다."

                [협업과 성장] (3~5문장, 약 500자)
                - 소프트스킬: 협업, 소통, 갈등 조율, 코드 리뷰, 팀 내 역할 등 구체적 에피소드.
                - 실패/극복 경험: 실패를 인정하고 피드백을 수용하여 개선한 사례.
                - 학습 태도: 새로운 기술을 어떻게 습득하는지, 지속적 학습 방식.
                - BAD: "커뮤니케이션 능력이 뛰어납니다"
                - GOOD: "프로젝트 초기에 API 명세 없이 프론트/백엔드를 동시 개발하다 연동 오류가 반복됐습니다. 이후 Swagger 기반 API 문서를 먼저 작성하고 개발하는 프로세스를 도입했고, 연동 이슈가 대폭 줄었습니다. 이 경험으로 '소통의 구조화'가 협업의 핵심임을 배웠습니다."

                [입사 후 포부] (3~5문장, 약 500자)
                - 단기(입사 1년): 시스템 이해 + 첫 기여 영역
                - 중기(2~3년): 성장 방향 + 팀 기여
                - 회사의 기술 방향/사업과 연결된 구체적 목표.
                - BAD: "열심히 배워서 성장하겠습니다"
                - GOOD: "입사 후 1년간 귀사의 XX 시스템 아키텍처를 깊이 이해하고, 이후 제가 경험한 비동기 처리와 AI 연동 기술을 활용해 YY 영역의 자동화에 기여하고 싶습니다. 특히 제가 구현한 WebSocket 기반 실시간 알림 경험이 귀사의 ZZ 서비스 고도화에 접점이 있다고 생각합니다."

                %s

                [내 프로필]
                %s

                [채용 공고]
                %s

                [기업 분석]
                %s

                [관련 프로젝트 상세]
                %s
                """, siteGuide, request.getUserProfile(), request.getJobDescription(),
                request.getCompanyInfo(), request.getMatchedProjects());
    }

    private String buildCustomCoverLetterPrompt(AiGenerationRequest request) {
        String sections = request.getCustomSections() != null ? request.getCustomSections() : "[]";
        String additional = request.getAdditionalRequest() != null ? request.getAdditionalRequest() : "";

        return String.format("""
                당신은 한국 IT 업계 자소서 컨설턴트입니다.
                아래 문항별로 각각 자기소개서를 작성하세요.

                ============================================================
                작성 규칙
                ============================================================
                - 순수 텍스트만. 마크다운, HTML 태그 절대 금지.
                - "~합니다/~했습니다" 존댓말 통일. 자연스럽고 진정성 있는 톤.
                - 추상적 표현("열정", "열심히") 금지.
                - 후속 제안 절대 넣지 말 것.
                - GitHub URL, 개인 링크, 이메일 등 개인정보를 본문에 절대 포함하지 말 것.
                - 에피소드는 "상황→행동→결과→교훈" 순서.
                - 가능한 곳에 정량적 성과(숫자, 전후 비교)를 넣을 것.
                - 시행착오, 트레이드오프를 솔직하게 포함할 것.

                ============================================================
                프로젝트 분배 규칙
                ============================================================
                - 프로젝트가 여러 개면, 각 문항에 가장 적합한 프로젝트를 골라서 사용할 것.
                - 같은 프로젝트를 여러 문항에서 반복하지 말 것. 문항마다 다른 프로젝트를 배정.
                - 지원동기/입사 후 포부 → 회사 사업과 접점이 있는 프로젝트
                - 직무역량/기술경험 → 공고 기술스택과 매칭되는 프로젝트
                - 문제해결/도전경험 → 트러블슈팅, 성능개선, 장애대응 경험이 있는 프로젝트
                - 성장/학습경험 → 새 기술을 배우거나 시행착오를 겪은 프로젝트
                - 프로젝트가 1개뿐이면 각 문항에서 다른 측면(기술선택, 문제해결, 성과 등)을 강조.

                ============================================================
                문항별 지시
                ============================================================
                아래 JSON의 각 문항에 대해 title과 rule을 읽고, 해당 규칙에 맞춰 작성하세요.
                각 문항의 내용이 서로 겹치지 않도록 할 것.

                %s

                ============================================================
                추가 요청
                ============================================================
                %s

                ============================================================
                출력 형식 (반드시 이 JSON 형식으로만 출력)
                ============================================================
                [{"title":"문항제목1","content":"작성된 내용1"},{"title":"문항제목2","content":"작성된 내용2"}]

                JSON 외 다른 텍스트는 절대 포함하지 마세요.

                [내 프로필]
                %s

                [채용 공고]
                %s

                [기업 분석]
                %s

                [관련 프로젝트 상세]
                %s
                """, sections, additional.isEmpty() ? "없음" : additional,
                request.getUserProfile(), request.getJobDescription(),
                request.getCompanyInfo(), request.getMatchedProjects());
    }

    private String buildPortfolioPrompt(AiGenerationRequest request) {
        String site = request.getSourceSite() != null ? request.getSourceSite() : "";
        String siteGuide = switch (site) {
            case "SARAMIN", "JOBKOREA" -> """
                    [사이트 제약]
                    - 텍스트 기반 입력. 글자수 제한 없음.
                    """;
            case "LINKAREER" -> """
                    [사이트 제약]
                    - 글자수 제한 있을 수 있음. 간결하게 작성.
                    """;
            default -> "";
        };

        return String.format("""
                당신은 한국 IT 업계 시니어 면접관입니다. 개발자 포트폴리오를 작성해주세요.
                포트폴리오는 '무엇을 만들었는가'가 아니라 '어떻게 문제를 해결했는가'를 증명하는 문서입니다.
                기능 구현은 기본 중의 기본이며, 면접관의 흥미를 끄는 것은 지원자의 '생각의 흐름(Thought Flow)'입니다.

                ============================================================
                어투 규칙
                ============================================================
                - "~합니다/~했습니다" 존댓말 통일.
                - AI스러운 딱딱한 표현 금지:
                  "판단했습니다"→"생각해서/~라고 봤습니다", "적용했습니다"→"넣었습니다/썼습니다",
                  "구현했습니다"→"만들었습니다", "확보했습니다"→"갖췄습니다", "활용했습니다"→"썼습니다",
                  "도입했습니다"→"넣었습니다", "수행/진행했습니다"→"했습니다"
                - "~니다." 연속 3번 금지. 연결어를 다양하게 사용: "그런데", "다만", "결국", "처음에는" 등.
                  "그래서"는 전체 포트폴리오에서 최대 2번만. 같은 연결어 연속 사용 금지.
                - "~인데,", "~지만,", "~라서" 접속사로 문장을 이어붙여도 좋다.
                - "대신"으로 시작하는 단점 서술 금지. 결과는 긍정적 성과 중심으로 마무리하라.
                  BAD: "성공률을 높였습니다. 대신 메모리가 500MB까지 증가했습니다."
                  GOOD: "성공률을 높였고, concurrency 조정으로 메모리도 안정 범위에서 관리했습니다."

                ============================================================
                분량 제한 (절대 준수)
                ============================================================
                - 전체 포트폴리오는 5,000~6,500자 이내. 절대 7,000자를 넘기지 마라.
                - 섹션 1(동기/개요): 400자 이내로 압축
                - 섹션 2(기술적 의사결정): 항목 수 제한 없음. 항목당 3~5문장. 왜 그 기술을 선택했는지 근거까지.
                - 섹션 3(문제 해결)이 포트폴리오의 핵심. 항목 수 제한 없음. 항목당 4~6문장으로 충분히 서술. 시니어 엔지니어가 기술 블로그에 쓰는 밀도와 깊이로.
                - 섹션 4(협업): 400자 이내
                - 섹션 5(직무 역량): 300자 이내
                - 섹션 3에 가장 많은 분량을 할당하되, 나머지 섹션도 내용이 빈약하지 않게 작성.
                - 핵심: 항목의 밀도가 중요하지만, 충분한 설명이 뒷받침되어야 설득력이 생긴다.

                ============================================================
                작성 원칙
                ============================================================
                - 마크다운, HTML 태그 절대 금지. 순수 텍스트만.
                - 후속 제안 문구 절대 넣지 마세요.
                - "채용 공고가 제공되지 않아", "공고 미제공으로", "아래는 프로젝트 정보만 기준으로" 같은 메타 설명 절대 금지. 공고 없이도 프로젝트 데이터만으로 완결된 포트폴리오를 작성하라.
                - GitHub URL, 개인 링크, 이메일, 학력, 학교명, 학점 등 개인정보를 본문에 절대 포함하지 마세요. "https://github.com/..." 형태의 주소가 본문에 나오면 실패한 것이다.
                - 채용 공고의 자격요건/기술스택에 1:1 매핑하여 프로젝트 경험을 연결.
                - 공고에 없는 기술/경험은 쓰지 말 것.
                - [관련 프로젝트]에 나온 기술명, 라이브러리명, 설정값, 수치를 그대로 사용할 것. 절대 추상화하지 마라.
                  BAD: "소스별 수집기를 분리" / "인증을 통합" / "캐시를 적용"
                  GOOD: "Playwright + Cheerio 기반 크롤러를 5개 소스별로 분리" / "Passport + OAuth2로 Google/Kakao/Apple/Toss/Naver 5개 로그인을 단일 API로 통합" / "Redis 5분 TTL 캐시 적용"
                - 프로젝트 데이터에 구체적 기술명이 있는데 추상적으로 바꾸면 실패한 것이다.
                - "관리", "조정", "개선", "구성", "설계" 같은 포장 단어만으로 끝내지 마라. 반드시 기술명과 함께 쓸 것.

                ============================================================
                가독성 포맷 규칙
                ============================================================
                - 섹션 제목은 성과 중심 타이틀로 작성 (How + Result).
                  BAD: "1. 프로젝트 개요"
                  GOOD: "1. 4개 채용 사이트 크롤링 자동화로 취업 준비 과정 전체를 플랫폼화"
                - 섹션 제목 바로 아래에 구분선 "───────────────────" 넣기.
                - 소제목이나 핵심 키워드는 【】로 감싸서 강조.
                - 문제 해결 항목은 ▶ 로 시작.
                - 성과 수치 항목은 ✔ 로 시작.
                - 섹션 사이에 빈 줄 2개로 구분.
                - 핵심 수치는 ( ) 안에 넣어 눈에 띄게. 예: (40%% → 100%%)
                - 섹션 안에 하위 소제목이 여러 개면 "3-1.", "3-2.", "3-3." 처럼 상위 번호에 하위 번호를 붙여라.
                - 모든 소제목에는 반드시 번호를 붙여라. 번호 없는 소제목은 절대 금지.
                  BAD: "실시간보다 60초 주기 갱신을 선택한 이유" (번호 없음)
                  GOOD: "2-4. 실시간보다 60초 주기 갱신을 선택한 이유"
                  BAD: "5개 소스 수집 중단을 AI 대체 경로로 흡수" (번호 없음)
                  GOOD: "4. 5개 소스 수집 중단을 AI 대체 경로로 흡수"
                - 기술 스택을 별도 섹션으로 나열하지 마라. 본문에서 자연스럽게 언급할 것.

                ============================================================
                섹션 3 작성법 (문제 해결 — 가장 중요한 섹션)
                ============================================================
                각 항목은 아래 4단 구조로 작성:
                1단: 소제목 — "3-N. ~했습니다" 형태의 완결된 문장
                2단: 문제 상황 — 1~2문장으로 어떤 문제가 있었는지
                3단: 해결 방법 — 구체적 기술명과 함께 무엇을 했는지
                4단: 결과 — 수치 포함. 긍정적 성과 중심으로 마무리

                === 기승전결 흐름 + 문장 다양성 (딱딱한 나열 금지) ===
                각 문제 해결 항목은 기승전결 흐름을 따라라:
                - 기(起): 당시 상황을 짧게 세팅. "초기에는 ~했고", "~하는 구조였습니다" — 독자를 그 상황에 놓기
                - 승(承): 문제가 드러나는 순간. "그런데 ~하면", "~이 늘어나자" — 왜 문제인지 체감시키기
                - 전(轉): 해결의 전환점. "이를 위해"가 아니라 선택의 이유. "~보다 ~이 더 적합하다고 봤습니다", "처음에는 ~를 시도했지만"
                - 결(結): 수치 결과 중심으로 긍정적 마무리. 단점/부작용을 굳이 노출하지 마라.

                이 4단계가 자연스러운 한 문단처럼 이어져야 한다. 불릿이 아니라 이야기를 읽는 느낌.

                문장 다양성:
                - 모든 항목이 "~문제가 있었습니다 → ~했습니다 → ~줄였습니다" 같은 동일 리듬이면 실패.
                - 때로는 결과부터 시작하고 원인을 뒤에 붙여라. 때로는 시행착오를 먼저 보여줘라.
                - 연속된 3개 항목이 같은 문장 구조로 시작하면 안 된다.
                - 시니어 개발자가 기술 블로그에 쓰는 글처럼 자연스럽고 읽기 편한 흐름이 목표.

                === 수치 표기 규칙 ===
                - 수치를 (괄호) 안에 따로 넣지 마라. 문장 안에 자연스럽게 녹여라.
                  BAD: "수집 실패율은 (30%% → 0%%) 수준으로 낮췄고"
                  GOOD: "수집 실패율을 단일 경로 기준 약 30%%에서 0%%로 낮췄고"
                - "체감상", "드묾" 같은 주관적 표현 금지. 수치가 없으면 차라리 안 쓰는 게 나음
                - 섹션 끝에 요약 불릿을 따로 넣지 마라. 각 항목 안에서 완결시킬 것

                레퍼런스 예시 (반드시 이 톤과 밀도를 따라라):

                3-1. 수집 실패가 관리자 등록 중단으로 이어지지 않게 했습니다

                초기에는 특정 소스 파싱이 실패하면 운영자가 등록 흐름 전체를 다시 시작해야 했고, 단일 실패가 전체 작업 중단으로 이어졌습니다.

                YSDL, IMDb, 일본 촬영지, 나무위키를 소스별 경로로 분리하고, Cheerio와 Playwright 실패 시 OpenClaw HTTP API와 Google Gemini 2.5-flash 대체 경로를 추가했습니다.

                수집 실패율을 단일 경로 기준 약 30%%에서 0%% 수준으로 낮췄습니다.

                3-2. 대량 위치 보강 중 중복 실행과 필드 덮어쓰기를 제어했습니다

                여러 locationId를 한 번에 보강할 때 이전 작업이 남아 있으면 같은 대상이 중복 처리되거나 늦게 끝난 작업이 최신 값을 덮어쓰는 문제가 있었습니다.

                관리자 API에 batch-enrich, 청크 단위 병렬 처리, abort API, 이전 태스크 자동 취소를 구현했습니다.

                위치 보강 시간을 수작업 기준 약 10분/건에서 15초/건으로 줄였습니다.

                ============================================================
                핵심: 생각의 흐름(Thought Flow) 필수
                ============================================================
                - 한 번에 성공한 사례보다, 시행착오를 거쳐 최적해를 찾는 과정이 논리적 사고력을 증명함.
                - 특정 기술을 선택한 이유, 대안과 비교한 근거, 그 선택이 가져온 변화를 서술.
                - 건조한 "~적용 → ~달성" 패턴 금지. 실패/재시도/관점 전환 과정을 솔직하게 포함.
                - "면접관이 이걸 보고 '이 사람은 생각하는 개발자'라고 느끼는가?" — 아니면 다시 쓸 것.

                ============================================================
                섹션 간 내용 중복 절대 금지 (가장 중요한 규칙)
                ============================================================
                섹션 2(기술적 의사결정)와 섹션 3(문제 해결 과정)이 같은 주제를 다루면 실패한 것이다.

                === 중복 판별 기준 ===
                섹션 2에서 "크롤러를 왜 분리했는가"를 썼으면, 섹션 3에서 "크롤러 수집 중단 해결"을 다시 쓰면 안 된다.
                섹션 2에서 "3단계 파이프라인 분리 이유"를 썼으면, 섹션 3에서 "수작업 누락을 파이프라인으로 해결"을 다시 쓰면 안 된다.
                섹션 2에서 "60초 주기 선택 이유"를 썼으면, 섹션 3에서 "60초 주기로 고정"을 다시 쓰면 안 된다.
                같은 기능을 "설계 관점"과 "운영 관점"으로 두 번 쓰는 것도 중복이다.

                === 섹션 3에 넣어야 하는 것 ===
                섹션 2에서 전혀 언급하지 않은, 런타임에서 터진 예상 못한 버그, 장애, 성능 병목, 데이터 정합성 오류만.
                프로젝트 데이터에 트러블슈팅 기록이 있으면 반드시 활용하라.

                각 섹션의 역할:
                - [프로젝트 동기와 개요]: 왜 만들었는지 + 한 줄 요약만. 기술/수치 쓰지 말 것.
                - [기술적 의사결정]: "왜 A 대신 B를 선택했는가" 선택 이유만. 구현 디테일/결과 수치 쓰지 말 것.
                - [문제 해결 과정]: 섹션 2에서 다룬 주제 재사용 금지. **운영 중 발생한 실제 장애, 성능 병목, 예상 못한 버그** 만. 3줄 기술법(문제 수치→해결→결과 수치+트레이드오프). 결과에 정량적 성과 수치 반드시 포함.
                - [협업과 소프트스킬]: 추상적 원칙 금지. **구체적 에피소드** 1~2개. "언제, 어떤 상황에서, 무엇을 했고, 결과가 어땠다"

                === 문장 시작 패턴 금지 (절대 준수) ===
                아래 패턴으로 시작하는 문장은 무조건 다시 써라:
                - "그 결과 ~" 금지
                - "결과적으로 ~" 금지
                - "이를 통해 ~" 금지
                - "이에 따라 ~" 금지
                대신 **수치 + 트레이드오프**로 바로 시작:
                - BAD: "그 결과 운영을 지속할 수 있는 구조를 구축했습니다."
                - GOOD: "수집 실패율 0%%를 유지했지만, AI 대체 비용이 월 $2~3 추가되는 트레이드오프를 감수했습니다."
                - BAD: "이에 따라 위치 보강을 단일 요청으로 처리하지 않고 분리했습니다."
                - GOOD: "위치 보강을 Ground Truth → Visitor Info → Content Fields 3단계로 분리해 단계별 실패를 격리했습니다."

                ============================================================
                항목 선별 기준
                ============================================================
                - 제외 대상: 내부 리팩토링, 당연한 설정 변경, 외부에서 임팩트 없는 것.
                - 포함 대상: 수치가 드라마틱하게 개선된 것, 외부 시스템 연동, 사용자 경험에 직접 영향을 준 것.
                - 선별 기준: "면접관이 이걸 보고 '오' 하겠는가?" — 아니면 빼라.

                %s

                [포트폴리오 구성 — 5개 섹션]
                1. 프로젝트 동기와 개요 — 왜 만들었는지(어떤 문제를 해결하고 싶었는지) + 한 줄 요약. 개인적 동기를 솔직하게.
                2. 기술적 의사결정 — 소제목별로 왜 이 기술/아키텍처를 선택했는지. 대안 비교 포함. 수치 없이 선택 이유만.
                3. 문제 해결 과정 — 가장 중요한 섹션. 항목 수 제한 없음. 3줄 기술법(문제→기술+논리→결과+인사이트). 시행착오와 트레이드오프 포함. 결과에 정량적 수치 필수.
                  결과 수치 형식:
                  GOOD: "수집 실패율: 단일 파서 30%% 실패 → 소스별 분리 후 0%%"
                  GOOD: "위치 보강 소요: 수작업 10분/건 → batch-enrich 15초/건"
                  GOOD: "장애 감지: 수동 확인 평균 30분 → 헬스체크 자동 감지 60초"
                  BAD: "5개 수집 경로를 분리했습니다" (이건 구현이지 성과가 아님)
                  BAD: "30개 이상 필드를 편집할 수 있게 했습니다" (이건 스펙이지 성과가 아님)
                  수치가 프로젝트 데이터에 없으면 합리적으로 추정하되 "약", "추정" 표기할 것.
                4. 협업과 소프트스킬 — 팀 내 소통/협업/코드리뷰/학습 방식. 구체적 에피소드 1~2개.
                5. 직무 연관 역량 — 이 프로젝트에서 증명한 개발자 핵심 역량. 공고가 없으면 일반적인 개발 직무 기준으로. 2~3문장.

                [내 프로필]
                %s

                [채용 공고]
                %s

                [관련 프로젝트]
                %s
                """, siteGuide, request.getUserProfile(), request.getJobDescription(),
                request.getMatchedProjects());
    }

    private String buildCustomPortfolioPrompt(AiGenerationRequest request) {
        String sections = request.getCustomSections() != null ? request.getCustomSections() : "[]";
        String additional = request.getAdditionalRequest() != null ? request.getAdditionalRequest() : "";

        return String.format("""
                당신은 한국 IT 업계 채용 전문가입니다. 아래 문항별로 포트폴리오를 작성하세요.

                ============================================================
                작성 규칙
                ============================================================
                - 순수 텍스트만. 마크다운, HTML 태그 절대 금지.
                - "~합니다/~했습니다" 존댓말 통일. 자연스럽고 진정성 있는 톤.
                - 추상적 표현 금지. 구체적 기술명과 수치를 사용.
                - 후속 제안 절대 넣지 말 것.
                - GitHub URL, 개인 링크, 이메일 등 개인정보를 본문에 절대 포함하지 말 것.
                - 문제 해결 과정은 디버깅, 시행착오, 트레이드오프를 솔직하게 포함.
                  건조한 "~적용 → ~달성" 패턴 금지. 구어체와 체감 표현 사용.
                - 가능한 곳에 정량적 성과(숫자, 전후 비교)를 넣을 것.

                ============================================================
                가독성 포맷 규칙
                ============================================================
                - 소제목이나 핵심 키워드는 【】로 감싸서 강조. 예: 【아키텍처 선택 이유】
                - 문제 해결 항목은 ▶ 로 시작. 레퍼런스 형태: "문제(수치): 원인→해결→결과"
                - 성과 수치 항목은 ✔ 로 시작.
                - 핵심 수치는 ( ) 안에 넣어 눈에 띄게. 예: (40%% → 100%%)

                ============================================================
                항목 선별 기준
                ============================================================
                - 외부에서 봤을 때 인상적이지 않은 항목은 넣지 말 것.
                - 제외 대상: 내부 리팩토링, 저장소/캐시 전환, 점수가 낮아지는 보정, 당연한 설정 변경
                - 포함 대상: 성능 수치가 드라마틱하게 개선된 것, 외부 시스템 연동, 사용자 경험에 직접 영향을 준 것
                - 선별 기준: "면접관이 이걸 보고 '오' 하겠는가?" — 아니면 빼라.

                ============================================================
                프로젝트 분배 규칙
                ============================================================
                - 프로젝트가 여러 개면, 각 문항에 가장 적합한 프로젝트를 골라서 사용할 것.
                - 같은 프로젝트를 여러 문항에서 반복하지 말 것. 문항마다 다른 프로젝트를 배정.
                - 프로젝트 개요 → 가장 대표적인 프로젝트
                - 기술적 의사결정 → 아키텍처 선택이 돋보이는 프로젝트
                - 문제 해결 → 트러블슈팅, 성능개선 경험이 있는 프로젝트
                - 성과/수치 → 정량적 결과가 명확한 프로젝트
                - 프로젝트가 1개뿐이면 각 문항에서 다른 측면(기술선택, 문제해결, 성과 등)을 강조.

                ============================================================
                문항별 지시
                ============================================================
                아래 JSON의 각 문항에 대해 title과 rule을 읽고, 해당 규칙에 맞춰 작성하세요.
                각 문항의 내용이 서로 겹치지 않도록 할 것.

                %s

                ============================================================
                추가 요청
                ============================================================
                %s

                ============================================================
                출력 형식 (반드시 이 JSON 형식으로만 출력)
                ============================================================
                [{"title":"문항제목1","content":"작성된 내용1"},{"title":"문항제목2","content":"작성된 내용2"}]

                JSON 외 다른 텍스트는 절대 포함하지 마세요.

                [내 프로필]
                %s

                [채용 공고]
                %s

                [관련 프로젝트 상세]
                %s
                """, sections, additional.isEmpty() ? "없음" : additional,
                request.getUserProfile(), request.getJobDescription(),
                request.getMatchedProjects());
    }

    private String callHttpApi(String prompt) throws Exception {
        return callHttpApi(prompt, null);
    }

    private String callHttpApi(String prompt, List<String> imageUrls) throws Exception {
        if (!aiSemaphore.tryAcquire(30, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new RuntimeException("AI 요청이 많아 대기 시간을 초과했습니다. 잠시 후 다시 시도해주세요.");
        }
        try {
            return callHttpApiInternal(prompt, imageUrls);
        } finally {
            aiSemaphore.release();
        }
    }

    private String callHttpApiInternal(String prompt, List<String> imageUrls) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        // 멀티모달: 텍스트 + 이미지
        List<Map<String, Object>> contentParts = new java.util.ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));

        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && url.startsWith("http")) {
                    contentParts.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", url)));
                }
            }
        }

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", imageUrls != null && !imageUrls.isEmpty() ? contentParts : prompt);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", "openai-codex/gpt-5.4");
        body.put("messages", List.of(message));
        body.put("temperature", aiTemperature);
        body.put("max_tokens", 8192);
        body.put("stream", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("OpenClaw HTTP API 요청: {}", apiUrl);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class);

        if (response.getBody() == null) {
            throw new RuntimeException("API 응답이 비어있습니다.");
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.path("message");
            if (!messageNode.isMissingNode()) {
                return messageNode.path("content").asText("");
            }
        }

        throw new RuntimeException("OpenAI 호환 JSON 파싱 실패: " + response.getBody());
    }
}
