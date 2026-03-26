"use client";

import { useState, useEffect } from "react";
import { applicationsApi, JobApplication } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
    Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog";
import Link from "next/link";

const statusMap: Record<string, { label: string; color: string }> = {
    DRAFT: { label: "작성 중", color: "bg-gray-500" },
    READY: { label: "준비 완료", color: "bg-blue-500" },
    APPLIED: { label: "지원 완료", color: "bg-emerald-600" },
    MANUALLY_APPLIED: { label: "수동 지원", color: "bg-teal-600" },
    FAILED: { label: "실패", color: "bg-red-500" },
    VERIFIED: { label: "확인됨", color: "bg-green-600" },
};

export default function ApplicationsPage() {
    const [apps, setApps] = useState<JobApplication[]>([]);
    const { token } = useAuth();

    const load = async () => {
        if (!token) return;
        try {
            const data = await applicationsApi.list(token);
            setApps(data.content);
        } catch { /* */ }
    };

    useEffect(() => { load(); }, [token]);

    const handleSubmit = async (id: number) => {
        if (!token) return;
        try { await applicationsApi.submit(token, id); load(); } catch { /* */ }
    };

    const handleManualApply = async (id: number) => {
        if (!token) return;
        try { await applicationsApi.manualApply(token, id); load(); } catch { /* */ }
    };

    const handleRetry = async (id: number) => {
        if (!token) return;
        try { await applicationsApi.retry(token, id); load(); } catch { /* */ }
    };

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold">지원 이력</h1>

            {apps.length === 0 ? (
                <p className="text-center text-muted-foreground py-12">지원 이력이 없습니다.</p>
            ) : (
                <div className="space-y-3">
                    {apps.map(app => {
                        const st = statusMap[app.status] || { label: app.status, color: "bg-gray-500" };
                        return (
                            <Card key={app.id} className="hover:shadow-md transition-shadow">
                                <CardContent className="flex items-center justify-between py-4">
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2">
                                            <span className="font-medium truncate">{app.jobPosting?.title}</span>
                                            <Badge className={st.color}>{st.label}</Badge>
                                        </div>
                                        <p className="text-sm text-muted-foreground mt-1">
                                            {app.jobPosting?.company} · {new Date(app.createdAt).toLocaleDateString("ko")}
                                        </p>
                                    </div>
                                    <div className="flex gap-2 shrink-0">
                                        {["APPLIED", "MANUALLY_APPLIED", "VERIFIED"].includes(app.status) ? (
                                            <PreviewDialog app={app} />
                                        ) : (
                                            <Link href={`/applications/${app.id}/preview`}>
                                                <Button size="sm" variant="outline">편집</Button>
                                            </Link>
                                        )}
                                        {(app.status === "DRAFT" || app.status === "READY") && (
                                            <>
                                                <Button size="sm" onClick={() => handleSubmit(app.id)}
                                                    className="bg-emerald-600 hover:bg-emerald-700">지원</Button>
                                                <Button size="sm" variant="outline" onClick={() => handleManualApply(app.id)}>
                                                    수동 완료
                                                </Button>
                                            </>
                                        )}
                                        {app.status === "FAILED" && (
                                            <Button size="sm" variant="outline" onClick={() => handleRetry(app.id)}>
                                                재시도
                                            </Button>
                                        )}
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

function PreviewDialog({ app }: { app: JobApplication }) {
    return (
        <Dialog>
            <DialogTrigger render={<Button variant="outline" size="sm">미리보기</Button>} />
            <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>지원서 미리보기 - {app.jobPosting?.title}</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                    <div>
                        <h3 className="font-medium mb-1">자기소개서</h3>
                        <pre className="text-sm whitespace-pre-wrap bg-muted/50 p-3 rounded">
                            {app.coverLetter || "(미생성)"}
                        </pre>
                    </div>
                    <div>
                        <h3 className="font-medium mb-1">포트폴리오</h3>
                        <pre className="text-sm whitespace-pre-wrap bg-muted/50 p-3 rounded">
                            {app.portfolioContent || "(미생성)"}
                        </pre>
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}
