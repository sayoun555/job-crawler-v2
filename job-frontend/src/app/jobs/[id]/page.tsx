"use client";

import { useState, useEffect, use } from "react";
import { jobsApi, aiApi, applicationsApi, templatesApi, JobPosting, Template } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Markdown } from "@/components/ui/markdown";
import DOMPurify from "dompurify";

/** 대기업 자소서 문항 프리셋 (커스텀 자소서용) */
const QUESTION_PRESETS: { name: string; sections: { title: string; rule: string }[] }[] = [
    {
        name: "삼성전자",
        sections: [
            { title: "지원동기 및 포부", rule: "삼성전자를 지원한 이유와 입사 후 이루고 싶은 꿈. 700자 이내" },
            { title: "성장과정", rule: "현재의 자신에게 가장 큰 영향을 끼친 사건, 인물 포함. 1500자 이내" },
            { title: "사회이슈", rule: "최근 사회 이슈 중 하나를 선택하고 자신의 견해. 1000자 이내" },
            { title: "직무역량", rule: "직무 관련 전문지식과 경험. 삼성전자 제품/서비스 사용 경험 기반. 1000자 이내" },
        ],
    },
    {
        name: "현대자동차",
        sections: [
            { title: "지원동기 및 성장", rule: "지원 동기와 입사 후 현대자동차에서 이루고 싶은 성장. 1000자 이내" },
            { title: "직무역량", rule: "지원 분야 가장 중요한 역량 선정, 이유 설명, 키우기 위한 노력. 1000자 이내" },
        ],
    },
    {
        name: "SK하이닉스",
        sections: [
            { title: "직무 관련 경험", rule: "프로젝트/공모전/논문/연구/활동 등. [경험명]/[소속]/[역할] 형식. 1000자 이내" },
            { title: "전문성 노력", rule: "직무 분야 전문성을 키우기 위해 꾸준히 노력한 경험. 600자 이내" },
            { title: "도전과 성취", rule: "도전적인 목표를 세우고 끈질기게 노력한 경험. 600자 이내" },
            { title: "자기표현", rule: "해시태그(#, 최대 2개) 포함. 특별한 가치관, 개성, 강점. 600자 이내" },
        ],
    },
    {
        name: "LG전자",
        sections: [
            { title: "지원동기", rule: "LG전자에 대한 지원동기를 구체적으로. 1000자 이내" },
            { title: "직무 향후 계획", rule: "지원한 직무 관련 향후 계획. 500자 이내" },
        ],
    },
    {
        name: "카카오",
        sections: [
            { title: "문제의 본질", rule: "'Why?'를 놓치지 않고 문제의 본질을 찾는 데 집중했던 경험. 1000자 이내" },
            { title: "끝까지 몰입", rule: "더 완성도를 높이고 싶어 끝까지 몰입했던 경험. 도전과 극복. 1000자 이내" },
            { title: "관심 분야", rule: "Platform/Client/Server/Infra를 관심 순으로 나열, 각 계기/경험. 1000자 이내" },
        ],
    },
    {
        name: "포스코",
        sections: [
            { title: "지원동기 및 가치", rule: "회사 선택 시 중시하는 가치와 포스코가 부합하는 이유. 600자 이내" },
            { title: "역량 및 성장 계획", rule: "직무 역량 갖추기 위한 학습/도전 경험과 입사 후 발전 계획. 600자 이내" },
        ],
    },
    {
        name: "한화에어로스페이스",
        sections: [
            { title: "지원동기 및 커리어 계획", rule: "지원 이유와 한화에서 키울 커리어 계획. 1000자 이내" },
            { title: "역량 발휘 경험", rule: "직무 관련 역량을 발휘한 경험. 700자 이내" },
        ],
    },
    {
        name: "CJ제일제당",
        sections: [
            { title: "이루고 싶은 성장", rule: "CJ제일제당에 합류해 이루고 싶은 성장. 1000자 이내" },
            { title: "직무 경험과 성장", rule: "쌓아온 경험과 가장 크게 성장했다고 느낀 순간. 1000자 이내" },
        ],
    },
    {
        name: "공기업 NCS",
        sections: [
            { title: "직무 관련 경험 및 활용", rule: "수행업무 관련 경험/경력과 현업 활용방안. 600자 이내" },
            { title: "인재상 관련 강점", rule: "기관 인재상 중 자신의 강점, 사례/경험으로 근거 제시. 600자 이내" },
        ],
    },
];

