"use client";
export const runtime = "edge";

import { useState, useEffect, use } from "react";
import { coverLettersApi, aiApi, templatesApi, CoverLetterItem } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import Link from "next/link";

/** 크롤링된 텍스트의 불필요한 줄바꿈 정리. 빈 줄(문단)은 유지, 짧은 줄바꿈은 합침 */
function normalizeLineBreaks(text: string): string {
    if (!text) return "";
    const lines = text.split("\n");
    const result: string[] = [];
    let buffer = "";

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();

        // 빈 줄 = 문단 구분
        if (!trimmed) {
            if (buffer) { result.push(buffer.trim()); buffer = ""; }
            result.push("");
            continue;
        }

        // 문단 시작 패턴 (번호, 첫째/둘째, [제목], 소제목 등) → 새 문단
        if (/^(\d+\.|첫째|둘째|셋째|넷째|다섯째|\[)/.test(trimmed) && buffer) {
            result.push(buffer.trim());
            buffer = "";
        }

        // 이전 줄이 짧게 끊긴 경우 (60자 미만 + 문장 안 끝남) → 합치기
        buffer = buffer ? buffer + " " + trimmed : trimmed;
    }
    if (buffer) result.push(buffer.trim());

    return result.join("\n");
}

type AnalysisResult = {
    structure?: string[];
    pattern?: string;
    keywords?: string[];
    strengths?: string[];
    template?: string;
};

