"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { applicationsApi, aiApi, projectsApi, type JobApplication, type Project } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Markdown } from "@/components/ui/markdown";
import { useAiTaskQueue } from "@/lib/use-ai-task-queue";
import { AiTaskProgress, AiTaskNotification } from "@/components/ai-task-indicator";

function stripHtml(text: string): string {
    if (!text) return "";
    let clean = text
        .replace(/<[^>]*>/g, "")
        .replace(/&nbsp;/g, " ")
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/\n{3,}/g, "\n\n");
    const cutPhrases = ["원하시면 제가", "활용 팁", "다음 중 하나로", "바로 바꿔드릴", "짧은 버전", "다음 형태 중"];
    for (const phrase of cutPhrases) {
        const idx = clean.indexOf(phrase);
        if (idx > 0) clean = clean.substring(0, idx).trim();
    }
    return clean.trim();
}

function downloadPdf(text: string, filename: string, title: string) {
    const printWindow = window.open("", "_blank");
    if (!printWindow) { alert("팝업 차단을 해제해주세요."); return; }

    const html = text.split("\n").map(line => {
        const trimmed = line.trim();
        if (!trimmed) return "<br/>";
        // 구분선 (───)
        if (/^[─━─]{3,}/.test(trimmed))
            return `<hr style="border:none;border-top:1.5px solid #d1d5db;margin:2px 0 8px;"/>`;
        // 섹션 제목 (1. 프로젝트 개요)
        if (/^\d+\.\s/.test(trimmed))
            return `<h3 style="font-size:15px;font-weight:700;margin:20px 0 2px;color:#1a1a1a;">${trimmed}</h3>`;
        // 【소제목】 강조
        if (trimmed.includes("【")) {
            const formatted = trimmed.replace(/【([^】]+)】/g, '<strong style="color:#059669;">【$1】</strong>');
            return `<p style="margin:6px 0;font-size:13px;line-height:1.8;">${formatted}</p>`;
        }
        // ▶ 불릿 (문제 해결)
        if (trimmed.startsWith("▶"))
            return `<p style="margin:8px 0 2px;font-size:13px;line-height:1.8;font-weight:600;">${trimmed}</p>`;
        // ✔ 불릿 (성과)
        if (trimmed.startsWith("✔"))
            return `<p style="margin:2px 0;padding-left:4px;font-size:13px;line-height:1.8;">${trimmed}</p>`;
        // [제목] 형태 (기존 호환)
        if (trimmed.startsWith("[") && trimmed.endsWith("]"))
            return `<h3 style="font-size:15px;font-weight:600;margin:16px 0 8px;color:#059669;">${trimmed}</h3>`;
        return `<p style="margin:4px 0;font-size:13px;line-height:1.8;">${trimmed}</p>`;
    }).join("");

    printWindow.document.write(`<!DOCTYPE html><html><head>
        <title>${filename}</title>
        <style>
            @page { size: A4; margin: 20mm; }
            body { font-family: 'Malgun Gothic', 'Apple SD Gothic Neo', sans-serif; color: #1a1a1a; max-width: 700px; margin: 0 auto; padding: 40px 20px; }
            h2 { font-size: 18px; font-weight: 700; margin-bottom: 24px; border-bottom: 2px solid #10b981; padding-bottom: 8px; }
            @media print { body { padding: 0; } }
        </style>
    </head><body>
        <h2>${title}</h2>
        ${html}
    </body></html>`);
    printWindow.document.close();
    printWindow.onload = () => { printWindow.print(); };
}

