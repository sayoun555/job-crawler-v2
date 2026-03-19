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
                    다음 프로젝트를 포트폴리오용으로 정리해주세요.
                    마크다운 형식으로 가독성 좋게 작성하세요. (##, -, **볼드**, `코드` 등 활용)
                    마지막에 후속 제안 문구는 넣지 마세요.

                    [프로젝트 설명]
                    %s

                    [기술 스택]
                    %s

                    다음 형식으로 정리:
                    ## 프로젝트 개요
                    ## 주요 기능
                    ## 사용 기술 및 역할
                    ## 성과 및 배운 점
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
                    너는 10년차 시니어 개발자이다.
                    GitHub 레포지토리의 소스 코드, 빌드 파일, 설정 파일, README, docs 폴더 속 .md 파일들을 꼼꼼히 읽고
                    포트폴리오용 프로젝트 정보를 정확하게 추출하라.

                    반드시 아래 JSON 형식으로만 응답하라. JSON 외 다른 텍스트는 절대 포함하지 마라.

                    {
                      "projectName": "서비스명 (레포 slug 아닌 실제 서비스명. README나 코드에서 확인)",
                      "description": "4~6문장의 포트폴리오용 프로젝트 설명",
                      "techStack": "쉼표로 구분된 기술 스택",
                      "keyFeatures": ["기능1", "기능2", ...],
                      "architecture": "아키텍처 설명 2~3문장"
                    }

                    === description 작성 규칙 ===
                    - 첫 문장: 이 프로젝트가 해결하는 문제와 대상 사용자
                    - 중간: 핵심 기능을 구체적으로 서술 (크롤링이면 어떤 사이트를, AI면 어떤 기능을)
                    - 마지막: 기술적 차별점이나 아키텍처 특징
                    - 채용 담당자가 "이 사람이 뭘 만들었는지" 바로 이해할 수 있도록 구체적으로 작성
                    - 추상적 표현 금지. "자동화합니다" 대신 "Playwright로 4개 채용 사이트를 병렬 크롤링합니다" 같이 구체적으로

                    === techStack 추출 규칙 ===
                    - build.gradle의 implementation/runtimeOnly 의존성에서 추출
                    - package.json의 dependencies에서 추출
                    - application.properties에서 사용하는 DB, 캐시 등 확인
                    - docker-compose.yml, nginx.conf 등 인프라 설정에서 확인
                    - 버전 포함 (예: "Java 21", "Spring Boot 3.4", "Next.js 16")
                    - 테스트/빌드 전용 도구(JUnit, ESLint 등) 제외

                    === keyFeatures 추출 규칙 ===
                    - Controller, Service 파일명과 README에서 실제 구현된 기능만 추출
                    - "채용 공고 크롤링" 같은 일반적 표현이 아닌 "사람인/잡플래닛/잡코리아/링커리어 4개 사이트 병렬 크롤링 및 스케줄링" 같이 구체적으로
                    - 각 기능이 어떻게 동작하는지 한 줄로 설명
                    - 5~8개 추출

                    === architecture 작성 규칙 ===
                    - 소스 패키지 구조(api/application/domain/infrastructure)에서 아키텍처 패턴 파악
                    - 프론트엔드/백엔드/DB/캐시/외부연동 구성 설명
                    - 사용된 디자인 패턴이 보이면 언급 (Strategy, Builder 등)

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
                당신은 한국 IT 업계 채용 전문가입니다. 백엔드 개발자 직무에 최적화된 자기소개서를 작성해주세요.

                [작성 원칙]
                - 반드시 순수 텍스트로만 작성. 마크다운(*, #, ` 등), HTML 태그 절대 금지.
                - STAR 기법(상황-과제-행동-결과)을 활용하여 경험을 구체적으로 서술.
                - 채용 공고의 자격요건/우대사항에 명시된 기술과 역량에 맞춰 내 경험을 연결.
                - "열심히 하겠습니다" 같은 추상적 표현 대신 정량적 성과(퍼센트, 수치)를 포함.
                - 채용 공고에 전형 절차나 지원 양식 요구사항이 있으면 그 형식에 맞춰 작성.
                - AI가 쓴 티가 나지 않도록 자연스럽고 진정성 있는 톤으로 작성.
                - 후속 제안 문구("원하시면", "버전", "이어서" 등) 절대 넣지 마세요.

                %s

                [자소서 구성]
                1. 지원동기 - 이 회사의 기술/서비스/비전과 내 경험의 접점을 구체적으로
                2. 직무 역량 - 채용 공고의 자격요건에 대응하는 내 기술 경험 (STAR 기법 적용)
                3. 프로젝트 경험 - 문제 정의 → 기술적 선택 → 구현 과정 → 성과 수치
                4. 성장 계획 - 입사 후 단기/중기 목표와 팀 기여 방향

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
                - 단순 기능 나열이 아니라 "왜 이 기술을 선택했고, 어떤 문제를 해결했는지" 중심으로 서술.
                - 성과는 수치로 표현 (응답속도 개선율, 처리량 등).
                - 후속 제안 문구 절대 넣지 마세요.

                %s

                [포트폴리오 구성 - 프로젝트별]
                1. 프로젝트 개요 (한 줄 요약)
                2. 기술적 의사결정 (왜 이 기술/아키텍처를 선택했는지)
                3. 핵심 구현 내용 (본인이 직접 한 것)
                4. 문제 해결 과정 (마주한 기술적 문제 → 분석 → 해결)
                5. 성과 및 수치

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