const sourceBadge: Record<string, { label: string; className: string }> = {
    SARAMIN: { label: "사람인", className: "bg-blue-600" },
    JOBPLANET: { label: "잡플래닛", className: "bg-purple-600" },
    LINKAREER: { label: "링커리어", className: "bg-green-600" },
    JOBKOREA: { label: "잡코리아", className: "bg-red-600" },
};

function getTechList(techStack: unknown): string[] {
    if (!techStack) return [];
    const raw = typeof techStack === "string" ? techStack : (techStack as { value?: string })?.value;
    if (!raw) return [];
    return raw.split(",").map(s => s.trim()).filter(Boolean);
}

export default function JobDetailPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const [job, setJob] = useState<JobPosting | null>(null);
    const [matchScore, setMatchScore] = useState<number | null>(null);
    const [matchReason, setMatchReason] = useState<{ matched?: string[]; missing?: string[]; summary?: string } | null>(null);
    const [showReasonDialog, setShowReasonDialog] = useState(false);
    const [analyzingScore, setAnalyzingScore] = useState(false);
    const [analysis, setAnalysis] = useState<string | null>(null);
    const [analyzingCompany, setAnalyzingCompany] = useState(false);
    const [preparing, setPreparing] = useState(false);
    const [showTemplateDialog, setShowTemplateDialog] = useState(false);
    const [templates, setTemplates] = useState<Template[]>([]);
    const [message, setMessage] = useState("");
    const { token, isLoggedIn } = useAuth();
    const router = useRouter();

    useEffect(() => {
        jobsApi.get(Number(id)).then(setJob).catch(console.error);
    }, [id]);

    // 저장된 AI 분석 결과 로드
    useEffect(() => {
        if (!token) return;
        aiApi.getSavedResults(token, Number(id)).then(saved => {
            if (saved.matchScore != null) setMatchScore(saved.matchScore);
            if (saved.matchScoreReason) {
                try { setMatchReason(JSON.parse(saved.matchScoreReason)); } catch {}
            }
            if (saved.companyAnalysis) setAnalysis(saved.companyAnalysis);
        }).catch(() => {});
    }, [id, token]);

    const handleMatchScore = async (force?: boolean) => {
        if (!token) return;
        setAnalyzingScore(true);
        setMessage("");
        try {
            const res = await aiApi.matchScore(token, Number(id), force);
            if (res.score >= 0) {
                setMatchScore(res.score);
                if (res.reason) {
                    try { setMatchReason(JSON.parse(res.reason)); } catch {}
                }
            } else {
                setMessage("적합률 분석 실패 - 다시 시도해주세요");
            }
        } catch (e) {
            setMessage("적합률 분석 실패: " + (e instanceof Error ? e.message : "서버 오류"));
        } finally {
            setAnalyzingScore(false);
        }
    };

    const handleAnalysis = async () => {
        if (!token) return;
        setAnalyzingCompany(true);
        setMessage("");
        try {
            const res = await aiApi.companyAnalysis(token, Number(id));
            setAnalysis(res.analysis);
        } catch {
            setMessage("기업 분석 실패");
        } finally {
            setAnalyzingCompany(false);
        }
    };

    // 커스텀 자소서
    const [showCustomDialog, setShowCustomDialog] = useState(false);
    const [customSections, setCustomSections] = useState<{ title: string; rule: string }[]>([
        { title: "지원동기", rule: "" },
    ]);
    const [additionalRequest, setAdditionalRequest] = useState("");


    const addSection = () => {
        setCustomSections(prev => [...prev, { title: "", rule: "" }]);
    };

    const removeSection = (idx: number) => {
        setCustomSections(prev => prev.filter((_, i) => i !== idx));
    };

    const updateSection = (idx: number, field: "title" | "rule", value: string) => {
        setCustomSections(prev => prev.map((s, i) => i === idx ? { ...s, [field]: value } : s));
    };

    const handleCustomPrepare = async () => {
        if (!token) return;
        const validSections = customSections.filter(s => s.title.trim());
        if (validSections.length === 0) return;
        setShowCustomDialog(false);
        setPreparing(true);
        setMessage("AI가 자소서를 생성하고 있습니다... 완료되면 자동으로 이동합니다.");
        try {
            const app = await applicationsApi.prepareCustom(
                token, Number(id), validSections, additionalRequest);
            const appId = app.id;
            const { onAiTaskComplete } = require("@/lib/websocket");
            const cleanup = onAiTaskComplete((data: { taskId: string; status: string; result: string }) => {
                if (typeof data.result === "string"
                    && data.result.includes("PREPARE_COMPLETE")
                    && data.result.includes(`"applicationId":${appId}`)) {
                    cleanup();
                    router.push(`/applications/${appId}/preview`);
                }
            });
            setTimeout(() => {
                cleanup();
                router.push(`/applications/${appId}/preview`);
            }, 90000);
        } catch (e: unknown) {
            setMessage(e instanceof Error ? e.message : "준비 실패");
            setPreparing(false);
        }
    };

    const handlePrepareClick = async () => {
        if (!token) return;
        try {
            const list = await templatesApi.list(token);
            const coverLetterTemplates = list.filter(t => t.type === "COVER_LETTER");
            setTemplates(coverLetterTemplates);
            setShowTemplateDialog(true);
        } catch {
            // 템플릿 로드 실패 시 바로 생성
            handlePrepare();
        }
    };

    const handlePrepare = async (templateId?: number) => {
        if (!token) return;
        setShowTemplateDialog(false);
        setPreparing(true);
        setMessage("AI가 자소서를 생성하고 있습니다... 완료되면 자동으로 이동합니다.");
        try {
            const app = await applicationsApi.prepare(token, Number(id), templateId);
            const appId = app.id;
            // WebSocket으로 AI 완료 대기
            const { onAiTaskComplete } = require("@/lib/websocket");
            const cleanup = onAiTaskComplete((data: { taskId: string; status: string; result: string }) => {
                // PREPARE_COMPLETE + 정확한 applicationId 매칭만
                if (typeof data.result === "string"
                    && data.result.includes("PREPARE_COMPLETE")
                    && data.result.includes(`"applicationId":${appId}`)) {
                    cleanup();
                    router.push(`/applications/${appId}/preview`);
                }
            });
            // fallback: 90초 후 강제 이동
            setTimeout(() => {
                cleanup();
                router.push(`/applications/${appId}/preview`);
            }, 90000);
        } catch (e: unknown) {
            setMessage(e instanceof Error ? e.message : "준비 실패");
            setPreparing(false);
        }
    };

    if (!job) {
        return <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-emerald-500" />
        </div>;
    }

    const src = sourceBadge[job.source];
    const techs = getTechList(job.techStack);
    const isAiWorking = analyzingScore || analyzingCompany || preparing;

    return (
        <div className="max-w-5xl mx-auto space-y-3">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Link href="/" className="hover:text-foreground">채용 공고</Link>
                <span>/</span>
                <span className="text-foreground line-clamp-1">{job.title}</span>
            </div>

            {/* 헤더 카드 */}
            <Card>
                <CardHeader>
                    <div className="flex justify-between items-start">
                        <div>
                            <CardTitle className="text-2xl">{job.title}</CardTitle>
                            <p className="mt-1 text-lg text-muted-foreground">{job.company}</p>
                        </div>
                        <Badge className={src.className}>{src.label}</Badge>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    {/* AI 적합률 결과 */}
                    {matchScore !== null && (
                        <div className={`flex items-center justify-between p-4 rounded-lg cursor-pointer transition-opacity hover:opacity-80 ${
                            matchScore >= 70 ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800" :
                            matchScore >= 40 ? "bg-yellow-50 dark:bg-yellow-950 border border-yellow-200 dark:border-yellow-800" :
                            "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800"
                        }`} onClick={() => matchReason && setShowReasonDialog(true)}>
                            <div className="flex items-center gap-3">
                                <span className={`text-3xl font-bold ${
                                    matchScore >= 70 ? "text-emerald-600" : matchScore >= 40 ? "text-yellow-600" : "text-red-600"
                                }`}>{matchScore}%</span>
                                <div>
                                    <p className="text-sm font-medium">AI 적합률 {matchReason && <span className="text-xs text-muted-foreground ml-1">(클릭하여 근거 확인)</span>}</p>
                                    <p className="text-xs text-muted-foreground">
                                        {matchScore >= 70 ? "이 공고와 높은 적합도를 보입니다" :
                                         matchScore >= 40 ? "도전해볼 만한 공고입니다" :
                                         "직무 방향이 다소 다릅니다"}
                                    </p>
                                </div>
                            </div>
                            <Button variant="ghost" size="sm" onClick={(e) => { e.stopPropagation(); handleMatchScore(true); }}
                                disabled={analyzingScore} className="text-xs">
                                재분석
                            </Button>
                        </div>
                    )}

                    {/* 기본 정보 */}
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                        {job.location && <InfoItem label="근무지" value={job.location} />}
                        {job.career && <InfoItem label="경력" value={job.career} />}
                        {job.education && <InfoItem label="학력" value={job.education} />}
                        {job.salary && <InfoItem label="급여" value={job.salary} />}
                        <InfoItem label="등록일" value={job.createdAt?.split("T")[0] || "-"} />
                        {job.deadline && <InfoItem label="마감일" value={job.deadline} />}
                    </div>

                    {/* 기술 스택 */}
                    {techs.length > 0 && (
                        <div>
                            <p className="text-sm font-medium mb-2">기술 스택</p>
                            <div className="flex flex-wrap gap-1">
                                {techs.map((t, i) => (
                                    <Badge key={i} variant="secondary">{t}</Badge>
                                ))}
                            </div>
                        </div>
                    )}

                    <Separator />

                    {/* 액션 버튼 */}
                    <div className="flex flex-wrap gap-2">
                        <a href={job.url} target="_blank" rel="noopener noreferrer">
                            <Button className="bg-emerald-600 hover:bg-emerald-700">
                                공고 원문 보기
                            </Button>
                        </a>
                        {isLoggedIn && (
                            <>
                                <Button variant="outline" onClick={() => handleMatchScore()}
                                    disabled={analyzingScore}>
                                    {analyzingScore ? (
                                        <span className="flex items-center gap-2">
                                            <span className="animate-spin rounded-full h-4 w-4 border-2 border-current border-t-transparent" />
                                            AI 분석 중...
                                        </span>
                                    ) : "AI 적합률 분석"}
                                </Button>
                                <Button variant="outline" onClick={handleAnalysis}
                                    disabled={analyzingCompany}>
                                    {analyzingCompany ? (
                                        <span className="flex items-center gap-2">
                                            <span className="animate-spin rounded-full h-4 w-4 border-2 border-current border-t-transparent" />
                                            AI 분석 중...
                                        </span>
                                    ) : "AI 기업 분석"}
                                </Button>
                                <Button onClick={handlePrepareClick} disabled={preparing}
                                    className="bg-orange-600 hover:bg-orange-700">
                                    {preparing ? (
                                        <span className="flex items-center gap-2">
                                            <span className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                                            지원서 준비 중...
                                        </span>
                                    ) : "기본 자소서 생성"}
                                </Button>
                                <Button onClick={() => setShowCustomDialog(true)} disabled={preparing}
                                    variant="outline" className="border-orange-600 text-orange-600 hover:bg-orange-50">
                                    커스텀 자소서
                                </Button>
                            </>
                        )}
                    </div>

                    {/* 적합률 근거 팝업 */}
                    {showReasonDialog && matchReason && (
                        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setShowReasonDialog(false)}>
                            <div className="bg-background rounded-xl shadow-xl p-6 max-w-md w-full mx-4 space-y-4" onClick={e => e.stopPropagation()}>
                                <div className="flex items-center justify-between">
                                    <h3 className="text-lg font-bold">AI 적합률 분석 근거</h3>
                                    <button onClick={() => setShowReasonDialog(false)} className="text-muted-foreground hover:text-foreground text-xl">&times;</button>
                                </div>
                                <div className="flex items-center gap-3">
                                    <span className={`text-4xl font-bold ${
                                        matchScore! >= 70 ? "text-emerald-600" : matchScore! >= 40 ? "text-yellow-600" : "text-red-600"
                                    }`}>{matchScore}%</span>
                                    {matchReason.summary && <p className="text-sm text-muted-foreground">{matchReason.summary}</p>}
                                </div>
                                {matchReason.matched && matchReason.matched.length > 0 && (
                                    <div>
                                        <p className="text-sm font-medium text-emerald-600 mb-1">매칭된 기술</p>
                                        <div className="flex flex-wrap gap-1.5">
                                            {matchReason.matched.map((t, i) => (
                                                <Badge key={i} className="bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300">{t}</Badge>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {matchReason.missing && matchReason.missing.length > 0 && (
                                    <div>
                                        <p className="text-sm font-medium text-red-600 mb-1">부족한 기술</p>
                                        <div className="flex flex-wrap gap-1.5">
                                            {matchReason.missing.map((t, i) => (
                                                <Badge key={i} variant="outline" className="border-red-300 text-red-600 dark:border-red-700 dark:text-red-400">{t}</Badge>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                <Button className="w-full" onClick={() => setShowReasonDialog(false)}>닫기</Button>
                            </div>
                        </div>
                    )}

                    {/* AI 작업 상태 표시 */}
                    {/* 템플릿 선택 다이얼로그 */}
                    {showTemplateDialog && (
                        <div className="p-4 rounded-lg bg-muted border border-border space-y-3">
                            <p className="text-sm font-medium">자소서 템플릿을 선택하세요</p>
                            <p className="text-xs text-muted-foreground">
                                템플릿을 선택하면 AI가 해당 구조에 맞춰 자소서를 생성합니다.
                            </p>
                            <div className="space-y-2">
                                {templates.length > 0 ? (
                                    templates.map(t => (
                                        <Button key={t.id} variant="outline" className="w-full justify-start text-left"
                                            onClick={() => handlePrepare(t.id)}>
                                            <div>
                                                <p className="font-medium text-sm">{t.name}</p>
                                                <p className="text-xs text-muted-foreground line-clamp-1">
                                                    {t.content.substring(0, 80)}...
                                                </p>
                                            </div>
                                        </Button>
                                    ))
                                ) : (
                                    <p className="text-xs text-muted-foreground">저장된 자소서 템플릿이 없습니다.</p>
                                )}
                            </div>
                            <div className="flex gap-2">
                                <Button size="sm" onClick={() => handlePrepare()}
                                    className="bg-orange-600 hover:bg-orange-700">
                                    템플릿 없이 생성
                                </Button>
                                <Button size="sm" variant="outline" onClick={() => setShowTemplateDialog(false)}>
                                    취소
                                </Button>
                            </div>
                        </div>
                    )}

                    {/* 커스텀 자소서 다이얼로그 */}
                    {showCustomDialog && (
                        <div className="p-4 rounded-lg bg-muted border border-border space-y-4">
                            <p className="text-sm font-medium">커스텀 자소서 문항 설정</p>
                            <p className="text-xs text-muted-foreground">
                                대기업 프리셋을 불러오거나, 직접 문항을 입력하세요. AI가 문항별로 맞춤 자소서를 생성합니다.
                            </p>

                            {/* 프리셋 불러오기 */}
                            <div className="flex flex-wrap gap-1.5">
                                {QUESTION_PRESETS.map(preset => (
                                    <button key={preset.name}
                                        onClick={() => {
                                            setCustomSections(preset.sections.map(s => ({ ...s })));
                                            setMessage(`"${preset.name}" 프리셋이 적용되었습니다.`);
                                            setTimeout(() => setMessage(""), 2000);
                                        }}
                                        className="px-2.5 py-1 text-xs border rounded-full hover:bg-accent transition-colors">
                                        {preset.name}
                                    </button>
                                ))}
                            </div>

                            <div className="space-y-3">
                                {customSections.map((section, idx) => (
                                    <div key={idx} className="p-3 border rounded-lg space-y-2">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs font-medium text-muted-foreground">문항 {idx + 1}</span>
                                            {customSections.length > 1 && (
                                                <button onClick={() => removeSection(idx)}
                                                    className="text-xs text-red-500 hover:text-red-700">삭제</button>
                                            )}
                                        </div>
                                        <input
                                            type="text"
                                            placeholder="문항 제목 (예: 지원동기, 직무역량, 프로젝트 경험)"
                                            value={section.title}
                                            onChange={e => updateSection(idx, "title", e.target.value)}
                                            className="w-full px-3 py-2 text-sm border rounded-md bg-background"
                                        />
                                        <input
                                            type="text"
                                            placeholder="작성 규칙 (예: 회사 사업과 내 경험의 접점, 500자 이내)"
                                            value={section.rule}
                                            onChange={e => updateSection(idx, "rule", e.target.value)}
                                            className="w-full px-3 py-2 text-sm border rounded-md bg-background"
                                        />
                                    </div>
                                ))}
                            </div>

                            <button onClick={addSection}
                                className="text-sm text-blue-500 hover:text-blue-700">
                                + 문항 추가
                            </button>

                            <div>
                                <p className="text-xs font-medium text-muted-foreground mb-1">추가 요청 (선택)</p>
                                <input
                                    type="text"
                                    placeholder="예: 비전공자라 학습과정 강조해줘, 협업 경험 위주로"
                                    value={additionalRequest}
                                    onChange={e => setAdditionalRequest(e.target.value)}
                                    className="w-full px-3 py-2 text-sm border rounded-md bg-background"
                                />
                            </div>

                            <div className="flex gap-2">
                                <Button size="sm" onClick={handleCustomPrepare}
                                    className="bg-orange-600 hover:bg-orange-700">
                                    AI 커스텀 자소서 생성
                                </Button>
                                <Button size="sm" variant="outline" onClick={() => setShowCustomDialog(false)}>
                                    취소
                                </Button>
                            </div>
                        </div>
                    )}

                    {isAiWorking && (
                        <div className="flex items-center gap-3 p-3 rounded-lg bg-blue-50 dark:bg-blue-950 text-blue-700 dark:text-blue-300 text-sm">
                            <span className="animate-spin rounded-full h-5 w-5 border-2 border-blue-500 border-t-transparent" />
                            <span>
                                {analyzingScore && "AI가 적합률을 분석하고 있습니다... (10~30초 소요)"}
                                {analyzingCompany && "AI가 기업을 분석하고 있습니다... (10~30초 소요)"}
                                {preparing && "AI가 지원서를 준비하고 있습니다... (30초~1분 소요)"}
                            </span>
                        </div>
                    )}

                    {message && (
                        <p className={`text-sm mt-2 ${message.includes("실패") ? "text-red-500" : "text-emerald-500"}`}>
                            {message}
                        </p>
                    )}
                </CardContent>
            </Card>

            {/* 공고 이미지 (작은 이미지 필터링) */}
            {job.companyImages && (
                <Card>
                    <CardContent className="p-4 space-y-4">
                        {job.companyImages.split(",").filter(Boolean).map((url, i) => (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img key={i} src={url.trim()} alt="공고 이미지"
                                className="w-full h-auto rounded-md object-contain"
                                onLoad={(e) => {
                                    const img = e.target as HTMLImageElement;
                                    if (img.naturalWidth < 50 || img.naturalHeight < 50) {
                                        img.style.display = "none";
                                    }
                                }}
                                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
                            />
                        ))}
                    </CardContent>
                </Card>
            )}

            {/* 상세 내용 - 섹션별 분리 */}
            {job.description && (
                <DescriptionSections description={job.description} companyImages={job.companyImages} />
            )}

            {/* 자격 요건 */}
            {job.requirements && (
                <Card>
                    <CardHeader><CardTitle className="text-lg">자격 요건</CardTitle></CardHeader>
                    <CardContent>
                        <ContentRenderer text={job.requirements} />
                    </CardContent>
                </Card>
            )}

            {/* AI 기업 분석 결과 */}
            {analysis && (
                <Card className="bg-muted/50">
                    <CardHeader><CardTitle className="text-lg">AI 기업 분석</CardTitle></CardHeader>
                    <CardContent>
                        <Markdown>{analysis}</Markdown>
                    </CardContent>
                </Card>
            )}
        </div>
    );
}

/** 탭 구분 데이터를 테이블 행/일반 텍스트로 분리 */
type ContentBlock = { type: "text"; content: string } | { type: "table"; rows: string[][] };

function parseContentBlocks(text: string): ContentBlock[] {
    const lines = text.split("\n");
    const blocks: ContentBlock[] = [];
    let tabLines: string[] = [];
    let textLines: string[] = [];

    const flushText = () => {
        if (textLines.length > 0) {
            blocks.push({ type: "text", content: textLines.join("\n") });
            textLines = [];
        }
    };

    const flushTable = () => {
        if (tabLines.length === 0) return;

        // 첫 번째 탭 라인으로 열 수 결정
        const firstCols = tabLines[0].split("\t").length;

        // 열 수가 같은 줄끼리 행으로 묶고, 열 수가 다른 줄은 이전 행에 합침
        const rows: string[][] = [];
        for (const line of tabLines) {
            const cells = line.split("\t").map(c => c.trim());
            if (cells.length === firstCols || rows.length === 0) {
                rows.push(cells);
            } else {
                // 열 수가 다르면 이전 행의 셀에 내용 추가
                const lastRow = rows[rows.length - 1];
                cells.forEach((cell, i) => {
                    if (cell && i < lastRow.length) {
                        lastRow[i] = (lastRow[i] + "\n" + cell).trim();
                    } else if (cell) {
                        lastRow[lastRow.length - 1] = (lastRow[lastRow.length - 1] + "\n" + cell).trim();
                    }
                });
            }
        }

        if (rows.length >= 2) {
            blocks.push({ type: "table", rows });
        } else {
            rows.forEach(row => textLines.push(row.join(" ")));
        }
        tabLines = [];
    };

    for (const line of lines) {
        if (line.includes("\t")) {
            const cells = line.split("\t").map(c => c.trim());
            if (cells.filter(c => c.length > 0).length >= 2) {
                if (textLines.length > 0) flushText();
                tabLines.push(line);
                continue;
            }
        }
        // 탭 없는 줄이지만 테이블 수집 중이면 이전 행에 합침
        if (tabLines.length > 0 && line.trim().length > 0 && !line.trim().match(/^[가-힣a-zA-Z].*[가-힣a-zA-Z]$/) ) {
            // 단독 텍스트 줄이면 이전 탭 라인의 마지막 셀에 추가
            tabLines[tabLines.length - 1] += "\t" + line.trim();
            continue;
        }
        if (tabLines.length > 0) flushTable();
        textLines.push(line);
    }
    if (tabLines.length > 0) flushTable();
    if (textLines.length > 0) flushText();

    return blocks;
}

function TabTable({ rows }: { rows: string[][] }) {
    const maxCols = Math.max(...rows.map(r => r.length));
    // 열 수 맞추기
    const normalized = rows.map(row => {
        const padded = [...row];
        while (padded.length < maxCols) padded.push("");
        return padded;
    });

    return (
        <div className="overflow-x-auto my-3">
            <table className="w-full text-sm border-collapse border border-border">
                <thead>
                    <tr className="bg-muted/50">
                        {normalized[0].map((cell, i) => (
                            <th key={i} className="border border-border px-3 py-2 text-left font-medium text-xs">
                                {cell || "\u00A0"}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {normalized.slice(1).map((row, i) => (
                        <tr key={i} className={i % 2 === 0 ? "" : "bg-muted/20"}>
                            {row.map((cell, j) => (
                                <td key={j} className="border border-border px-3 py-2 text-xs whitespace-pre-line">
                                    {cell || "\u00A0"}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function HtmlRenderer({ html, companyImages }: { html: string; companyImages?: string }) {
    // 상단에 표시된 이미지와 동일한 img 태그 제거
    let processed = html;
    if (companyImages) {
        const imgUrls = companyImages.split(",").map(u => u.trim()).filter(Boolean);
        imgUrls.forEach(url => {
            processed = processed.replace(new RegExp(`<img[^>]*src=["']${url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}["'][^>]*>`, 'gi'), '');
        });
    }
    const clean = typeof window !== "undefined"
        ? DOMPurify.sanitize(processed, {
            ALLOWED_TAGS: ["table","thead","tbody","tfoot","tr","td","th","caption",
                "p","br","b","strong","em","i","u","ul","ol","li",
                "h1","h2","h3","h4","h5","h6","div","span","a","img","col","colgroup"],
            ALLOWED_ATTR: ["colspan","rowspan","href","src","alt","width","height","border","cellspacing","cellpadding"],
        })
        : html;
    return (
        <div
            className="prose prose-sm dark:prose-invert max-w-none overflow-x-auto
                [&_table]:w-full [&_table]:border-collapse [&_table]:text-sm [&_table]:my-3
                [&_td]:border [&_td]:border-border [&_td]:px-3 [&_td]:py-2 [&_td]:text-xs [&_td]:align-top
                [&_th]:border [&_th]:border-border [&_th]:px-3 [&_th]:py-2 [&_th]:text-xs [&_th]:font-medium [&_th]:bg-muted/50
                [&_img]:max-w-full [&_img]:h-auto [&_img]:rounded-md
                [&_*]:max-w-full"
            dangerouslySetInnerHTML={{ __html: clean }}
        />
    );
}

function ContentRenderer({ text, companyImages }: { text: string; companyImages?: string }) {
    const isHtml = text.includes("<table") || text.includes("<div") || text.includes("<p")
                || text.includes("<h3") || text.includes("<ul") || text.includes("<li");

    if (isHtml) {
        return <HtmlRenderer html={text} companyImages={companyImages} />;
    }

    // 기존 텍스트 데이터 호환
    const cleaned = text
        .split("\n")
        .map(line => {
            let processed = line.replace(/\t/g, "  |  ");
            const trimmed = processed.trim();
            if (trimmed.startsWith("ㆍ") || trimmed.startsWith("·")) {
                return "- " + trimmed.substring(1).trim();
            }
            if (trimmed.startsWith("- ")) return trimmed;
            return processed;
        })
        .join("\n")
        .replace(/\n{3,}/g, "\n\n");
    return <Markdown>{cleaned}</Markdown>;
}

function DescriptionSections({ description, companyImages }: { description: string; companyImages?: string }) {
    // 섹션 헤더 키워드로 분리
    const sectionHeaders = [
        "모집요강", "모집분야", "모집내용", "채용 분야",
        "지원자격", "지원 자격", "자격요건", "공통자격",
        "근무 조건", "근무조건", "처우",
        "전형절차", "채용 전형", "전형 절차",
        "접수기간", "접수방법", "응시원서", "지원 방법",
        "기업 정보", "기업정보", "회사 소개",
        "복리후생", "기타사항", "기타",
        "안내사항", "문의"
    ];

    const lines = description.split("\n");
    const sections: { title: string; content: string }[] = [];
    let currentTitle = "상세 내용";
    let currentLines: string[] = [];

    for (const line of lines) {
        const trimmed = line.trim();
        const isHeader = sectionHeaders.some(h => trimmed === h || trimmed.startsWith(h + "\n"));
        if (isHeader && trimmed.length < 20) {
            if (currentLines.length > 0) {
                sections.push({ title: currentTitle, content: currentLines.join("\n").trim() });
            }
            currentTitle = trimmed;
            currentLines = [];
        } else {
            currentLines.push(line);
        }
    }
    if (currentLines.length > 0) {
        sections.push({ title: currentTitle, content: currentLines.join("\n").trim() });
    }

    // 빈 섹션 제거
    const filtered = sections.filter(s => s.content.length > 10);

    if (filtered.length <= 1) {
        return (
            <Card>
                <CardHeader><CardTitle className="text-lg">상세 내용</CardTitle></CardHeader>
                <CardContent>
                    <ContentRenderer text={description} companyImages={companyImages} />
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardContent className="p-4 space-y-4">
                {filtered.map((section, i) => (
                    <div key={i}>
                        {i > 0 && <div className="border-t my-3" />}
                        <p className="text-sm font-semibold text-emerald-600 mb-1">{section.title}</p>
                        <ContentRenderer text={section.content} companyImages={companyImages} />
                    </div>
                ))}
            </CardContent>
        </Card>
    );
}

function InfoItem({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="text-sm font-medium">{value}</p>
        </div>
    );
}