export default function PreviewPage() {
    const params = useParams();
    const id = Number(params?.id);
    const { token } = useAuth();
    const [app, setApp] = useState<JobApplication | null>(null);
    const [coverLetter, setCoverLetter] = useState("");
    const [companyAnalysis, setCompanyAnalysis] = useState("");
    const [userMatchScore, setUserMatchScore] = useState<number | undefined>();
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [regenerating, setRegenerating] = useState<string | null>(() => {
        if (typeof window === "undefined") return null;
        const saved = localStorage.getItem(`regenerating-${id}`);
        // 5분 이상 된 건 만료 처리
        if (saved) {
            try {
                const { value, timestamp } = JSON.parse(saved);
                if (Date.now() - timestamp < 5 * 60 * 1000) return value;
                localStorage.removeItem(`regenerating-${id}`);
            } catch { localStorage.removeItem(`regenerating-${id}`); }
        }
        return null;
    });
    const updateRegenerating = (value: string | null) => {
        setRegenerating(value);
        if (value) {
            localStorage.setItem(`regenerating-${id}`, JSON.stringify({ value, timestamp: Date.now() }));
        } else {
            localStorage.removeItem(`regenerating-${id}`);
        }
    };
    const [allProjects, setAllProjects] = useState<Project[]>([]);
    const [selectedProjectIds, setSelectedProjectIds] = useState<number[]>([]);
    const [showPicker, setShowPicker] = useState(false);
    const [notifications, setNotifications] = useState<{ id: string; message: string; type: "success" | "error" }[]>([]);

    const aiQueue = useAiTaskQueue(token);

    useEffect(() => {
        if (!token || !id) return;
        loadApplication();
    }, [token, id]);

    // AI 생성 완료 시 자동 리로드
    useEffect(() => {
        if (!token) return;
        const { onAiTaskComplete } = require("@/lib/websocket");
        const cleanup = onAiTaskComplete((data: { taskId: string; status: string; result: string }) => {
            if (data.status === "COMPLETED") {
                loadApplication();
            }
        });
        return cleanup;
    }, [token]);

    const loadApplication = async () => {
        try {
            const found = await applicationsApi.get(token!, id);
            if (found) {
                setApp(found);
                setCoverLetter(found.coverLetter || "");

                if (found.coverLetter) {
                    updateRegenerating(null);
                }

                if (found.matchedProjectIds) {
                    const ids = found.matchedProjectIds.split(",").map(Number).filter(Boolean);
                    setSelectedProjectIds(ids);
                }

                const projects = await projectsApi.list(token!);
                setAllProjects(projects);

                try {
                    const savedResults = await aiApi.getSavedResults(token!, found.jobPosting.id);
                    if (savedResults.companyAnalysis) setCompanyAnalysis(savedResults.companyAnalysis);
                    if (savedResults.matchScore) setUserMatchScore(savedResults.matchScore);
                } catch { /* AI 미연결 시 무시 */ }
            }
        } catch (e) {
            console.error("로딩 실패:", e);
        } finally {
            setLoading(false);
        }
    };

    const toggleProjectSelection = async (pid: number) => {
        const updated = selectedProjectIds.includes(pid)
            ? selectedProjectIds.filter(id => id !== pid)
            : [...selectedProjectIds, pid];
        setSelectedProjectIds(updated);
        // DB에 즉시 저장
        if (token && app) {
            try {
                await applicationsApi.updateMatchedProjects(token, app.id, updated.join(","));
            } catch { /* 저장 실패해도 UI는 유지 */ }
        }
    };

    const addNotification = (message: string, type: "success" | "error") => {
        const nid = Date.now().toString();
        setNotifications(prev => [...prev, { id: nid, message, type }]);
        setTimeout(() => {
            setNotifications(prev => prev.filter(n => n.id !== nid));
        }, 5000);
    };

    const removeNotification = (nid: string) => {
        setNotifications(prev => prev.filter(n => n.id !== nid));
    };

    const reloadCompanyAnalysis = async () => {
        if (!token || !app) return;
        try {
            const savedResults = await aiApi.getSavedResults(token, app.jobPosting.id);
            if (savedResults.companyAnalysis) setCompanyAnalysis(savedResults.companyAnalysis);
            if (savedResults.matchScore) setUserMatchScore(savedResults.matchScore);
        } catch {}
    };

    /** 비동기 자소서 재생성 */
    const handleRegenerateCoverLetter = async () => {
        if (!token || !app || regenerating) return;
        updateRegenerating("coverLetter");
        const projectIds = selectedProjectIds.length > 0 ? selectedProjectIds.join(",") : undefined;
        await aiQueue.startCoverLetter(
            app.jobPosting.id,
            undefined,
            async (result) => {
                // DB 저장 먼저 → 그다음 UI 반영
                if (token && app) {
                    try { await applicationsApi.updateDocs(token, app.id, result, app.portfolioContent || ""); } catch {}
                }
                setCoverLetter(result);
                loadApplication();
                reloadCompanyAnalysis();
                updateRegenerating(null);
                addNotification("자소서 재생성이 완료되었습니다.", "success");
            },
            (error) => {
                updateRegenerating(null);
                addNotification("자소서 재생성 실패: " + error, "error");
            },
            projectIds,
        );
    };

    /** 선택한 프로젝트로 비동기 자소서 재생성 */
    const handleRegenerateAll = async () => {
        if (!token || !app || selectedProjectIds.length === 0 || regenerating) return;
        updateRegenerating("all");

        const projectIds = selectedProjectIds.join(",");

        // 선택한 프로젝트 ID 저장
        await applicationsApi.updateMatchedProjects(token, app.id, projectIds);

        await aiQueue.startCoverLetter(
            app.jobPosting.id,
            undefined,
            async (result) => {
                if (token && app) {
                    try { await applicationsApi.updateDocs(token, app.id, result, app.portfolioContent || ""); } catch {}
                }
                setCoverLetter(result);
                loadApplication();
                reloadCompanyAnalysis();
                updateRegenerating(null);
                addNotification("자소서 재생성 완료", "success");
            },
            (error) => {
                updateRegenerating(null);
                addNotification("자소서 재생성 실패: " + error, "error");
            },
            projectIds,
        );

        setShowPicker(false);
    };

    const handleSubmit = async () => {
        if (!token || !app) return;
        setSubmitting(true);
        try {
            await applicationsApi.updateDocs(token, app.id, coverLetter, app.portfolioContent || "");
            const result = await applicationsApi.submit(token, app.id);
            setApp(result);
            alert(result.status === "APPLIED" || result.status === "VERIFIED"
                ? "지원 완료!"
                : result.status === "FAILED"
                    ? `지원 실패: ${result.failReason}`
                    : "지원이 처리되었습니다.");
        } catch (e: unknown) {
            alert("지원 실패: " + (e instanceof Error ? e.message : "알 수 없는 오류"));
        } finally {
            setSubmitting(false);
        }
    };

    const handleManualApply = async () => {
        if (!token || !app) return;
        try {
            const result = await applicationsApi.manualApply(token, app.id);
            setApp(result);
            alert("수동 지원 완료로 표시되었습니다.");
        } catch (e: unknown) {
            alert("실패: " + (e instanceof Error ? e.message : ""));
        }
    };

    if (loading) return <div className="flex justify-center py-20">로딩 중...</div>;
    if (!app) return <div className="flex justify-center py-20">지원 내역을 찾을 수 없습니다.</div>;

    const job = app.jobPosting;
    const isHomepageApply = job.applicationMethod === "HOMEPAGE";

    const statusColor: Record<string, string> = {
        NOT_APPLIED: "bg-gray-500",
        PENDING: "bg-blue-500",
        APPLIED: "bg-green-500",
        VERIFIED: "bg-green-600",
        MANUALLY_MARKED: "bg-green-400",
        FAILED: "bg-red-500",
    };

    return (
        <div className="container mx-auto py-6 px-4">
            <h1 className="text-2xl font-bold mb-6">지원 검토 및 편집</h1>

            <div className="flex items-center gap-3 mb-4">
                <Badge className={statusColor[app.status] || "bg-gray-500"}>
                    {app.status}
                </Badge>
                <span className="text-sm text-muted-foreground">
                    {job.company} — {job.title}
                </span>
            </div>

            {/* 알림 영역 */}
            {notifications.length > 0 && (
                <div className="space-y-2 mb-4">
                    {notifications.map(n => (
                        <AiTaskNotification
                            key={n.id}
                            message={n.message}
                            type={n.type}
                            onClose={() => removeNotification(n.id)}
                        />
                    ))}
                </div>
            )}


            {/* AI 태스크 진행 상태 (재생성용) */}
            {aiQueue.hasActiveTasks && (
                <div className="mb-4">
                    <AiTaskProgress tasks={aiQueue.activeTasks} />
                </div>
            )}

            {/* 2분할 레이아웃 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* 왼쪽 패널: 기업 정보 + 공고 상세 */}
                <div className="space-y-4">
                    <Card>
                        <CardHeader><CardTitle>기업 기본 정보</CardTitle></CardHeader>
                        <CardContent className="space-y-2 text-sm">
                            <p><strong>회사명:</strong> {job.company}</p>
                            <p><strong>위치:</strong> {job.location || "정보 없음"}</p>
                            <p><strong>지원 방식:</strong> {job.applicationMethod === "DIRECT_APPLY" ? "즉시지원" :
                                job.applicationMethod === "HOMEPAGE" ? "홈페이지 지원" : job.applicationMethod}</p>
                            <p><strong>마감일:</strong> {job.deadline || "상시 채용"}</p>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader><CardTitle>공고 상세 정보</CardTitle></CardHeader>
                        <CardContent className="text-sm space-y-2">
                            <p><strong>학력:</strong> {job.education || "-"}</p>
                            <p><strong>경력:</strong> {job.career || "-"}</p>
                            <p><strong>연봉:</strong> {job.salary || "-"}</p>
                            {job.techStack && (
                                <div>
                                    <strong>기술 스택:</strong>
                                    <div className="flex flex-wrap gap-1 mt-1">
                                        {(typeof job.techStack === "string" ? job.techStack : (job.techStack as any)?.value || "").split(",").filter(Boolean).map((s: string, i: number) => (
                                            <Badge key={`${s.trim()}-${i}`} variant="outline" className="text-xs">{s.trim()}</Badge>
                                        ))}
                                    </div>
                                </div>
                            )}
                            {job.description && (
                                <div className="mt-3 text-muted-foreground text-xs max-h-60 overflow-y-auto prose prose-xs dark:prose-invert"
                                    dangerouslySetInnerHTML={{ __html: job.description }} />
                            )}
                        </CardContent>
                    </Card>

                    {companyAnalysis && (
                        <Card>
                            <CardHeader><CardTitle>AI 기업 분석</CardTitle></CardHeader>
                            <CardContent>
                                <Markdown>{companyAnalysis}</Markdown>
                            </CardContent>
                        </Card>
                    )}

                    {userMatchScore != null && (
                        <Card>
                            <CardHeader><CardTitle>적합률</CardTitle></CardHeader>
                            <CardContent>
                                <div className="text-3xl font-bold text-center">
                                    {userMatchScore}%
                                </div>
                            </CardContent>
                        </Card>
                    )}
                </div>

                {/* 오른쪽 패널: 프로젝트 + 자소서 + 포폴 + 버튼 */}
                <div className="space-y-4">
                    {/* 매칭된 프로젝트 */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between">
                                <CardTitle>매칭 프로젝트</CardTitle>
                                <Button variant="outline" size="sm" onClick={() => setShowPicker(!showPicker)}>
                                    {showPicker ? "닫기" : "프로젝트 다시 선택"}
                                </Button>
                            </div>
                        </CardHeader>
                        <CardContent>
                            {showPicker ? (
                                <div className="space-y-3">
                                    <p className="text-xs text-muted-foreground">프로젝트를 선택하고 AI가 다시 생성합니다.</p>
                                    <div className="space-y-2 max-h-48 overflow-y-auto">
                                        {allProjects.map(p => (
                                            <label key={p.id} className="flex items-center gap-2 p-2 border rounded hover:bg-gray-50 cursor-pointer">
                                                <input type="checkbox"
                                                    checked={selectedProjectIds.includes(p.id)}
                                                    onChange={() => toggleProjectSelection(p.id)} />
                                                <div>
                                                    <span className="text-sm font-medium">{p.name}</span>
                                                    {p.techStack && <span className="text-xs text-muted-foreground ml-2">{p.techStack}</span>}
                                                </div>
                                            </label>
                                        ))}
                                    </div>
                                    <Button className="w-full bg-emerald-600 hover:bg-emerald-700"
                                        disabled={selectedProjectIds.length === 0 || !!regenerating}
                                        onClick={handleRegenerateAll}>
                                        {regenerating === "all" ? "생성중..." : `선택한 ${selectedProjectIds.length}개 프로젝트로 재생성`}
                                    </Button>
                                </div>
                            ) : (
                                <div className="flex flex-wrap gap-2">
                                    {selectedProjectIds.length > 0 ? (
                                        allProjects.filter(p => selectedProjectIds.includes(p.id))
                                            .map(p => <Badge key={p.id} className="bg-emerald-600">{p.name}</Badge>)
                                    ) : (
                                        <span className="text-sm text-muted-foreground">매칭된 프로젝트 없음</span>
                                    )}
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between">
                                <CardTitle>자기소개서</CardTitle>
                                <div className="flex gap-2">
                                    <span className="text-xs text-muted-foreground">{coverLetter.length}자</span>
                                    <Button variant="outline" size="sm"
                                        disabled={!!regenerating}
                                        onClick={handleRegenerateCoverLetter}>
                                        {regenerating === "coverLetter" ? "생성중..." : "재생성"}
                                    </Button>
                                    <Button variant="outline" size="sm" onClick={() => {
                                        navigator.clipboard.writeText(stripHtml(coverLetter));
                                        addNotification("자소서가 클립보드에 복사되었습니다.", "success");
                                    }}>복사</Button>
                                    <Button variant="outline" size="sm" onClick={() =>
                                        downloadPdf(stripHtml(coverLetter), `자기소개서_${app?.jobPosting?.company || ""}.pdf`, "자기소개서")
                                    }>PDF</Button>
                                    <Button variant="outline" size="sm" onClick={async () => {
                                        if (!token || !app) return;
                                        await applicationsApi.updateDocs(token, app.id, coverLetter, app.portfolioContent || "");
                                        addNotification("저장 완료", "success");
                                    }}>저장</Button>
                                </div>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <textarea
                                className="w-full p-4 border rounded-md text-sm resize-y leading-relaxed font-sans bg-background"
                                style={{ minHeight: "400px" }}
                                value={stripHtml(coverLetter)}
                                onChange={(e) => setCoverLetter(e.target.value)}
                                placeholder={coverLetter ? "" : "AI가 자소서를 생성하고 있습니다... 완료되면 자동으로 표시됩니다."}
                            />
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader><CardTitle>추가 파일 첨부</CardTitle></CardHeader>
                        <CardContent>
                            <input type="file" multiple className="text-sm" />
                            <p className="text-xs text-muted-foreground mt-2">
                                증명서, 포트폴리오 PDF 등을 첨부할 수 있습니다.
                            </p>
                        </CardContent>
                    </Card>

                    {/* 지원 버튼 */}
                    <div className="space-y-3">
                        {isHomepageApply ? (
                            <>
                                <a
                                    href={job.url}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="w-full inline-flex items-center justify-center rounded-md bg-primary text-primary-foreground h-10 px-4 py-2 text-sm font-medium hover:bg-primary/90"
                                >
                                    채용 홈페이지에서 직접 지원하기
                                </a>
                                <Button variant="outline" className="w-full" onClick={handleManualApply}>
                                    지원 완료로 표시 (수동)
                                </Button>
                            </>
                        ) : (
                            <Button
                                className="w-full"
                                size="lg"
                                onClick={handleSubmit}
                                disabled={submitting || app.status === "APPLIED" || app.status === "VERIFIED"}
                            >
                                {submitting ? "지원 처리 중..." :
                                    app.status === "APPLIED" || app.status === "VERIFIED" ? "지원 완료" :
                                        app.status === "FAILED" ? "재시도" : "최종 지원"}
                            </Button>
                        )}

                        {app.status === "FAILED" && app.failReason && (
                            <div className="p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-700">
                                <strong>실패 사유:</strong> {app.failReason}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

