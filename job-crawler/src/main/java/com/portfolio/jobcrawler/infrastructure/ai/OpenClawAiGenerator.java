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

    // AI API 동시 요청 제한 (서버 과부하 방지)
    private static final int MAX_CONCURRENT_AI_REQUESTS = 5;
    private final java.util.concurrent.Semaphore aiSemaphore = new java.util.concurrent.Semaphore(MAX_CONCURRENT_AI_REQUESTS);

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        try {
            String prompt = buildPrompt(request);
            String responseText = callHttpApi(prompt);
            return AiGenerationResult.success(responseText);
        } catch (Exception e) {
            log.error("AI 생성 실패: {}", e.getMessage());
            return AiGenerationResult.failure(e.getMessage());
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

            case PORTFOLIO -> buildPortfolioPrompt(request);

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
                      "challenges": ["[S] 상황 [T] 과제 [A] 행동 [R] 결과", ...]
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
                    - 기능1: 무엇을 + 어떻게 + 정량적 결과\\n
                    - 기능2: 같은 구조\\n
                    (5~7개)\\n
                    \\n
                    [성과]\\n
                    - 반드시 정량적 수치 포함. docs/ 블로그에서 수치를 찾아 사용\\n
                    - "확장성 확보" 같은 추상 표현 절대 금지\\n

                    === 구현 항목 작성 핵심 규칙 (가장 중요) ===
                    레퍼런스 형태를 따라라:
                    - BAD: "Playwright 기반 크롤러를 구축하고 사이트별 DOM 파서 분리 구조를 적용해 4개 채용 사이트를 동시 수집하도록 구현했습니다."
                    - GOOD: "병렬 크롤링 성공률 개선(40%%→100%%): 3개 워커 스레드가 단일 WebSocket을 공유해 상세 페이지 60%% 실패 발생. 스레드별 독립 Playwright 인스턴스로 분리하여 성공률 100%% 달성, 메모리 300MB 추가로 처리 시간은 90초 유지."

                    즉, 각 항목을 아래 구조로 작성:
                    **항목명(수치):** 문제 상황 → 원인 → 해결 방법 → 정량적 결과

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
                    [사람인 자소서 양식 가이드]
                    - 사람인 입사지원은 자유 양식 자기소개서를 텍스트로 입력하는 방식.
                    - 글자 수 제한이 있을 수 있으므로 핵심 위주로 1500~2000자 내외로 작성.
                    - 소제목을 활용하여 가독성을 높일 것 (예: [지원동기], [직무역량], [프로젝트 경험]).
                    """;
            case "JOBKOREA" -> """
                    [잡코리아 자소서 양식 가이드]
                    - 잡코리아 즉시지원은 텍스트 입력 필드에 자기소개서를 작성하는 방식.
                    - 채용 공고에 별도 문항이 있으면 해당 문항에 맞춰 작성 (상세내용에서 확인).
                    - 간결하고 핵심 위주로 1500~2000자 내외로 작성.
                    """;
            case "JOBPLANET" -> """
                    [잡플래닛 자소서 양식 가이드]
                    - 잡플래닛은 합격보상 제도가 있어 기업 문화/비전과의 연결을 강조할 것.
                    - 자유 양식 텍스트 입력. 잡플래닛 기업 리뷰 정보를 활용하여 기업 이해도를 보여줄 것.
                    - 1500~2000자 내외.
                    """;
            case "LINKAREER" -> """
                    [링커리어 자소서 양식 가이드]
                    - 링커리어는 대학생/신입 대상 플랫폼으로, 인턴/신입 지원에 특화.
                    - 학교 활동, 동아리, 공모전, 프로젝트 경험을 적극 활용.
                    - 성장 가능성과 학습 의지를 강조할 것.
                    - 1000~1500자 내외로 간결하게.
                    """;
            default -> "";
        };

        return String.format("""
                당신은 한국 IT 업계 채용 담당자 출신 자소서 컨설턴트입니다.
                채용 공고의 자격요건과 내 프로필/프로젝트를 분석해서, 채용 담당자가 "면접 보고 싶다"고 느낄 수준의 자기소개서를 작성해주세요.

                ============================================================
                작성 원칙
                ============================================================
                - 순수 텍스트만. 마크다운(*, #, ` 등), HTML 태그 절대 금지.
                - "~합니다/~했습니다" 존댓말 통일. 자연스럽고 진정성 있는 톤.
                - "열정을 가지고 있습니다", "열심히 하겠습니다" 같은 추상적 표현 금지.
                - 후속 제안("원하시면", "버전", "이어서") 절대 넣지 말 것.
                - 각 섹션은 1500~2000자 내외. 전체 합계 3000~4000자 이내.

                ============================================================
                핵심: 섹션 간 내용 중복 절대 금지
                ============================================================
                - [지원동기]: 이 회사 + 이 직무에 왜 지원하는지만. 프로젝트 수치 쓰지 말 것.
                - [직무역량]: 보유 기술과 학습 배경만. 프로젝트 상세 과정 쓰지 말 것.
                - [프로젝트 경험]: 문제 해결 과정만. 기술 나열이나 지원동기 반복 금지.
                - [성장 계획]: 입사 후 계획만. 다른 섹션 내용 반복 금지.

                ============================================================
                각 섹션 작성 가이드
                ============================================================

                [지원동기] (3~5문장)
                - 이 회사의 제품/서비스/기술과 내 경험의 구체적 접점
                - "왜 다른 회사가 아니라 이 회사인지" 명확한 이유
                - BAD: "어린 시절부터 컴퓨터를 좋아했습니다"
                - GOOD: "이 회사의 XX 서비스를 사용하며 YY 문제를 경험했고, 제가 ZZ 프로젝트에서 해결한 방식이 이 직무와 맞닿아 있다고 판단했습니다"

                [직무역량] (5~7문장)
                - 채용 공고 자격요건의 기술에 대응하는 내 학습/경험
                - 비개발 경험이 있으면 직무와 연결 (물류→운영 이슈 대응, 소통 등)
                - 기술 나열이 아니라 "어떤 맥락에서 어떤 기술을 써봤는지"
                - BAD: "Java, Spring Boot, PostgreSQL을 다룰 수 있습니다"
                - GOOD: "Java와 Spring Boot 기반으로 4개 외부 사이트를 연동하는 백엔드를 설계했고, PostgreSQL 커넥션 풀 튜닝과 Redis 캐싱으로 성능을 개선한 경험이 있습니다"

                [프로젝트 경험] (핵심 3~5개, 각 2~3문장 압축)
                - 각 항목: 문제 상황 → 원인 분석 → 해결 방법 → 정량적 결과
                - 한 항목당 2~3문장으로 압축. 장황하게 풀어쓰지 말 것.
                - BAD: "처음에는 3개 워커가 하나의 브라우저를 공유했는데... (4문장)"
                - GOOD: "병렬 크롤링에서 3개 워커가 단일 연결을 공유해 60%% 실패하는 문제가 있었습니다. 워커별 독립 인스턴스로 분리하여 성공률을 40%%에서 100%%로 개선하면서 처리 시간은 90초를 유지했습니다."

                [성장 계획] (3~4문장)
                - 단기: 입사 후 무엇을 먼저 익히고 기여할지
                - 중기: 어떤 역할로 성장하고 싶은지
                - 공고의 직무/기술과 연결된 구체적 목표

                %s

                [내 프로필]
                %s

                [채용 공고]
                %s

                [기업 정보]
                %s

                [관련 프로젝트]
                %s
                """, siteGuide, request.getUserProfile(), request.getJobDescription(),
                request.getCompanyInfo(), request.getMatchedProjects());
    }

    private String buildPortfolioPrompt(AiGenerationRequest request) {
        String site = request.getSourceSite() != null ? request.getSourceSite() : "";
        String siteGuide = switch (site) {
            case "SARAMIN", "JOBKOREA" -> """
                    [포트폴리오 특이사항]
                    - 이 사이트는 텍스트 기반 포트폴리오 입력. GitHub 링크와 핵심 내용 위주로 작성.
                    """;
            case "LINKAREER" -> """
                    [포트폴리오 특이사항]
                    - 링커리어는 대학생/신입 대상. 학습 과정과 성장 스토리를 강조.
                    - 프로젝트 규모보다 문제 해결 과정과 배운 점에 집중.
                    """;
            default -> "";
        };

        return String.format("""
                당신은 한국 IT 업계 채용 전문가입니다. 백엔드 개발자 포트폴리오 텍스트를 작성해주세요.

                [작성 원칙]
                - 반드시 순수 텍스트로만 작성. 마크다운, HTML 태그 절대 금지.
                - 줄바꿈은 빈 줄로만 구분.
                - 채용 공고의 자격요건/기술스택에 맞춰 프로젝트 경험을 연결.
                - 후속 제안 문구 절대 넣지 마세요.

                === 핵심: 섹션 간 내용이 절대 겹치지 않도록 할 것 ===
                - [기술적 의사결정]은 왜 이 기술/구조를 택했는지만. 문제 해결 수치 쓰지 말 것.
                - [핵심 구현 내용]은 직접 만든 기능 목록만. 문제 해결 과정은 여기에 쓰지 말 것.
                - [문제 해결 과정]은 레퍼런스 형태로 압축:
                  각 항목: "문제(수치): 원인 분석 → 해결 방법 → 결과 수치" 한 문단.
                  BAD: 장황하게 풀어쓰기
                  GOOD: "병렬 크롤링 실패율 개선(40%%→100%%): 3개 워커가 단일 WebSocket을 공유해 60%% 실패. 워커별 독립 인스턴스로 분리하여 성공률 100%% 달성, 처리 시간 90초 유지."
                - [성과 및 수치]는 번호 불릿으로 한 줄씩. 줄 사이에 빈 줄 넣지 말 것.
                - [직무 연관 역량]은 공고 요구사항과 내 경험의 연결점. 다른 섹션 내용 반복 금지.

                %s

                [포트폴리오 구성]
                1. 프로젝트 개요 - 한 줄 요약
                2. 기술적 의사결정 - 왜 이 기술/아키텍처를 선택했는지. 수치 없이 판단 근거만.
                3. 핵심 구현 내용 - 직접 구현한 기능 목록. 3~5문장.
                4. 문제 해결 과정 - 레퍼런스 형태로 3~5개. "문제(수치): 원인→해결→결과"
                5. 성과 및 수치 - 불릿 한 줄씩. 빈 줄 없이.
                6. 직무 연관 역량 - 공고 요구사항과 연결. 2~3문장.

                [내 프로필]
                %s

                [채용 공고]
                %s

                [관련 프로젝트]
                %s
                """, siteGuide, request.getUserProfile(), request.getJobDescription(),
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
        body.put("temperature", 0.7);
        body.put("max_tokens", 4096);
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