export default function CoverLetterDetailPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const { token } = useAuth();
    const [item, setItem] = useState<CoverLetterItem | null>(null);
    const [analyzing, setAnalyzing] = useState(false);
    const setAnalyzingPersist = (v: boolean) => {
        setAnalyzing(v);
        if (v) localStorage.setItem(`ai-cl-analyze-${id}`, JSON.stringify({ ts: Date.now() }));
        else localStorage.removeItem(`ai-cl-analyze-${id}`);
    };
    useEffect(() => {
        try {
            const s = localStorage.getItem(`ai-cl-analyze-${id}`);
            if (s) { const { ts } = JSON.parse(s); if (Date.now() - ts < 5 * 60 * 1000) setAnalyzing(true); else localStorage.removeItem(`ai-cl-analyze-${id}`); }
        } catch { localStorage.removeItem(`ai-cl-analyze-${id}`); }
    }, [id]);
    const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);
    const [savingTemplate, setSavingTemplate] = useState(false);
    const [message, setMessage] = useState("");

    useEffect(() => {
        coverLettersApi.get(Number(id)).then(setItem).catch(console.error);
    }, [id]);

    const handleAnalyze = async () => {
        if (!token) return;
        setAnalyzingPersist(true);
        setMessage("");
        try {
            const result = await aiApi.analyzeCoverLetter(token, Number(id));
            setAnalysis({
                structure: result.structure,
                pattern: result.pattern,
                keywords: result.keywords,
                strengths: result.strengths,
                template: result.template,
            });
            setAnalyzingPersist(false);
        } catch {
            setMessage("AI 분석 실패");
            // localStorage는 지우지 않음 — 새로고침 후에도 비활성 유지
            // useEffect에서 5분 만료로 자동 해제
            setAnalyzing(false);
        }
    };

    const handleSaveAsTemplate = async () => {
        if (!token || !analysis?.template || !item) return;
        setSavingTemplate(true);
        try {
            const name = `${item.company} ${item.position || ""} 패턴`.trim();
            await templatesApi.create(token, {
                name,
                type: "COVER_LETTER",
                content: analysis.template,
            });
            setMessage("템플릿 저장 완료!");
            setTimeout(() => setMessage(""), 3000);
        } catch {
            setMessage("템플릿 저장 실패");
        } finally {
            setSavingTemplate(false);
        }
    };

    if (!item) {
        return <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-orange-500" />
        </div>;
    }

    const contentSections = splitBySections(item.content);

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Link href="/cover-letters" className="hover:text-foreground">합격 자소서</Link>
                <span>/</span>
                <span className="text-foreground line-clamp-1">{item.company}</span>
            </div>

            {/* 헤더: 기업/직무 정보 */}
            <Card className="border-orange-500/30">
                <CardHeader className="pb-3">
                    <div className="space-y-1">
                        <p className="text-xs text-orange-500 font-medium">합격 자소서</p>
                        <CardTitle className="text-xl">{item.company}</CardTitle>
                        {item.position && (
                            <p className="text-muted-foreground">{item.position}</p>
                        )}
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="flex gap-2 flex-wrap">
                        {item.period && <Badge className="bg-orange-600">{item.period}</Badge>}
                        {item.companyType && <Badge variant="outline">{item.companyType}</Badge>}
                        {item.careerType && <Badge variant="secondary">{item.careerType}</Badge>}
                    </div>

                    <Separator />

                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                        {item.school && <InfoItem label="학교" value={item.school} />}
                        {item.major && <InfoItem label="전공" value={item.major} />}
                        {item.gpa && <InfoItem label="학점" value={item.gpa} />}
                        <InfoItem label="등록일" value={item.createdAt?.split("T")[0] || "-"} />
                    </div>

                    {item.specs && (
                        <div className="p-3 rounded-lg bg-muted/50">
                            <p className="text-xs text-muted-foreground mb-1">스펙 / 활동 / 자격증</p>
                            <p className="text-sm">{item.specs}</p>
                        </div>
                    )}

                    <div className="flex gap-2">
                        <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">
                            <Button variant="outline" className="border-orange-500/50 text-orange-600 hover:bg-orange-50">
                                원문 보기 (링커리어)
                            </Button>
                        </a>
                        {token && (
                            <Button
                                onClick={handleAnalyze}
                                disabled={analyzing}
                                className="bg-blue-600 hover:bg-blue-700"
                            >
                                {analyzing ? "AI 분석 중..." : "AI 패턴 분석"}
                            </Button>
                        )}
                    </div>
                    {message && (
                        <p className={`text-sm ${message.includes("실패") ? "text-red-500" : "text-emerald-500"}`}>
                            {message}
                        </p>
                    )}
                </CardContent>
            </Card>

            {/* AI 분석 결과 */}
            {analysis && (
                <Card className="border-blue-500/30">
                    <CardHeader className="pb-3">
                        <div className="flex items-center justify-between">
                            <CardTitle className="text-lg">AI 패턴 분석</CardTitle>
                            {analysis.template && (
                                <Button
                                    size="sm"
                                    onClick={handleSaveAsTemplate}
                                    disabled={savingTemplate}
                                    className="bg-emerald-600 hover:bg-emerald-700"
                                >
                                    {savingTemplate ? "저장 중..." : "템플릿으로 저장"}
                                </Button>
                            )}
                        </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        {analysis.pattern && (
                            <div>
                                <p className="text-sm font-medium mb-1">작성 패턴</p>
                                <p className="text-sm text-muted-foreground">{analysis.pattern}</p>
                            </div>
                        )}

                        {analysis.structure && analysis.structure.length > 0 && (
                            <div>
                                <p className="text-sm font-medium mb-2">문단 구조</p>
                                <ol className="space-y-1.5">
                                    {analysis.structure.map((s, i) => (
                                        <li key={i} className="flex items-start gap-2 text-sm text-muted-foreground">
                                            <span className="text-blue-400 font-medium min-w-[20px]">{i + 1}.</span>
                                            <span>{s}</span>
                                        </li>
                                    ))}
                                </ol>
                            </div>
                        )}

                        {analysis.keywords && analysis.keywords.length > 0 && (
                            <div>
                                <p className="text-sm font-medium mb-2">핵심 키워드</p>
                                <div className="flex gap-2 flex-wrap">
                                    {analysis.keywords.map((k, i) => (
                                        <Badge key={i} variant="outline" className="border-blue-500/50 text-blue-400">
                                            {k}
                                        </Badge>
                                    ))}
                                </div>
                            </div>
                        )}

                        {analysis.strengths && analysis.strengths.length > 0 && (
                            <div>
                                <p className="text-sm font-medium mb-2">합격 포인트</p>
                                <ul className="space-y-1.5">
                                    {analysis.strengths.map((s, i) => (
                                        <li key={i} className="flex items-start gap-2 text-sm text-muted-foreground">
                                            <span className="text-emerald-400 mt-0.5">+</span>
                                            <span>{s}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}

                        {analysis.template && (
                            <div>
                                <p className="text-sm font-medium mb-1">생성된 템플릿</p>
                                <pre className="text-sm text-muted-foreground whitespace-pre-wrap bg-muted/50 p-3 rounded-lg font-sans">
                                    {analysis.template}
                                </pre>
                            </div>
                        )}
                    </CardContent>
                </Card>
            )}

            {/* 본문: 문항별 분리 */}
            {contentSections.length > 1 ? (
                contentSections.map((section, i) => (
                    <Card key={i}>
                        <CardContent className="p-5">
                            <pre className="whitespace-pre-wrap text-sm leading-relaxed font-sans">{normalizeLineBreaks(section)}</pre>
                        </CardContent>
                    </Card>
                ))
            ) : (
                <Card>
                    <CardHeader><CardTitle className="text-lg">자기소개서 본문</CardTitle></CardHeader>
                    <CardContent>
                        <pre className="whitespace-pre-wrap text-sm leading-relaxed font-sans">{normalizeLineBreaks(item.content)}</pre>
                    </CardContent>
                </Card>
            )}
        </div>
    );
}

function splitBySections(content: string): string[] {
    const pattern = /\n(?=\d+\.\s)/g;
    const parts = content.split(pattern).filter(s => s.trim().length > 10);
    return parts.length > 1 ? parts : [content];
}

function InfoItem({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="text-sm font-medium">{value}</p>
        </div>
    );
}
