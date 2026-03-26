"use client";

import { useState, useEffect, useCallback } from "react";
import { resumeApi, Resume } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import BasicInfoSection from "@/components/resume/BasicInfoSection";
import IntroductionSection from "@/components/resume/IntroductionSection";
import EducationSection from "@/components/resume/EducationSection";
import CareerSection from "@/components/resume/CareerSection";
import SkillSection from "@/components/resume/SkillSection";
import CertificationSection from "@/components/resume/CertificationSection";
import LanguageSection from "@/components/resume/LanguageSection";
import ActivitySection from "@/components/resume/ActivitySection";
import PortfolioLinkSection from "@/components/resume/PortfolioLinkSection";
import DesiredConditionsSection from "@/components/resume/DesiredConditionsSection";
import SyncSection from "@/components/resume/SyncSection";

const SITE_TABS = [
    { key: "MASTER", label: "내 이력서", color: "bg-emerald-600" },
    { key: "SARAMIN", label: "사람인", color: "bg-blue-600" },
    { key: "JOBKOREA", label: "잡코리아", color: "bg-red-600" },
    { key: "JOBPLANET", label: "잡플래닛", color: "bg-purple-600" },
    { key: "LINKAREER", label: "링커리어", color: "bg-teal-600" },
];

export default function ResumePage() {
    const { token } = useAuth();
    const [resume, setResume] = useState<Resume | null>(null);
    const [siteResumes, setSiteResumes] = useState<Record<string, Resume[]>>({});
    const [activeTab, setActiveTab] = useState("MASTER");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const loadResume = useCallback(async () => {
        if (!token) return;
        try {
            const data = await resumeApi.get(token);
            setResume(data);
            setError("");
        } catch (e) {
            console.error(e);
            setError("이력서를 불러오는데 실패했습니다.");
        } finally {
            setLoading(false);
        }
    }, [token]);

    const loadSiteResumes = useCallback(async () => {
        if (!token) return;
        try {
            const all = await resumeApi.getAllResumes(token);
            const map: Record<string, Resume[]> = {};
            for (const r of all) {
                const site = (r as any).sourceSite;
                if (site) {
                    if (!map[site]) map[site] = [];
                    map[site].push(r);
                }
            }
            setSiteResumes(map);
        } catch {
            // 사이트별 이력서 로드 실패는 무시
        }
    }, [token]);

    useEffect(() => {
        loadResume();
        loadSiteResumes();
    }, [loadResume, loadSiteResumes]);

    if (!token) {
        return (
            <div className="max-w-2xl mx-auto py-12 text-center">
                <p className="text-muted-foreground">로그인이 필요합니다.</p>
            </div>
        );
    }

    if (loading) {
        return (
            <div className="max-w-2xl mx-auto py-12 text-center">
                <p className="text-muted-foreground">이력서를 불러오는 중...</p>
            </div>
        );
    }

    if (error || !resume) {
        return (
            <div className="max-w-2xl mx-auto py-12 text-center">
                <p className="text-red-500">{error || "이력서를 불러올 수 없습니다."}</p>
            </div>
        );
    }

    const currentSiteResumes = activeTab === "MASTER" ? null : (siteResumes[activeTab] || []);
    const isMaster = activeTab === "MASTER";

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <h1 className="text-2xl font-bold">이력서 관리</h1>

            {/* 탭 */}
            <div className="flex flex-wrap gap-2">
                {SITE_TABS.map(tab => {
                    const isActive = activeTab === tab.key;
                    const hasSiteResume = tab.key === "MASTER" || (siteResumes[tab.key]?.length ?? 0) > 0;
                    return (
                        <button
                            key={tab.key}
                            onClick={() => setActiveTab(tab.key)}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                isActive
                                    ? `${tab.color} text-white`
                                    : hasSiteResume
                                        ? "bg-muted text-foreground hover:bg-muted/80"
                                        : "bg-muted/50 text-muted-foreground hover:bg-muted/80"
                            }`}
                        >
                            {tab.label}
                            {tab.key !== "MASTER" && hasSiteResume && (
                                <span className="ml-1 text-xs opacity-70">{siteResumes[tab.key]?.length ?? 0}</span>
                            )}
                            {tab.key !== "MASTER" && !hasSiteResume && (
                                <span className="ml-1 text-xs opacity-60">미등록</span>
                            )}
                        </button>
                    );
                })}
            </div>

            {/* 사이트별 이력서 내용 */}
            {isMaster ? (
                <>
                    <BasicInfoSection resume={resume} token={token} onUpdate={loadResume} />
                    <IntroductionSection resume={resume} token={token} onUpdate={loadResume} />
                    <EducationSection resume={resume} token={token} onUpdate={loadResume} />
                    <CareerSection resume={resume} token={token} onUpdate={loadResume} />
                    <SkillSection resume={resume} token={token} onUpdate={loadResume} />
                    <CertificationSection resume={resume} token={token} onUpdate={loadResume} />
                    <LanguageSection resume={resume} token={token} onUpdate={loadResume} />
                    <ActivitySection resume={resume} token={token} onUpdate={loadResume} />
                    <PortfolioLinkSection resume={resume} token={token} onUpdate={loadResume} />
                    <DesiredConditionsSection resume={resume} token={token} onUpdate={loadResume} />
                    <SyncSection token={token} onImportComplete={() => { loadResume(); loadSiteResumes(); }} />
                </>
            ) : currentSiteResumes && currentSiteResumes.length > 0 ? (
                <div className="space-y-6">
                    {currentSiteResumes.map((sr, idx) => {
                        const reloadSite = async () => {
                            // 사이트 이력서 다시 로드
                            const fresh = await resumeApi.getById(token, sr.id);
                            setSiteResumes(prev => ({
                                ...prev,
                                [activeTab]: prev[activeTab]?.map(r => r.id === sr.id ? fresh : r) ?? [],
                            }));
                        };
                        return (
                            <div key={sr.id}>
                                {currentSiteResumes.length > 1 && (
                                    <p className="text-sm font-medium text-muted-foreground mb-2">
                                        이력서 {idx + 1} {(sr as any).resumeTitle && `- ${(sr as any).resumeTitle}`}
                                    </p>
                                )}
                                <div className="mb-2">
                                    <Badge variant="outline">
                                        {SITE_TABS.find(t => t.key === activeTab)?.label} Import - 편집 가능
                                    </Badge>
                                </div>
                                <BasicInfoSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <IntroductionSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <EducationSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <CareerSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <SkillSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <CertificationSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <LanguageSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <ActivitySection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <PortfolioLinkSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                                <DesiredConditionsSection resume={sr} token={token} onUpdate={reloadSite} resumeId={sr.id} />
                            </div>
                        );
                    })}
                </div>
            ) : (
                <Card>
                    <CardContent className="py-12 text-center">
                        <p className="text-muted-foreground mb-2">
                            {SITE_TABS.find(t => t.key === activeTab)?.label}에서 가져온 이력서가 없습니다.
                        </p>
                        <p className="text-sm text-muted-foreground">
                            이력서 페이지 하단의 "이력서 가져오기"에서 Import 하세요.
                        </p>
                    </CardContent>
                </Card>
            )}
        </div>
    );
}

