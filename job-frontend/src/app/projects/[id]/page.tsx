"use client";
export const runtime = "edge";

import { useState, useEffect, use, useRef } from "react";
import { projectsApi, aiApi, Project } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Markdown } from "@/components/ui/markdown";
import dynamic from "next/dynamic";

const MermaidDiagram = dynamic(() => import("@/components/ui/mermaid-diagram"), { ssr: false });

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

export default function ProjectDetailPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const isNew = id === "new";
    const router = useRouter();
    const { token } = useAuth();
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [githubUrl, setGithubUrl] = useState("");
    const [notionUrl, setNotionUrl] = useState("");
    const [techStack, setTechStack] = useState("");
    const [images, setImages] = useState<string[]>([]);
    const [aiSummary, setAiSummary] = useState("");
    const [aiAnalysis, setAiAnalysis] = useState<{ keyFeatures?: string[]; architecture?: string } | null>(null);
    const [diagramPrompt, setDiagramPrompt] = useState("");
    const [mermaidCodes, setMermaidCodes] = useState<{ architecture?: string; feature?: string; sequence?: string }>({});
    const [diagramTab, setDiagramTab] = useState<"prompt" | "mermaid">("mermaid");
    const [saving, setSaving] = useState(false);
    const [analyzing, setAnalyzing] = useState(false);
    const setAnalyzingPersist = (v: boolean, url?: string) => {
        setAnalyzing(v);
        if (v) localStorage.setItem(`ai-project-${id}`, JSON.stringify({ ts: Date.now(), githubUrl: url || "" }));
        else localStorage.removeItem(`ai-project-${id}`);
    };
    useEffect(() => {
        try {
            const s = localStorage.getItem(`ai-project-${id}`);
            if (!s) return;
            const { ts, githubUrl: savedUrl, taskId } = JSON.parse(s);
            if (Date.now() - ts >= 5 * 60 * 1000) {
                localStorage.removeItem(`ai-project-${id}`);
                return;
            }
            setAnalyzing(true);
            if (savedUrl) setGithubUrl(savedUrl);
            setMessage("AI가 GitHub 프로젝트를 분석하고 있습니다...");

            // taskId가 있으면 폴링 재시작
            if (taskId && token) {
                const pollInterval = setInterval(async () => {
                    try {
                        const res = await aiApi.asyncStatus(token, taskId);
                        if (res.status === "COMPLETED" && res.result) {
                            clearInterval(pollInterval);
                            try {
                                let jsonStr = res.result;
                                if (jsonStr.includes("```json")) {
                                    jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                                    jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
                                } else if (jsonStr.includes("```")) {
                                    jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                                    jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
                                }
                                jsonStr = jsonStr.trim();
                                if (jsonStr.startsWith("{")) {
                                    const parsed = JSON.parse(jsonStr);
                                    applyAnalysisResult({ ...parsed, summary: res.result });
                                }
                            } catch {
                                setDescription(res.result);
                            }
                            setAnalyzingPersist(false);
                            setMessage("GitHub 분석 완료! 프로젝트명, 설명, 기술스택이 자동으로 채워졌습니다.");
                        } else if (res.status === "FAILED") {
                            clearInterval(pollInterval);
                            setAnalyzingPersist(false);
                            setMessage("분석 실패: " + (res.result || ""));
                        }
                    } catch { /* 폴링 에러 무시 */ }
                }, 3000);
                return () => clearInterval(pollInterval);
            }
        } catch { localStorage.removeItem(`ai-project-${id}`); }
    }, [id, token]);
    const [message, setMessage] = useState("");

    useEffect(() => {
        if (isNew || !token) return;
        projectsApi.get(token, Number(id)).then(p => {
            setName(p.name);
            setDescription(p.description || "");
            setGithubUrl(p.githubUrl || "");
            setNotionUrl(p.notionUrl || "");
            setTechStack(p.techStack || "");
            setImages(p.imageUrls || []);
            setAiSummary(p.aiSummary || "");
            // 저장된 AI 분석 결과 복원
            if (p.aiSummary) {
                try {
                    const parsed = JSON.parse(p.aiSummary);
                    if (parsed.keyFeatures || parsed.architecture) {
                        setAiAnalysis({ keyFeatures: parsed.keyFeatures, architecture: parsed.architecture });
                    }
                    if (parsed.architectureDiagramPrompt || parsed.featureDiagramPrompt) {
                        setDiagramPrompt(
                            (parsed.architectureDiagramPrompt ? "[ 아키텍처 다이어그램 ]\n" + parsed.architectureDiagramPrompt : "")
                            + (parsed.architectureDiagramPrompt && parsed.featureDiagramPrompt ? "\n\n" : "")
                            + (parsed.featureDiagramPrompt ? "[ 기능 흐름 다이어그램 ]\n" + parsed.featureDiagramPrompt : "")
                        );
                    } else if (parsed.diagramPrompt) {
                        setDiagramPrompt(parsed.diagramPrompt);
                    }
                    if (parsed.architectureMermaid || parsed.featureMermaid || parsed.sequenceMermaid) {
                        setMermaidCodes({
                            architecture: parsed.architectureMermaid || "",
                            feature: parsed.featureMermaid || "",
                            sequence: parsed.sequenceMermaid || "",
                        });
                    }
                } catch { /* aiSummary가 JSON이 아닌 경우 무시 */ }
            }
        }).catch(() => router.push("/projects"));
    }, [id, isNew, token, router]);

    const handleSave = async () => {
        if (!token || !name.trim()) return;
        setSaving(true);
        try {
            const data = { name, description, githubUrl, notionUrl, techStack, aiSummary };
            if (isNew) {
                await projectsApi.create(token, data);
                router.push("/projects");
            } else {
                await projectsApi.update(token, Number(id), data);
                router.push("/projects");
            }
        } catch { setMessage("저장 실패"); }
        finally { setSaving(false); }
    };

    const handleImageUpload = async (file: File) => {
        if (!token || isNew) return;
        try {
            const result = await projectsApi.uploadImage(token, Number(id), file);
            setImages(prev => [...prev, result.imageUrl]);
            setMessage("이미지 업로드 완료");
        } catch { setMessage("이미지 업로드 실패"); }
    };

    const applyAnalysisResult = async (data: Record<string, unknown>) => {
        const pName = (data.projectName as string) || name || githubUrl.replace(/\/$/, "").split("/").pop() || "";
        const pDesc = (data.description as string) || description || (data.summary as string) || "";
        const pTech = (data.techStack as string) || techStack || "";

        setName(pName);
        setDescription(pDesc);
        setTechStack(pTech);

        const analysis = {
            keyFeatures: (data.keyFeatures as string[]) || [],
            architecture: (data.architecture as string) || "",
            architectureDiagramPrompt: (data.architectureDiagramPrompt as string) || "",
            featureDiagramPrompt: (data.featureDiagramPrompt as string) || "",
        };
        setAiAnalysis(analysis);
        const summary = JSON.stringify(analysis);
        setAiSummary(summary);

        if (data.architectureDiagramPrompt || data.featureDiagramPrompt) {
            setDiagramPrompt(
                (data.architectureDiagramPrompt ? "[ 아키텍처 다이어그램 ]\n" + data.architectureDiagramPrompt : "")
                + (data.architectureDiagramPrompt && data.featureDiagramPrompt ? "\n\n" : "")
                + (data.featureDiagramPrompt ? "[ 기능 흐름 다이어그램 ]\n" + data.featureDiagramPrompt : "")
            );
        }

        // AI 분석 완료 시 자동 저장
        if (token && pName.trim()) {
            try {
                const saveData = { name: pName, description: pDesc, githubUrl, notionUrl, techStack: pTech, aiSummary: summary };
                if (isNew) {
                    await projectsApi.create(token, saveData);
                } else {
                    await projectsApi.update(token, Number(id), saveData);
                }
            } catch { /* 자동 저장 실패는 무시 — 수동 저장 가능 */ }
        }
    };

    const handleGitHubAnalyze = async () => {
        if (!token || !githubUrl.trim()) return;
        setAnalyzingPersist(true, githubUrl);
        setMessage("AI가 GitHub 프로젝트를 분석하고 있습니다...");
        try {
            const { taskId } = await aiApi.asyncProjectSummary(token, githubUrl);
            // localStorage에 taskId 저장 (새로고침 복구용)
            localStorage.setItem(`ai-project-${id}`, JSON.stringify({ ts: Date.now(), githubUrl, taskId }));

            // 폴링으로 완료 감지
            const pollInterval = setInterval(async () => {
                try {
                    const res = await aiApi.asyncStatus(token, taskId);
                    if (res.status === "COMPLETED" && res.result) {
                        clearInterval(pollInterval);
                        // JSON 파싱 시도
                        try {
                            let jsonStr = res.result;
                            if (jsonStr.includes("```json")) {
                                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
                            } else if (jsonStr.includes("```")) {
                                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
                            }
                            jsonStr = jsonStr.trim();
                            if (jsonStr.startsWith("{")) {
                                const parsed = JSON.parse(jsonStr);
                                applyAnalysisResult({ ...parsed, summary: res.result });
                            }
                        } catch {
                            setDescription(res.result);
                        }
                        setAnalyzingPersist(false);
                        setMessage("GitHub 분석 완료! 프로젝트명, 설명, 기술스택이 자동으로 채워졌습니다.");
                    } else if (res.status === "FAILED") {
                        clearInterval(pollInterval);
                        setAnalyzingPersist(false);
                        setMessage("분석 실패: " + (res.result || ""));
                    }
                } catch { /* 폴링 에러 무시 */ }
            }, 3000);
        } catch {
            setMessage("GitHub 분석 요청 실패");
            setAnalyzing(false);
        }
    };

    const handleDelete = async () => {
        if (!token || isNew || !confirm("정말 삭제하시겠습니까?")) return;
        await projectsApi.delete(token, Number(id));
        router.push("/projects");
    };

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Link href="/projects" className="hover:text-foreground">프로젝트</Link>
                <span>/</span>
                <span className="text-foreground">{isNew ? "새 프로젝트" : name}</span>
            </div>

            {/* 기본 정보 */}
            <Card>
                <CardHeader>
                    <CardTitle>{isNew ? "새 프로젝트 등록" : "프로젝트 편집"}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div>
                        <label className="text-sm font-medium mb-1 block">프로젝트명</label>
                        <Input value={name} onChange={e => setName(e.target.value)} placeholder="프로젝트 이름" />
                    </div>

                    <div>
                        <label className="text-sm font-medium mb-1 block">GitHub URL</label>
                        <div className="flex gap-2">
                            <Input value={githubUrl} onChange={e => setGithubUrl(e.target.value)}
                                placeholder="https://github.com/user/repo" className="flex-1" />
                            <Button onClick={handleGitHubAnalyze} disabled={analyzing || !githubUrl.trim()}
                                variant="outline">
                                {analyzing ? (
                                    <span className="flex items-center gap-2">
                                        <span className="animate-spin rounded-full h-4 w-4 border-2 border-current border-t-transparent" />
                                        분석 중...
                                    </span>
                                ) : "AI 자동 분석"}
                            </Button>
                        </div>
                        <p className="text-xs text-muted-foreground mt-1">
                            GitHub URL을 입력하고 "AI 자동 분석"을 누르면 README, 코드 구조를 분석해서 프로젝트 설명을 자동으로 채워줍니다.
                        </p>
                    </div>

                    <div>
                        <label className="text-sm font-medium mb-1 block">Notion URL</label>
                        <Input value={notionUrl} onChange={e => setNotionUrl(e.target.value)}
                            placeholder="https://notion.so/..." />
                    </div>

                    <div>
                        <label className="text-sm font-medium mb-1 block">기술 스택 (쉼표 구분)</label>
                        <Input value={techStack} onChange={e => setTechStack(e.target.value)}
                            placeholder="Java, Spring Boot, React, PostgreSQL" />
                        {techStack && (
                            <div className="flex flex-wrap gap-1 mt-2">
                                {techStack.split(",").map((t, i) => (
                                    <Badge key={i} variant="secondary" className="text-xs">{t.trim()}</Badge>
                                ))}
                            </div>
                        )}
                    </div>
                </CardContent>
            </Card>

            {/* 프로젝트 설명 (에디터) */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">프로젝트 설명</CardTitle>
                </CardHeader>
                <CardContent>
                    <textarea
                        value={description}
                        onChange={e => setDescription(e.target.value)}
                        placeholder="프로젝트에 대한 상세 설명을 작성하세요.&#10;&#10;주요 기능, 개발 배경, 본인의 역할, 사용 기술, 성과 등을 포함하면 AI가 더 좋은 포트폴리오를 생성할 수 있습니다."
                        className="w-full rounded-md border px-4 py-3 text-sm bg-background min-h-[300px] resize-y leading-relaxed"
                        rows={15}
                    />
                </CardContent>
            </Card>

            {/* 이미지 관리 */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle className="text-base">프로젝트 이미지</CardTitle>
                        {!isNew && (
                            <Button variant="outline" size="sm" onClick={() => fileInputRef.current?.click()}>
                                이미지 추가
                            </Button>
                        )}
                        <input ref={fileInputRef} type="file" accept="image/*" className="hidden"
                            onChange={e => {
                                const file = e.target.files?.[0];
                                if (file) handleImageUpload(file);
                                e.target.value = "";
                            }} />
                    </div>
                </CardHeader>
                <CardContent>
                    {isNew ? (
                        <p className="text-sm text-muted-foreground text-center py-4">
                            프로젝트를 먼저 저장한 후 이미지를 추가할 수 있습니다.
                        </p>
                    ) : images.length === 0 ? (
                        <p className="text-sm text-muted-foreground text-center py-4">
                            등록된 이미지가 없습니다. 스크린샷, 아키텍처 다이어그램 등을 추가하세요.
                        </p>
                    ) : (
                        <div className="space-y-4">
                            {images.map((url, i) => (
                                <div key={i} className="relative group">
                                    {/* eslint-disable-next-line @next/next/no-img-element */}
                                    <img src={`http://localhost:8080${url}`} alt={`이미지 ${i + 1}`}
                                        className="w-full h-auto rounded-md object-contain border" />
                                </div>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* AI 분석 결과 */}
            {aiAnalysis && (
                <Card className="bg-muted/50">
                    <CardHeader>
                        <CardTitle className="text-base">AI 프로젝트 분석</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        {aiAnalysis.keyFeatures && aiAnalysis.keyFeatures.length > 0 && (
                            <div>
                                <p className="text-sm font-medium mb-2">주요 기능</p>
                                <ul className="space-y-1.5">
                                    {aiAnalysis.keyFeatures.map((f, i) => (
                                        <li key={i} className="flex items-start gap-2 text-sm text-muted-foreground">
                                            <span className="text-green-400 mt-0.5">+</span>
                                            <span>{f}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                        {aiAnalysis.architecture && (
                            <div>
                                <p className="text-sm font-medium mb-2">아키텍처</p>
                                <div className="text-sm text-muted-foreground space-y-2">
                                    {aiAnalysis.architecture.split(/\n|\[/).filter(Boolean).map((line, i) => {
                                        const trimmed = line.trim();
                                        if (!trimmed) return null;
                                        const labelMatch = trimmed.match(/^(계층 구조|디자인 패턴|데이터 저장|성능|보안|배포)]\s*(.*)/);
                                        if (labelMatch) {
                                            return (
                                                <div key={i}>
                                                    <span className="font-medium text-foreground">{labelMatch[1]}</span>
                                                    <span className="ml-1">{labelMatch[2]}</span>
                                                </div>
                                            );
                                        }
                                        return <p key={i}>{trimmed.replace(/^]/, '')}</p>;
                                    })}
                                </div>
                            </div>
                        )}
                    </CardContent>
                </Card>
            )}

            {/* 다이어그램 */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle className="text-base">다이어그램</CardTitle>
                        <div className="flex gap-2">
                            <Button variant="outline" size="sm"
                                disabled={analyzing || !name.trim()}
                                onClick={async () => {
                                    if (!token || isNew) return;
                                    setAnalyzingPersist(true);
                                    setMessage("텍스트 프롬프트 생성 중...");
                                    try {
                                        const res = await projectsApi.generateDiagramPrompt(token, Number(id));
                                        const parts: string[] = [];
                                        if (res.architectureDiagramPrompt) parts.push("[ 아키텍처 다이어그램 ]\n" + res.architectureDiagramPrompt);
                                        if (res.featureDiagramPrompt) parts.push("[ 기능 흐름 다이어그램 ]\n" + res.featureDiagramPrompt);
                                        if (res.sequenceDiagramPrompt) parts.push("[ 시퀀스 다이어그램 ]\n" + res.sequenceDiagramPrompt);
                                        if (res.raw && parts.length === 0) parts.push(res.raw);
                                        setDiagramPrompt(parts.join("\n\n"));
                                        setDiagramTab("prompt");
                                        try {
                                            const current = aiSummary ? JSON.parse(aiSummary) : {};
                                            current.architectureDiagramPrompt = res.architectureDiagramPrompt || "";
                                            current.featureDiagramPrompt = res.featureDiagramPrompt || "";
                                            current.sequenceDiagramPrompt = res.sequenceDiagramPrompt || "";
                                            setAiSummary(JSON.stringify(current));
                                        } catch {}
                                        setMessage("텍스트 프롬프트 생성 완료");
                                        setAnalyzingPersist(false);
                                    } catch { setMessage("텍스트 프롬프트 생성 실패"); setAnalyzing(false); }
                                }}>
                                {analyzing ? "생성중..." : "텍스트 프롬프트"}
                            </Button>
                            <Button variant="outline" size="sm"
                                className="text-indigo-600 border-indigo-300 hover:bg-indigo-50 dark:hover:bg-indigo-950"
                                disabled={analyzing || !name.trim()}
                                onClick={async () => {
                                    if (!token || isNew) return;
                                    setAnalyzingPersist(true);
                                    setMessage("Mermaid 다이어그램 생성 중...");
                                    try {
                                        const res = await projectsApi.generateDiagramMermaid(token, Number(id));
                                        setMermaidCodes({
                                            architecture: res.architectureMermaid || "",
                                            feature: res.featureMermaid || "",
                                            sequence: res.sequenceMermaid || "",
                                        });
                                        setDiagramTab("mermaid");
                                        try {
                                            const current = aiSummary ? JSON.parse(aiSummary) : {};
                                            current.architectureMermaid = res.architectureMermaid || "";
                                            current.featureMermaid = res.featureMermaid || "";
                                            current.sequenceMermaid = res.sequenceMermaid || "";
                                            setAiSummary(JSON.stringify(current));
                                        } catch {}
                                        setMessage("Mermaid 다이어그램 생성 완료");
                                        setAnalyzingPersist(false);
                                    } catch { setMessage("Mermaid 다이어그램 생성 실패"); setAnalyzing(false); }
                                }}>
                                {analyzing ? "생성중..." : "Mermaid 다이어그램"}
                            </Button>
                        </div>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    {(diagramPrompt || mermaidCodes.architecture) && (
                        <div className="flex gap-1 border-b">
                            <button
                                className={`px-3 py-1.5 text-sm font-medium border-b-2 transition-colors ${
                                    diagramTab === "mermaid" ? "border-emerald-600 text-emerald-600" : "border-transparent text-muted-foreground hover:text-foreground"
                                }`}
                                onClick={() => setDiagramTab("mermaid")}>
                                Mermaid 다이어그램
                            </button>
                            <button
                                className={`px-3 py-1.5 text-sm font-medium border-b-2 transition-colors ${
                                    diagramTab === "prompt" ? "border-emerald-600 text-emerald-600" : "border-transparent text-muted-foreground hover:text-foreground"
                                }`}
                                onClick={() => setDiagramTab("prompt")}>
                                텍스트 프롬프트
                            </button>
                        </div>
                    )}

                    {diagramTab === "mermaid" && (mermaidCodes.architecture || mermaidCodes.feature || mermaidCodes.sequence) && (
                        <div className="space-y-6">
                            {mermaidCodes.architecture && <MermaidDiagram code={mermaidCodes.architecture} title="아키텍처 다이어그램" />}
                            {mermaidCodes.feature && <MermaidDiagram code={mermaidCodes.feature} title="기능 흐름 다이어그램" />}
                            {mermaidCodes.sequence && <MermaidDiagram code={mermaidCodes.sequence} title="시퀀스 다이어그램" />}
                        </div>
                    )}

                    {diagramTab === "prompt" && diagramPrompt && (
                        <div className="space-y-2">
                            <div className="flex justify-end">
                                <Button variant="outline" size="sm" onClick={() => {
                                    navigator.clipboard.writeText(diagramPrompt);
                                    setMessage("클립보드에 복사되었습니다.");
                                }}>전체 복사</Button>
                            </div>
                            <textarea
                                className="w-full p-3 border rounded-md text-sm resize-y bg-muted/30 font-sans leading-relaxed"
                                style={{ minHeight: "200px" }}
                                value={diagramPrompt}
                                readOnly
                            />
                            <p className="text-xs text-muted-foreground">
                                ChatGPT, Eraser.io 등에 붙여넣으면 다이어그램 이미지가 생성됩니다.
                            </p>
                        </div>
                    )}

                    {!diagramPrompt && !mermaidCodes.architecture && (
                        <p className="text-sm text-muted-foreground text-center py-4">
                            버튼을 눌러 다이어그램을 생성하세요.
                        </p>
                    )}
                </CardContent>
            </Card>



            <Separator />

            {/* 하단 버튼 */}
            <div className="flex items-center justify-between">
                <div className="flex gap-2">
                    <Button onClick={handleSave} disabled={saving || !name.trim()}
                        className="bg-emerald-600 hover:bg-emerald-700">
                        {saving ? "저장 중..." : isNew ? "프로젝트 등록" : "변경사항 저장"}
                    </Button>
                    <Button variant="outline" onClick={() => router.push("/projects")}>취소</Button>
                </div>
                {!isNew && (
                    <Button variant="outline" className="text-red-500" onClick={handleDelete}>
                        프로젝트 삭제
                    </Button>
                )}
            </div>

            {message && (
                <p className={`text-sm ${message.includes("실패") ? "text-red-500" : "text-emerald-500"}`}>
                    {message}
                </p>
            )}
        </div>
    );
}
