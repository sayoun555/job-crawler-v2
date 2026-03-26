"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { resumeApi, accountApi, ResumeSyncResult, ExternalAccount } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

type Props = {
    token: string;
    onImportComplete?: () => void;
};

const SITES = [
    { key: "SARAMIN", label: "사람인", color: "bg-blue-600" },
    { key: "JOBKOREA", label: "잡코리아", color: "bg-red-600" },
    { key: "JOBPLANET", label: "잡플래닛", color: "bg-purple-600" },
    { key: "LINKAREER", label: "링커리어", color: "bg-teal-600" },
];

export default function SyncSection({ token, onImportComplete }: Props) {
    const [syncing, setSyncing] = useState<string | null>(null);
    const [importing, setImporting] = useState<string | null>(null);
    const [results, setResults] = useState<Record<string, ResumeSyncResult>>({});
    const [importResult, setImportResult] = useState<{ success: boolean; message: string } | null>(null);
    const [confirmTarget, setConfirmTarget] = useState<string | null>(null);
    const [accounts, setAccounts] = useState<ExternalAccount[]>([]);

    // 외부 계정 연동 상태 조회
    useEffect(() => {
        accountApi.list(token).then(setAccounts).catch(() => {});
    }, [token]);

    const isConnected = (siteKey: string) => {
        const account = accounts.find(a => a.site === siteKey);
        return account && account.sessionValid === true;
    };

    const handleSync = async (siteKey: string) => {
        setConfirmTarget(null);
        setSyncing(siteKey);
        try {
            const result = await resumeApi.syncToSite(token, siteKey);
            setResults(prev => ({ ...prev, [siteKey]: result }));
        } catch (e) {
            setResults(prev => ({
                ...prev,
                [siteKey]: { status: "FAILED", message: e instanceof Error ? e.message : "연동 실패" },
            }));
        } finally {
            setSyncing(null);
        }
    };

    const handleImport = async (siteKey: string) => {
        setImporting(siteKey);
        setImportResult(null);
        try {
            const result = await resumeApi.importFromSite(token, siteKey);
            setImportResult({ success: result.success, message: result.message });
            if (result.success && onImportComplete) onImportComplete();
        } catch (e) {
            const msg = e instanceof Error ? e.message : "가져오기 실패";
            if (msg.includes("세션") || msg.includes("연동")) {
                setImportResult({ success: false, message: "세션 만료 - 설정에서 다시 연동해주세요." });
            } else {
                setImportResult({ success: false, message: msg });
            }
        } finally {
            setImporting(null);
        }
    };

    const getStatusColor = (status?: string) => {
        if (status === "SUCCESS") return "text-green-400";
        if (status === "PARTIAL_SUCCESS") return "text-yellow-400";
        if (status === "FAILED") return "text-red-400";
        return "";
    };

    const getStatusLabel = (status?: string) => {
        if (status === "SUCCESS") return "연동 완료";
        if (status === "PARTIAL_SUCCESS") return "부분 성공";
        if (status === "FAILED") return "연동 실패";
        return "";
    };

    // 연동된 사이트가 하나도 없으면 설정 페이지 안내
    const hasAnyConnection = accounts.some(a => a.sessionValid === true);

    return (
        <Card>
            <CardHeader>
                <CardTitle>사이트 연동</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                {!hasAnyConnection && accounts.length === 0 ? null : !hasAnyConnection && (
                    <div className="p-4 rounded-lg bg-yellow-950/30 border border-yellow-700/50 text-sm space-y-2">
                        <p className="font-medium text-yellow-300">연동된 사이트가 없습니다</p>
                        <p className="text-xs text-yellow-300/70">
                            이력서를 가져오거나 내보내려면 먼저 채용 사이트에 로그인 연동이 필요합니다.
                        </p>
                        <Link href="/settings">
                            <Button size="sm" variant="outline" className="mt-1">
                                설정에서 사이트 연동하기
                            </Button>
                        </Link>
                    </div>
                )}

                <p className="text-sm text-muted-foreground">
                    이력서 데이터를 외부 채용 사이트에 자동으로 등록합니다. 사이트 로그인이 연동되어 있어야 합니다.
                </p>

                {/* 동의 확인 모달 */}
                {confirmTarget && (
                    <div className="p-4 rounded-lg bg-muted border border-border space-y-3">
                        <p className="text-sm font-medium">
                            {SITES.find(s => s.key === confirmTarget)?.label}에 이력서를 등록하시겠습니까?
                        </p>
                        <p className="text-xs text-muted-foreground">
                            서버에서 해당 사이트에 접속하여 이력서 폼을 자동으로 채웁니다.
                        </p>
                        <div className="flex gap-2">
                            <Button size="sm" onClick={() => handleSync(confirmTarget)}>
                                동의 및 연동 시작
                            </Button>
                            <Button size="sm" variant="outline" onClick={() => setConfirmTarget(null)}>
                                취소
                            </Button>
                        </div>
                    </div>
                )}

                {/* 가져오기 섹션 */}
                <div className="space-y-2">
                    <p className="text-sm font-medium">외부 사이트에서 가져오기</p>
                    <p className="text-xs text-muted-foreground">
                        채용 사이트에 등록된 이력서를 가져옵니다. 사이트별로 별도 저장되며, 해당 사이트 공고 지원 시 AI가 자동으로 사용합니다.
                    </p>
                    <div className="grid grid-cols-2 gap-3">
                        {SITES.map(site => {
                            const connected = isConnected(site.key);
                            return (
                                <div key={`import-${site.key}`} className="space-y-1">
                                    {connected ? (
                                        <Button
                                            variant="outline"
                                            className="w-full"
                                            disabled={importing !== null || syncing !== null}
                                            onClick={() => handleImport(site.key)}
                                        >
                                            {importing === site.key ? `${site.label} 가져오는 중...` : `${site.label}에서 가져오기`}
                                        </Button>
                                    ) : (
                                        <Link href="/settings" className="block">
                                            <Button variant="outline" className="w-full text-muted-foreground">
                                                {site.label} 미연동
                                                <Badge variant="outline" className="ml-2 text-xs">연동 필요</Badge>
                                            </Button>
                                        </Link>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                    {importResult && (
                        <div className={`text-xs p-2 rounded ${importResult.success ? "bg-green-950/30 text-green-400" : "bg-red-950/30 text-red-400"}`}>
                            {importResult.success ? "✓" : "✗"} {importResult.message}
                            {!importResult.success && importResult.message.includes("설정") && (
                                <Link href="/settings" className="ml-2 text-blue-400 underline">
                                    설정으로 이동
                                </Link>
                            )}
                        </div>
                    )}
                </div>

                <div className="border-t pt-4 space-y-2">
                    <p className="text-sm font-medium">외부 사이트로 내보내기</p>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    {SITES.map(site => {
                        const connected = isConnected(site.key);
                        const account = accounts.find(a => a.site === site.key);
                        return (
                            <div key={site.key} className="space-y-1">
                                {connected ? (
                                    <Button
                                        variant="outline"
                                        className="w-full"
                                        disabled={syncing !== null || confirmTarget !== null || importing !== null}
                                        onClick={() => setConfirmTarget(site.key)}
                                    >
                                        {syncing === site.key ? `${site.label} 연동 중...` : `${site.label} 연동`}
                                    </Button>
                                ) : (
                                    <Link href="/settings" className="block">
                                        <Button variant="outline" className="w-full text-muted-foreground">
                                            {site.label} 미연동
                                        </Button>
                                    </Link>
                                )}
                                {/* 동기화 상태 표시 */}
                                {account?.resumeSyncStatus && (
                                    <p className={`text-xs px-1 ${getStatusColor(account.resumeSyncStatus)}`}>
                                        {getStatusLabel(account.resumeSyncStatus)}
                                        {account.resumeSyncedAt && (
                                            <span className="text-muted-foreground ml-1">
                                                ({new Date(account.resumeSyncedAt).toLocaleDateString("ko-KR")})
                                            </span>
                                        )}
                                    </p>
                                )}
                                {results[site.key] && (
                                    <div className="text-xs px-1">
                                        <span className={getStatusColor(results[site.key].status)}>
                                            {getStatusLabel(results[site.key].status)}
                                        </span>
                                        {results[site.key].message && (
                                            <span className="text-muted-foreground ml-1">- {results[site.key].message}</span>
                                        )}
                                        {results[site.key].sessionExpired && (
                                            <div className="mt-1 p-2 rounded bg-red-950/30 border border-red-800/50">
                                                <p className="text-red-400 font-medium">세션 만료 - 연동 해제됨</p>
                                                <Link href="/settings" className="text-blue-400 underline text-xs">
                                                    설정에서 다시 연동하기
                                                </Link>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </CardContent>
        </Card>
    );
}
