"use client";

import { useState, useEffect, useCallback } from "react";
import { projectsApi, aiApi, type Project } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import dynamic from "next/dynamic";

const RichEditor = dynamic(() => import("@/components/ui/rich-editor"), { ssr: false });

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function stripHtml(text: string): string {
    if (!text) return "";
    return text
        .replace(/<[^>]*>/g, "")
        .replace(/&nbsp;/g, " ")
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}

function downloadPdf(html: string, filename: string, title: string) {
    const printWindow = window.open("", "_blank");
    if (!printWindow) { alert("팝업 차단을 해제해주세요."); return; }

    printWindow.document.write(`
        <html><head><title>${title}</title>
        <style>
            @page{margin:20mm}
            body{font-family:'Pretendard','Apple SD Gothic Neo','Malgun Gothic',sans-serif;max-width:700px;margin:0 auto;padding:40px 20px;font-size:13px;line-height:1.8;color:#1a1a1a}
            h2{font-size:18px;font-weight:800;color:#111;margin:32px 0 12px;padding-bottom:8px;border-bottom:2px solid #d1d5db}
            h3{font-size:13px;font-weight:600;color:#047857;margin:18px 0 4px;padding-left:8px;border-left:3px solid #10b981}
            p{margin:4px 0}
            strong{font-weight:700}
            img{max-width:100%;border-radius:8px;margin:12px 0}
            blockquote{border-left:3px solid #34d399;background:#ecfdf5;padding:8px 14px;margin:10px 0;color:#374151;border-radius:0 6px 6px 0}
            ul,ol{margin:6px 0;padding-left:20px}
            li{margin:2px 0}
            hr{border:none;border-top:1.5px solid #d1d5db;margin:16px 0}
        </style>
        </head><body><h2>${title}</h2>${html}</body></html>`);
    printWindow.document.close();
    printWindow.print();
}

/** AI가 생성한 plain text를 기본 HTML로 변환 */
function textToHtml(text: string): string {
    if (!text) return "";
    if (text.trim().startsWith("<")) return text; // 이미 HTML이면 그대로

    return text.split("\n").map(line => {
        const trimmed = line.trim();
        if (!trimmed) return "";
        // 카테고리 제목: "1. xxx", "2. xxx" (단일 숫자 + 점)
        if (/^\d+\.\s/.test(trimmed) && !/^\d+-\d+/.test(trimmed)) return `<h2>${trimmed}</h2>`;
        // 소제목: "1-1. xxx", "2-3. xxx" (하이픈 포함 번호)
        if (/^\d+-\d+\.\s/.test(trimmed)) return `<h3>${trimmed}</h3>`;
        // ▶ ✔ 【 마커
        if (trimmed.startsWith("▶") || trimmed.startsWith("✔") || trimmed.startsWith("【"))
            return `<p><strong>${trimmed}</strong></p>`;
        // ─── 구분선
        if (/^[─━═]{3,}/.test(trimmed)) return `<hr/>`;
        return `<p>${trimmed}</p>`;
    }).join("");
}

