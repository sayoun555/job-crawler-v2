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
                    다음 사용자 프로필과 채용 공고를 비교하여 적합률을 0~100 사이 숫자 하나로만 답하세요.
                    숫자만 출력하세요.

                    [사용자 프로필]
                    %s

                    [채용 공고]
                    %s
                    """, userProfile, jobDescription);
            String result = callHttpApi(prompt).trim().replaceAll("[^0-9]", "");
            return result.isEmpty() ? 50 : Math.min(100, Integer.parseInt(result));
        } catch (Exception e) {
            log.error("적합률 계산 실패: {}", e.getMessage());
            return 50;
        }
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
            case COVER_LETTER -> String.format("""
                    당신은 한국 IT 업계 채용 전문가입니다. 백엔드 개발자 직무에 최적화된 자기소개서를 작성해주세요.

                    [작성 원칙]
                    - 반드시 순수 텍스트로만 작성. 마크다운(*, #, ` 등), HTML 태그 절대 금지.
                    - STAR 기법(상황-과제-행동-결과)을 활용하여 경험을 구체적으로 서술.
                    - 채용 공고의 자격요건/우대사항에 명시된 기술과 역량에 맞춰 내 경험을 연결.
                    - "열심히 하겠습니다" 같은 추상적 표현 대신 정량적 성과(퍼센트, 수치)를 포함.
                    - 채용 공고에 전형 절차나 지원 양식 요구사항이 있으면 그 형식에 맞춰 작성.
                    - AI가 쓴 티가 나지 않도록 자연스럽고 진정성 있는 톤으로 작성.
                    - 후속 제안 문구("원하시면", "버전", "이어서" 등) 절대 넣지 마세요.

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
                    """, request.getUserProfile(), request.getJobDescription(),
                    request.getCompanyInfo(), request.getMatchedProjects());

            case PORTFOLIO -> String.format("""
                    당신은 한국 IT 업계 채용 전문가입니다. 백엔드 개발자 포트폴리오 텍스트를 작성해주세요.

                    [작성 원칙]
                    - 반드시 순수 텍스트로만 작성. 마크다운, HTML 태그 절대 금지.
                    - 줄바꿈은 빈 줄로만 구분.
                    - 채용 공고의 자격요건/기술스택에 맞춰 프로젝트 경험을 연결.
                    - 단순 기능 나열이 아니라 "왜 이 기술을 선택했고, 어떤 문제를 해결했는지" 중심으로 서술.
                    - 성과는 수치로 표현 (응답속도 개선율, 처리량 등).
                    - 후속 제안 문구 절대 넣지 마세요.

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
                    """, request.getUserProfile(), request.getJobDescription(),
                    request.getMatchedProjects());

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
                    다음 GitHub 레포지토리 데이터를 분석하여 프로젝트를 정리해주세요.
                    마크다운 형식으로 가독성 좋게 작성하세요. (##, -, **볼드**, `코드` 등 활용)
                    마지막에 후속 제안 문구는 넣지 마세요.

                    [레포지토리 데이터]
                    %s

                    다음 형식으로 정리:
                    ## 프로젝트 개요
                    ## 기술 스택
                    ## 아키텍처 패턴
                    ## 주요 기능
                    ## 특이사항 및 강점
                    """, request.getMatchedProjects());
        };
    }

    private String callHttpApi(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", prompt
        );
        Map<String, Object> body = Map.of(
                "model", "openai-codex/gpt-5.4",
                "messages", List.of(message),
                "temperature", 0.7,
                "stream", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("OpenClaw HTTP API 요청: {}", apiUrl);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

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
