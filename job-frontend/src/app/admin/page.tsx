"use client";

import { useState } from "react";
import Link from "next/link";
import { crawlerApi, jobsApi, coverLettersApi, adminApi, type User } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default function AdminPage() {
    const { token, isAdmin } = useAuth();
    const [keyword, setKeyword] = useState("");
    const [crawling, setCrawling] = useState(false);
    const [crawlResult, setCrawlResult] = useState("");
    const [selectedSites, setSelectedSites] = useState<string[]>(["SARAMIN", "JOBPLANET", "LINKAREER", "JOBKOREA"]);
    const [stats, setStats] = useState<{ saramin: number; jobplanet: number; total: number } | null>(null);
    const [loadingStats, setLoadingStats] = useState(false);
    const [sched1Hour, setSched1Hour] = useState("9");
    const [sched2Hour, setSched2Hour] = useState("14");
    const [maxPages, setMaxPages] = useState("0");
    const [schedMsg, setSchedMsg] = useState("");
    const [clCrawling, setClCrawling] = useState(false);
    const [clResult, setClResult] = useState("");
    const [clPages, setClPages] = useState("5");
    const [deleteIds, setDeleteIds] = useState("");
    const [deleteMsg, setDeleteMsg] = useState("");
    const [deleting, setDeleting] = useState(false);
    const [schedEnabled, setSchedEnabled] = useState(true);
    const [validating, setValidating] = useState(false);
    const [validateMsg, setValidateMsg] = useState("");
    const [closedJobs, setClosedJobs] = useState<any[]>([]);
    const [closedTotal, setClosedTotal] = useState(0);
    const [closedPage, setClosedPage] = useState(0);
    const [closedPages, setClosedPages] = useState(0);
    const [loadingClosed, setLoadingClosed] = useState(false);
    const [users, setUsers] = useState<User[]>([]);
    const [loadingUsers, setLoadingUsers] = useState(false);

    const loadUsers = async () => {
        if (!token) return;
        setLoadingUsers(true);
        try { setUsers(await adminApi.listUsers(token)); } catch { /* */ }
        finally { setLoadingUsers(false); }
    };

    const handleApprove = async (userId: number) => {
        if (!token) return;
        await adminApi.approveUser(token, userId);
        loadUsers();
    };

    const handleSuspend = async (userId: number) => {
        if (!token || !confirm("이 유저를 정지하시겠습니까?")) return;
        await adminApi.suspendUser(token, userId);
        loadUsers();
    };

    if (!token) return <div className="text-center py-20 text-muted-foreground">로그인이 필요합니다.</div>;
    if (!isAdmin) return <div className="text-center py-20 text-muted-foreground">관리자 권한이 필요합니다.</div>;

    // 통계 로드
    const loadStats = async () => {
        setLoadingStats(true);
        try {
            const data = await jobsApi.stats();
            setStats(data);
        } catch { setStats(null); }
        finally { setLoadingStats(false); }
    };

    const siteLabels: Record<string, string> = { SARAMIN: "사람인", JOBPLANET: "잡플래닛", LINKAREER: "링커리어", JOBKOREA: "잡코리아" };

    const toggleSite = (site: string) => {
        setSelectedSites(prev =>
            prev.includes(site) ? prev.filter(s => s !== site) : [...prev, site]
        );
    };

    // 크롤링 실행
    const startCrawl = async () => {
        if (!token || selectedSites.length === 0) return;
        setCrawling(true);
        setCrawlResult("");
        try {
            const label = selectedSites.length === 4
                ? "전체"
                : selectedSites.map(s => siteLabels[s]).join(", ");
            setCrawlResult(`⏳ ${label} 크롤링 진행 중... (1~2분 소요)`);
            const kw = keyword.trim() || undefined;
            const pages = parseInt(maxPages) || 50;
            const res = selectedSites.length === 4
                ? await crawlerApi.crawlAll(token, kw, pages)
                : await crawlerApi.crawlBySites(token, selectedSites, kw, pages);
            setCrawlResult(`✅ ${label} 크롤링 완료! ${res.savedCount}개 새 공고 저장`);
            loadStats();
        } catch (e) {
            setCrawlResult(`❌ 크롤링 실패: ${e instanceof Error ? e.message : "알 수 없는 오류"}`);
        } finally {
            setCrawling(false);
        }
    };

    // 스케줄 로드
    const loadSchedule = async () => {
        if (!token) return;
        try {
            const data = await crawlerApi.getSchedule(token);
            const h1 = data.schedule1.match(/0 0 (\d+)/)?.[1] || "9";
            const h2 = data.schedule2.match(/0 0 (\d+)/)?.[1] || "14";
            setSched1Hour(h1);
            setSched2Hour(h2);
            if (data.maxPages) setMaxPages(data.maxPages.toString());
            setSchedEnabled(data.enabled);
        } catch { /* ignore */ }
    };

    // 스케줄 on/off 토글
    const toggleSchedule = async () => {
        if (!token) return;
        try {
            const data = await crawlerApi.toggleSchedule(token);
            setSchedEnabled(data.enabled);
            setSchedMsg(data.enabled ? "자동 크롤링 활성화" : "자동 크롤링 비활성화");
            setTimeout(() => setSchedMsg(""), 3000);
        } catch {
            setSchedMsg("토글 실패");
        }
    };

    // 스케줄 저장
    const saveSchedule = async () => {
        if (!token) return;
        try {
            const cron1 = `0 0 ${sched1Hour} * * MON-FRI`;
            const cron2 = `0 0 ${sched2Hour} * * MON-FRI`;
            const pages = parseInt(maxPages) || 50;
            await crawlerApi.updateSchedule(token, cron1, cron2, pages);
            setSchedMsg("✅ 스케줄 저장 완료");
            setTimeout(() => setSchedMsg(""), 3000);
        } catch {
            setSchedMsg("❌ 저장 실패");
        }
    };

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <h1 className="text-2xl font-bold">🛠 관리자</h1>

            {/* DB 통계 */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>📊 채용 공고 현황</CardTitle>
                        <Button variant="outline" size="sm" onClick={loadStats} disabled={loadingStats}>
                            {loadingStats ? "로딩..." : "새로고침"}
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {stats ? (
                        <div className="grid grid-cols-3 md:grid-cols-5 gap-3 text-center">
                            <div className="p-3 rounded-lg bg-blue-50 dark:bg-blue-950">
                                <p className="text-xl font-bold text-blue-600">{stats.saramin}</p>
                                <p className="text-xs text-muted-foreground">사람인</p>
                            </div>
                            <div className="p-3 rounded-lg bg-purple-50 dark:bg-purple-950">
                                <p className="text-xl font-bold text-purple-600">{stats.jobplanet}</p>
                                <p className="text-xs text-muted-foreground">잡플래닛</p>
                            </div>
                            <div className="p-3 rounded-lg bg-green-50 dark:bg-green-950">
                                <p className="text-xl font-bold text-green-600">{(stats as Record<string, number>).linkareer || 0}</p>
                                <p className="text-xs text-muted-foreground">링커리어</p>
                            </div>
                            <div className="p-3 rounded-lg bg-red-50 dark:bg-red-950">
                                <p className="text-xl font-bold text-red-600">{(stats as Record<string, number>).jobkorea || 0}</p>
                                <p className="text-xs text-muted-foreground">잡코리아</p>
                            </div>
                            <div className="p-3 rounded-lg bg-emerald-50 dark:bg-emerald-950">
                                <p className="text-xl font-bold text-emerald-600">{stats.total}</p>
                                <p className="text-xs text-muted-foreground">전체</p>
                            </div>
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground text-center py-4">
                            &quot;새로고침&quot; 버튼을 눌러 통계를 로드하세요
                        </p>
                    )}
                </CardContent>
            </Card>

            {/* 크롤링 */}
            <Card>
                <CardHeader><CardTitle>🕷 크롤링</CardTitle></CardHeader>
                <CardContent className="space-y-4">
                    <p className="text-sm text-muted-foreground">
                        사람인·잡플래닛에서 채용 공고를 수집합니다. 키워드 없이 실행하면 전체 최신 공고를 크롤링합니다.
                    </p>

                    <div className="flex gap-2">
                        <Input
                            placeholder="키워드 (비워두면 전체 최신 공고)"
                            value={keyword}
                            onChange={e => setKeyword(e.target.value)}
                            className="flex-1"
                        />
                        <div className="flex items-center gap-2">
                            <span className="text-sm whitespace-nowrap text-muted-foreground">최대</span>
                            <Input
                                type="number"
                                value={maxPages}
                                onChange={e => setMaxPages(e.target.value)}
                                className="w-20"
                                min={0}
                                max={500}
                                title="0 = 무제한 (전체 수집)"
                            />
                            <span className="text-sm border-r pr-2 py-1 mr-2 whitespace-nowrap text-muted-foreground">페이지 (0=전체)</span>
                        </div>
                    </div>

                    <div className="space-y-3">
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-medium text-muted-foreground mr-1">사이트 선택</span>
                            {[
                                { site: "SARAMIN", color: "bg-blue-600", border: "border-blue-600" },
                                { site: "JOBPLANET", color: "bg-purple-600", border: "border-purple-600" },
                                { site: "LINKAREER", color: "bg-green-600", border: "border-green-600" },
                                { site: "JOBKOREA", color: "bg-red-600", border: "border-red-600" },
                            ].map(({ site, color, border }) => {
                                const checked = selectedSites.includes(site);
                                return (
                                    <label key={site}
                                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full border cursor-pointer transition-colors text-sm ${
                                            checked ? `${color} text-white ${border}` : "border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground"
                                        }`}>
                                        <input
                                            type="checkbox"
                                            checked={checked}
                                            onChange={() => toggleSite(site)}
                                            className="sr-only"
                                        />
                                        {checked && <span>&#10003;</span>}
                                        {siteLabels[site]}
                                    </label>
                                );
                            })}
                            <button
                                type="button"
                                className="text-xs text-muted-foreground underline ml-1"
                                onClick={() => setSelectedSites(prev =>
                                    prev.length === 4 ? [] : ["SARAMIN", "JOBPLANET", "LINKAREER", "JOBKOREA"]
                                )}>
                                {selectedSites.length === 4 ? "전체 해제" : "전체 선택"}
                            </button>
                        </div>
                        <Button
                            onClick={startCrawl}
                            disabled={crawling || selectedSites.length === 0}
                            className="w-full bg-emerald-600 hover:bg-emerald-700">
                            {crawling ? "크롤링 중..." : selectedSites.length === 0 ? "사이트를 선택하세요" : `크롤링 시작 (${selectedSites.length}개 사이트)`}
                        </Button>
                    </div>

                    {crawlResult && (
                        <div className={`p-3 rounded-lg text-sm font-medium ${
                            crawlResult.startsWith("✅") ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300" :
                            crawlResult.startsWith("❌") ? "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300" :
                            "bg-yellow-50 text-yellow-700 dark:bg-yellow-950 dark:text-yellow-300"
                        }`}>
                            {crawlResult}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* 자소서 크롤링 */}
            <Card>
                <CardHeader><CardTitle>합격 자소서 크롤링</CardTitle></CardHeader>
                <CardContent className="space-y-4">
                    <p className="text-sm text-muted-foreground">
                        링커리어 합격 자소서를 수집합니다. 채용 공고와 별도로 관리됩니다.
                    </p>
                    <div className="flex gap-2">
                        <div className="flex items-center gap-2">
                            <span className="text-sm whitespace-nowrap text-muted-foreground">최대</span>
                            <Input
                                type="number"
                                value={clPages}
                                onChange={e => setClPages(e.target.value)}
                                className="w-20"
                                min={1}
                                max={100}
                            />
                            <span className="text-sm whitespace-nowrap text-muted-foreground">페이지</span>
                        </div>
                        <Button
                            onClick={async () => {
                                if (!token) return;
                                setClCrawling(true);
                                setClResult("자소서 크롤링 진행 중...");
                                try {
                                    const res = await coverLettersApi.crawl(token, parseInt(clPages) || 5);
                                    setClResult(`자소서 크롤링 완료! ${res.savedCount}개 새 자소서 저장`);
                                } catch (e) {
                                    setClResult(`크롤링 실패: ${e instanceof Error ? e.message : "오류"}`);
                                } finally { setClCrawling(false); }
                            }}
                            disabled={clCrawling}
                            className="bg-orange-600 hover:bg-orange-700">
                            {clCrawling ? "크롤링 중..." : "자소서 크롤링"}
                        </Button>
                    </div>
                    {clResult && (
                        <p className={`text-sm font-medium ${clResult.includes("실패") ? "text-red-500" : clResult.includes("완료") ? "text-emerald-500" : "text-yellow-500"}`}>
                            {clResult}
                        </p>
                    )}
                </CardContent>
            </Card>

            {/* 스케줄 설정 */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>⏰ 자동 크롤링 스케줄</CardTitle>
                        <Button variant="outline" size="sm" onClick={loadSchedule}>현재 설정 불러오기</Button>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="flex items-center justify-between">
                        <p className="text-sm text-muted-foreground">
                            평일(월~금) 자동 크롤링 시간을 설정합니다. 마감 지난 공고는 매시간 자동으로 닫힙니다.
                        </p>
                        <button
                            onClick={toggleSchedule}
                            className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                                schedEnabled ? "bg-emerald-600" : "bg-muted-foreground/30"
                            }`}>
                            <span className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition-transform ${
                                schedEnabled ? "translate-x-5" : "translate-x-0"
                            }`} />
                        </button>
                    </div>
                    <div className={`grid grid-cols-2 gap-4 ${!schedEnabled ? "opacity-50 pointer-events-none" : ""}`}>
                        <div className="space-y-1">
                            <label className="text-sm font-medium">1차 크롤링</label>
                            <select
                                className="w-full rounded-md border px-3 py-2 text-sm bg-background"
                                value={sched1Hour}
                                onChange={e => setSched1Hour(e.target.value)}
                            >
                                {Array.from({ length: 24 }, (_, i) => (
                                    <option key={i} value={i}>{`${i.toString().padStart(2, "0")}:00`}</option>
                                ))}
                            </select>
                        </div>
                        <div className="space-y-1">
                            <label className="text-sm font-medium">2차 크롤링</label>
                            <select
                                className="w-full rounded-md border px-3 py-2 text-sm bg-background"
                                value={sched2Hour}
                                onChange={e => setSched2Hour(e.target.value)}
                            >
                                {Array.from({ length: 24 }, (_, i) => (
                                    <option key={i} value={i}>{`${i.toString().padStart(2, "0")}:00`}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Button onClick={saveSchedule} className="bg-emerald-600 hover:bg-emerald-700">
                            💾 스케줄 저장
                        </Button>
                        {schedMsg && <span className="text-sm font-medium">{schedMsg}</span>}
                    </div>
                    <div className="border-t pt-4 space-y-3">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm font-medium">만료 공고 URL 검증</p>
                                <p className="text-xs text-muted-foreground">
                                    마감일 없는 공고의 원본 URL을 확인하여 만료된 공고를 자동 비활성화합니다 (매일 03시 자동 실행)
                                </p>
                            </div>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={validating}
                                onClick={async () => {
                                    if (!token) return;
                                    setValidating(true);
                                    setValidateMsg("");
                                    try {
                                        const res = await crawlerApi.validateUrls(token);
                                        setValidateMsg(`${res.closedCount}건 만료 처리 완료`);
                                    } catch (e) {
                                        setValidateMsg(`실패: ${e instanceof Error ? e.message : "오류"}`);
                                    } finally { setValidating(false); }
                                }}>
                                {validating ? "검증 중..." : "수동 실행"}
                            </Button>
                        </div>
                        {validateMsg && (
                            <p className={`text-sm font-medium ${validateMsg.includes("실패") ? "text-red-500" : "text-emerald-500"}`}>
                                {validateMsg}
                            </p>
                        )}
                    </div>
                    <p className="text-xs text-muted-foreground">
                        마감일 지난 공고는 매시간 자동으로 비활성화됩니다 (목록에서 사라짐)
                    </p>
                </CardContent>
            </Card>

            {/* 공고 삭제 */}
            <Card>
                <CardHeader><CardTitle>공고 삭제</CardTitle></CardHeader>
                <CardContent className="space-y-4">
                    <p className="text-sm text-muted-foreground">
                        크롤링된 공고를 사이트별 또는 전체 삭제할 수 있습니다.
                    </p>
                    {/* 사이트별 삭제 */}
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
                        {[
                            { site: "SARAMIN", label: "사람인", color: "bg-blue-600 hover:bg-blue-700" },
                            { site: "JOBPLANET", label: "잡플래닛", color: "bg-purple-600 hover:bg-purple-700" },
                            { site: "LINKAREER", label: "링커리어", color: "bg-green-600 hover:bg-green-700" },
                            { site: "JOBKOREA", label: "잡코리아", color: "bg-red-600 hover:bg-red-700" },
                        ].map(({ site, label, color }) => (
                            <Button key={site} variant="outline" disabled={deleting}
                                onClick={async () => {
                                    if (!token || !confirm(`${label} 공고를 모두 삭제하시겠습니까?`)) return;
                                    setDeleting(true);
                                    try {
                                        await crawlerApi.deleteJobsBySite(token, site);
                                        setDeleteMsg(`${label} 공고 삭제 완료`);
                                        loadStats();
                                    } catch (e) {
                                        setDeleteMsg(`삭제 실패: ${e instanceof Error ? e.message : "오류"}`);
                                    } finally { setDeleting(false); }
                                }}>
                                {label} 삭제
                            </Button>
                        ))}
                        <Button variant="destructive" disabled={deleting}
                            onClick={async () => {
                                if (!token || !confirm("정말로 모든 공고를 삭제하시겠습니까?")) return;
                                setDeleting(true);
                                try {
                                    await crawlerApi.deleteAllJobs(token);
                                    setDeleteMsg("전체 공고 삭제 완료");
                                    loadStats();
                                } catch (e) {
                                    setDeleteMsg(`삭제 실패: ${e instanceof Error ? e.message : "오류"}`);
                                } finally { setDeleting(false); }
                            }}>
                            {deleting ? "삭제 중..." : "전체 삭제"}
                        </Button>
                    </div>
                    {/* ID 선택 삭제 */}
                    <div className="flex gap-2">
                        <Input
                            placeholder="공고 ID (예: 1,2,3)"
                            value={deleteIds}
                            onChange={e => setDeleteIds(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outline" disabled={deleting || !deleteIds.trim()}
                            onClick={async () => {
                                if (!token) return;
                                setDeleting(true);
                                try {
                                    const ids = deleteIds.split(",").map(s => parseInt(s.trim())).filter(n => !isNaN(n));
                                    if (ids.length === 1) await crawlerApi.deleteJob(token, ids[0]);
                                    else await crawlerApi.deleteJobs(token, ids);
                                    setDeleteMsg(`${ids.length}개 공고 삭제 완료`);
                                    setDeleteIds("");
                                    loadStats();
                                } catch (e) {
                                    setDeleteMsg(`삭제 실패: ${e instanceof Error ? e.message : "오류"}`);
                                } finally { setDeleting(false); }
                            }}>
                            선택 삭제
                        </Button>
                    </div>
                    {deleteMsg && (
                        <p className={`text-sm font-medium ${deleteMsg.includes("실패") ? "text-red-500" : "text-emerald-500"}`}>
                            {deleteMsg}
                        </p>
                    )}
                </CardContent>
            </Card>

            {/* 닫힌 공고 히스토리 */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>닫힌 공고 히스토리</CardTitle>
                        <Button variant="outline" size="sm" disabled={loadingClosed}
                            onClick={async () => {
                                if (!token) return;
                                setLoadingClosed(true);
                                try {
                                    const res = await crawlerApi.getClosedJobs(token, 0, 20);
                                    setClosedJobs(res.content);
                                    setClosedTotal(res.totalElements);
                                    setClosedPages(res.totalPages);
                                    setClosedPage(0);
                                } catch { /* ignore */ }
                                finally { setLoadingClosed(false); }
                            }}>
                            {loadingClosed ? "로딩..." : "불러오기"}
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {closedJobs.length === 0 ? (
                        <p className="text-sm text-muted-foreground text-center py-4">
                            &quot;불러오기&quot; 버튼을 눌러 닫힌 공고를 확인하세요
                        </p>
                    ) : (
                        <div className="space-y-3">
                            <p className="text-sm text-muted-foreground">총 {closedTotal}건</p>
                            <div className="space-y-2 max-h-80 overflow-y-auto">
                                {closedJobs.map((job: any) => (
                                    <div key={job.id} className="flex items-center justify-between p-3 rounded-lg border text-sm">
                                        <div className="flex-1 min-w-0">
                                            <p className="font-medium truncate">{job.title}</p>
                                            <p className="text-xs text-muted-foreground">
                                                {job.company} · {job.source} · 마감: {job.deadline || "채용시"}
                                            </p>
                                        </div>
                                        <a href={job.url} target="_blank" rel="noopener noreferrer"
                                            className="text-xs text-blue-500 hover:underline ml-2 shrink-0">원문</a>
                                    </div>
                                ))}
                            </div>
                            {closedPages > 1 && (
                                <div className="flex justify-center gap-2 pt-2">
                                    <Button variant="outline" size="sm"
                                        disabled={closedPage === 0 || loadingClosed}
                                        onClick={async () => {
                                            if (!token) return;
                                            setLoadingClosed(true);
                                            try {
                                                const res = await crawlerApi.getClosedJobs(token, closedPage - 1, 20);
                                                setClosedJobs(res.content);
                                                setClosedPage(closedPage - 1);
                                            } catch { /* ignore */ }
                                            finally { setLoadingClosed(false); }
                                        }}>이전</Button>
                                    <span className="text-sm self-center">{closedPage + 1} / {closedPages}</span>
                                    <Button variant="outline" size="sm"
                                        disabled={closedPage >= closedPages - 1 || loadingClosed}
                                        onClick={async () => {
                                            if (!token) return;
                                            setLoadingClosed(true);
                                            try {
                                                const res = await crawlerApi.getClosedJobs(token, closedPage + 1, 20);
                                                setClosedJobs(res.content);
                                                setClosedPage(closedPage + 1);
                                            } catch { /* ignore */ }
                                            finally { setLoadingClosed(false); }
                                        }}>다음</Button>
                                </div>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* 빠른 이동 */}
            <Card>
                <CardHeader><CardTitle>🔗 빠른 이동</CardTitle></CardHeader>
                <CardContent>
                    <div className="grid grid-cols-2 gap-3">
                        <Link href="/test" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <span className="text-lg">📋</span>
                            <div>
                                <p className="font-medium text-sm">테스트 체크리스트</p>
                                <p className="text-xs text-muted-foreground">기능 구현 검증</p>
                            </div>
                        </Link>
                        <Link href="/settings" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <span className="text-lg">⚙️</span>
                            <div>
                                <p className="font-medium text-sm">설정</p>
                                <p className="text-xs text-muted-foreground">알림 · 계정 · 직무</p>
                            </div>
                        </Link>
                        <Link href="/profile" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <span className="text-lg">👤</span>
                            <div>
                                <p className="font-medium text-sm">프로필</p>
                                <p className="text-xs text-muted-foreground">이력 관리</p>
                            </div>
                        </Link>
                        <Link href="/templates" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <span className="text-lg">📝</span>
                            <div>
                                <p className="font-medium text-sm">템플릿</p>
                                <p className="text-xs text-muted-foreground">자소서 · 포트폴리오</p>
                            </div>
                        </Link>
                    </div>
                </CardContent>
            </Card>

            {/* 시스템 정보 */}
            <Card>
                <CardHeader><CardTitle>ℹ️ 시스템 정보</CardTitle></CardHeader>
                <CardContent>
                    <div className="space-y-2 text-sm">
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">백엔드</span>
                            <Badge className="bg-emerald-600">Spring Boot 3.4</Badge>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">프론트엔드</span>
                            <Badge className="bg-blue-600">Next.js 16</Badge>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">데이터베이스</span>
                            <Badge className="bg-purple-600">PostgreSQL 14</Badge>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">캐시</span>
                            <Badge className="bg-red-600">Redis</Badge>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-muted-foreground">크롤러</span>
                            <Badge className="bg-orange-600">Playwright</Badge>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* 유저 관리 */}
            <Card className="md:col-span-2">
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>유저 관리</CardTitle>
                        <Button variant="outline" size="sm" onClick={loadUsers} disabled={loadingUsers}>
                            {loadingUsers ? "로딩..." : "유저 목록 조회"}
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {users.length === 0 ? (
                        <p className="text-sm text-muted-foreground">유저 목록을 조회하세요.</p>
                    ) : (
                        <div className="space-y-2">
                            {users.map(u => (
                                <div key={u.id} className="flex items-center justify-between p-3 border rounded-lg">
                                    <div className="flex items-center gap-3">
                                        <span className="text-sm font-medium">{u.email}</span>
                                        <span className="text-xs text-muted-foreground">{u.nickname}</span>
                                        <Badge className={
                                            u.status === "ACTIVE" ? "bg-emerald-600" :
                                            u.status === "PENDING" ? "bg-yellow-600" :
                                            "bg-red-600"
                                        }>{u.status}</Badge>
                                        <Badge variant="outline">{u.role}</Badge>
                                    </div>
                                    <div className="flex gap-2">
                                        {u.status === "PENDING" && (
                                            <Button size="sm" className="bg-emerald-600 hover:bg-emerald-700"
                                                onClick={() => handleApprove(u.id)}>
                                                승인
                                            </Button>
                                        )}
                                        {u.status === "ACTIVE" && u.role !== "ADMIN" && (
                                            <Button size="sm" variant="outline"
                                                className="text-red-500 hover:text-red-700"
                                                onClick={() => handleSuspend(u.id)}>
                                                정지
                                            </Button>
                                        )}
                                        {u.status === "SUSPENDED" && (
                                            <Button size="sm" className="bg-emerald-600 hover:bg-emerald-700"
                                                onClick={() => handleApprove(u.id)}>
                                                해제
                                            </Button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