export default function PortfolioPage() {
    const { token } = useAuth();
    const [projects, setProjects] = useState<Project[]>([]);
    const [loading, setLoading] = useState(true);
    const [expandedId, setExpandedId] = useState<number | null>(null);
    const [editContents, setEditContents] = useState<Record<number, string>>({});
    const [generating, setGenerating] = useState<Record<number, boolean>>({});
    const [saving, setSaving] = useState<Record<number, boolean>>({});

    const load = useCallback(async () => {
        if (!token) return;
        try { setProjects(await projectsApi.list(token)); } catch { /* */ }
        finally { setLoading(false); }
    }, [token]);

    useEffect(() => { load(); }, [load]);

    const handleGenerate = async (projectId: number) => {
        if (!token) return;
        setGenerating(prev => ({ ...prev, [projectId]: true }));
        try {
            const { taskId } = await aiApi.asyncProjectPortfolio(token, projectId);

            const pollInterval = setInterval(async () => {
                try {
                    const res = await aiApi.asyncStatus(token, taskId);
                    if (res.status === "COMPLETED") {
                        clearInterval(pollInterval);
                        setGenerating(prev => ({ ...prev, [projectId]: false }));
                        const html = textToHtml(res.result || "");
                        setEditContents(prev => ({ ...prev, [projectId]: html }));
                        setExpandedId(projectId);
                        load();
                    } else if (res.status === "FAILED") {
                        clearInterval(pollInterval);
                        setGenerating(prev => ({ ...prev, [projectId]: false }));
                        alert("포트폴리오 생성 실패: " + (res.result || ""));
                    }
                } catch { /* polling error ignored */ }
            }, 3000);
        } catch {
            setGenerating(prev => ({ ...prev, [projectId]: false }));
            alert("포트폴리오 생성 요청 실패");
        }
    };

    const handleSave = async (projectId: number) => {
        if (!token) return;
        setSaving(prev => ({ ...prev, [projectId]: true }));
        try {
            const project = projects.find(p => p.id === projectId);
            const content = editContents[projectId] !== undefined
                ? editContents[projectId]
                : (project?.aiPortfolioContent || "");
            await projectsApi.updatePortfolio(token, projectId, content);
            await load();
        } catch { /* */ }
        setSaving(prev => ({ ...prev, [projectId]: false }));
    };

    const handleImageUpload = useCallback(async (projectId: number, file: File): Promise<string> => {
        if (!token) throw new Error("인증 필요");
        const { imageUrl } = await projectsApi.uploadPortfolioImage(token, projectId, file);
        // 상대 경로를 절대 경로로 변환
        if (imageUrl.startsWith("/uploads")) {
            const apiBase = API_BASE.replace("/api/v1", "");
            return `${apiBase}${imageUrl}`;
        }
        return imageUrl;
    }, [token]);

    const getContent = (p: Project) => {
        if (editContents[p.id] !== undefined) return editContents[p.id];
        return textToHtml(p.aiPortfolioContent || "");
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold">포트폴리오 관리</h1>
                <p className="text-sm text-muted-foreground">프로젝트별 AI 포트폴리오를 생성하고 관리합니다.</p>
            </div>

            {loading ? (
                <div className="space-y-4">
                    {Array.from({ length: 2 }).map((_, i) => (
                        <div key={i} className="border rounded-lg p-5 space-y-3 animate-pulse">
                            <div className="flex justify-between">
                                <div className="h-6 w-1/3 bg-muted/50 rounded" />
                                <div className="flex gap-2">
                                    <div className="h-8 w-16 bg-muted/50 rounded" />
                                    <div className="h-8 w-16 bg-muted/50 rounded" />
                                </div>
                            </div>
                            <div className="flex gap-2">
                                {Array.from({ length: 5 }).map((_, j) => (
                                    <div key={j} className="h-6 w-20 bg-muted/50 rounded-full" />
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            ) : projects.length === 0 ? (
                <p className="text-center text-muted-foreground py-12">
                    등록된 프로젝트가 없습니다. 프로젝트를 먼저 추가해주세요.
                </p>
            ) : (
                <div className="space-y-4">
                    {projects.map(p => {
                        const content = getContent(p);
                        const isExpanded = expandedId === p.id;
                        const isGenerating = generating[p.id];
                        const hasContent = stripHtml(content).length > 0;

                        return (
                            <Card key={p.id}>
                                <CardHeader>
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-3">
                                            <CardTitle className="text-lg">{p.name}</CardTitle>
                                            {hasContent && (
                                                <Badge className="bg-emerald-600 text-xs">생성됨</Badge>
                                            )}
                                            {!hasContent && !isGenerating && (
                                                <Badge variant="outline" className="text-xs">미생성</Badge>
                                            )}
                                        </div>
                                        <div className="flex gap-2">
                                            {hasContent && (
                                                <Button variant="outline" size="sm"
                                                    onClick={() => setExpandedId(isExpanded ? null : p.id)}>
                                                    {isExpanded ? "접기" : "펼치기"}
                                                </Button>
                                            )}
                                            <Button
                                                size="sm"
                                                className="bg-emerald-600 hover:bg-emerald-700"
                                                disabled={isGenerating}
                                                onClick={() => handleGenerate(p.id)}>
                                                {isGenerating ? "생성중..." : hasContent ? "재생성" : "AI 포트폴리오 생성"}
                                            </Button>
                                        </div>
                                    </div>
                                    {p.techStack && (
                                        <div className="flex flex-wrap gap-1 mt-2">
                                            {p.techStack.split(",").map((t, i) => (
                                                <Badge key={`${p.id}-${t.trim()}-${i}`} variant="outline" className="text-xs">
                                                    {t.trim()}
                                                </Badge>
                                            ))}
                                        </div>
                                    )}
                                </CardHeader>

                                {isExpanded && hasContent && (
                                    <CardContent className="space-y-3">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs text-muted-foreground">
                                                {stripHtml(content).length}자
                                                <span className="ml-2 text-emerald-600">이미지: 드래그, 붙여넣기, 또는 IMG 버튼</span>
                                            </span>
                                            <div className="flex gap-2">
                                                <Button variant="outline" size="sm" onClick={() => {
                                                    navigator.clipboard.writeText(stripHtml(content));
                                                }}>복사</Button>
                                                <Button variant="outline" size="sm" onClick={() =>
                                                    downloadPdf(content, `포트폴리오_${p.name}.pdf`, `포트폴리오 - ${p.name}`)
                                                }>PDF</Button>
                                                <Button variant="outline" size="sm"
                                                    disabled={saving[p.id]}
                                                    onClick={() => handleSave(p.id)}>
                                                    {saving[p.id] ? "저장중..." : "저장"}
                                                </Button>
                                                <Button variant="outline" size="sm"
                                                    className="text-red-500 hover:text-red-700"
                                                    onClick={async () => {
                                                        if (!confirm("포트폴리오를 삭제하시겠습니까?")) return;
                                                        if (!token) return;
                                                        await projectsApi.updatePortfolio(token, p.id, "");
                                                        setEditContents(prev => ({ ...prev, [p.id]: "" }));
                                                        setExpandedId(null);
                                                        await load();
                                                    }}>삭제</Button>
                                            </div>
                                        </div>
                                        <RichEditor
                                            content={content}
                                            onChange={(html) => setEditContents(prev => ({ ...prev, [p.id]: html }))}
                                            onImageUpload={(file) => handleImageUpload(p.id, file)}
                                            placeholder="AI가 포트폴리오를 생성하면 여기에 표시됩니다. 직접 편집하거나 이미지를 추가할 수 있습니다."
                                        />
                                    </CardContent>
                                )}
                            </Card>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
