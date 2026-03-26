"use client";

import { useState, useEffect } from "react";
import { userApi, accountApi, preferenceApi, ExternalAccount } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const SARAMIN_JOBS = [
    "서버/백엔드 개발", "프론트엔드 개발", "풀스택 개발", "안드로이드 개발",
    "iOS 개발", "데이터 엔지니어", "DevOps/인프라", "QA/테스트", "보안",
    "AI/머신러닝", "게임 개발", "임베디드/펌웨어", "DBA", "기타",
];
const JOBPLANET_JOBS = [
    "백엔드 개발자", "프론트엔드 개발자", "웹 풀스택 개발자", "모바일 개발자",
    "데이터 엔지니어", "데이터 사이언티스트", "DevOps 엔지니어", "QA 엔지니어",
    "보안 엔지니어", "AI 엔지니어", "게임 개발자", "CTO", "기타",
];
const JOBKOREA_JOBS = [
    "프론트엔드개발자", "백엔드개발자", "웹개발자", "앱개발자",
    "소프트웨어개발자", "시스템엔지니어", "네트워크엔지니어", "DBA",
    "클라우드엔지니어", "AI/ML엔지니어", "데이터엔지니어", "데이터사이언티스트",
    "데이터분석가", "보안엔지니어", "게임개발자", "하드웨어개발자", "기타",
];
const LINKAREER_JOBS = [
    "개발", "기획", "디자인", "마케팅", "영업",
    "데이터/AI", "생산/제조", "연구개발", "기타",
];

export default function SettingsPage() {
    const { token } = useAuth();
    const [webhookUrl, setWebhookUrl] = useState("");
    const [notificationEnabled, setNotificationEnabled] = useState(true);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState("");

    // 알림 시간
    const [notificationHours, setNotificationHours] = useState<number[]>([9, 18]);

    // 외부 계정
    const [accounts, setAccounts] = useState<ExternalAccount[]>([]);
    const [newAccount, setNewAccount] = useState({ site: "SARAMIN", accountId: "", password: "" });
    const [accountSaving, setAccountSaving] = useState(false);

    // 희망 직무
    const [saraminJobs, setSaraminJobs] = useState<string[]>([]);
    const [jobplanetJobs, setJobplanetJobs] = useState<string[]>([]);
    const [jobkoreaJobs, setJobkoreaJobs] = useState<string[]>([]);
    const [linkareerJobs, setLinkareerJobs] = useState<string[]>([]);
    const [prefEnabled, setPrefEnabled] = useState(true);

    useEffect(() => {
        if (!token) return;
        userApi.me(token).then(u => {
            setWebhookUrl(u.discordWebhookUrl || "");
            setNotificationEnabled(u.notificationEnabled);
            if (u.notificationHours) {
                setNotificationHours(u.notificationHours.split(",").map(Number).filter(n => !isNaN(n)));
            }
        }).catch(console.error);
        accountApi.list(token).then(setAccounts).catch(console.error);
    }, [token]);

    // === 디스코드 Webhook ===
    const saveWebhook = async () => {
        if (!token) return;
        setSaving(true);
        try {
            await userApi.updateWebhook(token, webhookUrl);
            setMessage("✅ Webhook 저장 완료");
        } catch { setMessage("저장 실패"); }
        finally { setSaving(false); setTimeout(() => setMessage(""), 3000); }
    };

    const toggleNotification = async () => {
        if (!token) return;
        const next = !notificationEnabled;
        try {
            await userApi.toggleNotification(token, next);
            setNotificationEnabled(next);
        } catch { /* */ }
    };

    // === 외부 계정 ===
    const addAccount = async () => {
        if (!token || !newAccount.accountId || !newAccount.password) return;
        setAccountSaving(true);
        try {
            const created = await accountApi.create(token, newAccount);
            setAccounts(prev => [...prev, created]);
            setNewAccount({ site: "SARAMIN", accountId: "", password: "" });
        } catch { /* */ }
        finally { setAccountSaving(false); }
    };

    const deleteAccount = async (id: number) => {
        if (!token) return;
        try {
            await accountApi.delete(token, id);
            setAccounts(prev => prev.filter(a => a.id !== id));
        } catch { /* */ }
    };

    const verifyAccount = async (id: number) => {
        if (!token) return;
        try {
            const result = await accountApi.verify(token, id);
            setAccounts(prev => prev.map(a => a.id === id ? { ...a, isValid: result.valid } : a));
        } catch { /* */ }
    };

    // === 희망 직무 토글 ===
    const toggleJob = (list: string[], setList: (v: string[]) => void, job: string) => {
        if (list.includes(job)) setList(list.filter(j => j !== job));
        else setList([...list, job]);
    };

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <h1 className="text-2xl font-bold">설정</h1>

            {/* 디스코드 알림 */}
            <Card>
                <CardHeader><CardTitle>디스코드 알림</CardTitle></CardHeader>
                <CardContent className="space-y-4">
                    <div>
                        <label className="text-sm font-medium">Discord Webhook URL</label>
                        <Input placeholder="https://discord.com/api/webhooks/..."
                            value={webhookUrl}
                            onChange={e => setWebhookUrl(e.target.value)} />
                    </div>
                    <Button onClick={saveWebhook} disabled={saving}
                        className="bg-emerald-600 hover:bg-emerald-700">
                        {saving ? "저장 중..." : "Webhook 저장"}
                    </Button>
                    {message && <p className="text-sm text-emerald-500">{message}</p>}

                    <div className="flex items-center justify-between pt-4 border-t">
                        <div>
                            <p className="font-medium">알림 수신</p>
                            <p className="text-sm text-muted-foreground">새 공고 매칭 시 디스코드 알림</p>
                        </div>
                        <Button variant={notificationEnabled ? "default" : "outline"}
                            onClick={toggleNotification}
                            className={notificationEnabled ? "bg-emerald-600 hover:bg-emerald-700" : ""}>
                            {notificationEnabled ? "ON" : "OFF"}
                        </Button>
                    </div>

                    {notificationEnabled && (
                        <div className="pt-4 border-t space-y-3">
                            <div>
                                <p className="font-medium">알림 시간 설정</p>
                                <p className="text-sm text-muted-foreground">선택한 시간에 새 공고 알림을 받습니다</p>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {Array.from({ length: 24 }, (_, i) => i).map(hour => (
                                    <Badge key={hour}
                                        className={`cursor-pointer transition-colors min-w-[3rem] justify-center ${
                                            notificationHours.includes(hour)
                                                ? "bg-emerald-600 hover:bg-emerald-700 text-white"
                                                : "bg-gray-200 text-gray-700 hover:bg-gray-300"
                                        }`}
                                        onClick={() => {
                                            setNotificationHours(prev =>
                                                prev.includes(hour)
                                                    ? prev.filter(h => h !== hour)
                                                    : [...prev, hour].sort((a, b) => a - b)
                                            );
                                        }}>
                                        {hour}시
                                    </Badge>
                                ))}
                            </div>
                            <Button size="sm" variant="outline"
                                onClick={async () => {
                                    if (!token) return;
                                    try {
                                        await userApi.updateNotificationHours(token, notificationHours.join(","));
                                        setMessage("✅ 알림 시간 저장 완료");
                                        setTimeout(() => setMessage(""), 3000);
                                    } catch { setMessage("❌ 저장 실패"); }
                                }}>
                                알림 시간 저장
                            </Button>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* 외부 계정 관리 */}
            <Card>
                <CardHeader><CardTitle>외부 사이트 계정</CardTitle></CardHeader>
                <CardContent className="space-y-4">
                    <p className="text-sm text-muted-foreground">
                        자동 지원을 위해 채용 사이트 계정을 연동합니다.
                    </p>

                    {/* 사이트별 연동 상태 */}
                    <div className="space-y-3">
                        <p className="text-xs text-muted-foreground">
                            로그인 버튼을 누르면 브라우저가 열립니다. 카카오·네이버·구글 등 원하는 방식으로 로그인하세요.
                        </p>
                        <div className="grid grid-cols-2 gap-3">
                            {[
                                { site: "SARAMIN", label: "사람인", color: "bg-blue-600 hover:bg-blue-700", connectedColor: "border-blue-600 text-blue-600" },
                                { site: "JOBPLANET", label: "잡플래닛", color: "bg-purple-600 hover:bg-purple-700", connectedColor: "border-purple-600 text-purple-600" },
                                { site: "JOBKOREA", label: "잡코리아", color: "bg-red-600 hover:bg-red-700", connectedColor: "border-red-600 text-red-600" },
                                { site: "LINKAREER", label: "링커리어", color: "bg-teal-600 hover:bg-teal-700", connectedColor: "border-teal-600 text-teal-600" },
                            ].map(({ site, label, color, connectedColor }) => {
                                const linked = accounts.find(a => a.site === site);
                                const isConnected = linked && linked.sessionValid === true;
                                return isConnected ? (
                                    <div key={site} className={`p-2.5 border-2 rounded-lg ${connectedColor}`}>
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm font-medium">&#10003; {label} 연동됨</span>
                                            <Button variant="ghost" size="sm"
                                                className="h-7 px-2 text-xs text-destructive hover:text-destructive"
                                                onClick={() => deleteAccount(linked.id)}>
                                                해제
                                            </Button>
                                        </div>
                                        {linked.resumeSyncStatus && (
                                            <p className={`text-xs mt-1 ${
                                                linked.resumeSyncStatus === "SUCCESS" ? "text-emerald-600" :
                                                linked.resumeSyncStatus === "PARTIAL_SUCCESS" ? "text-yellow-600" : "text-red-500"
                                            }`}>
                                                이력서: {linked.resumeSyncStatus === "SUCCESS" ? "동기화 완료" :
                                                    linked.resumeSyncStatus === "PARTIAL_SUCCESS" ? "일부 동기화" : "동기화 실패"}
                                                {linked.resumeSyncedAt && ` (${new Date(linked.resumeSyncedAt).toLocaleDateString("ko-KR")})`}
                                            </p>
                                        )}
                                        {!linked.resumeSyncStatus && (
                                            <p className="text-xs mt-1 text-muted-foreground">이력서 미동기화</p>
                                        )}
                                    </div>
                                ) : linked && linked.sessionValid === false ? (
                                    <div key={site} className="flex items-center justify-between p-2.5 border-2 rounded-lg border-yellow-500 text-yellow-500">
                                        <span className="text-sm font-medium">&#9888; {label} 세션 만료</span>
                                        <Button variant="ghost" size="sm"
                                            className="h-7 px-2 text-xs"
                                            onClick={async () => {
                                                if (!token) return;
                                                setAccountSaving(true);
                                                try {
                                                    const res = await accountApi.loginPopup(token, site);
                                                    if (res.success) accountApi.list(token).then(setAccounts).catch(() => {});
                                                } catch { /* */ }
                                                finally { setAccountSaving(false); }
                                            }}
                                            disabled={accountSaving}>
                                            재연동
                                        </Button>
                                    </div>
                                ) : (
                                    <Button
                                        key={site}
                                        onClick={async () => {
                                            if (!token) return;
                                            setAccountSaving(true);
                                            setMessage("");
                                            try {
                                                const res = await accountApi.loginPopup(token, site);
                                                setMessage(res.success ? "✅ " + res.message : "❌ " + res.message);
                                                if (res.success) accountApi.list(token).then(setAccounts).catch(() => {});
                                            } catch { setMessage(`❌ ${label} 연동 실패`); }
                                            finally { setAccountSaving(false); setTimeout(() => setMessage(""), 5000); }
                                        }}
                                        disabled={accountSaving}
                                        className={`${color} text-white`}>
                                        {accountSaving ? "로그인 대기 중..." : `${label} 로그인`}
                                    </Button>
                                );
                            })}
                        </div>
                        {message && <p className="text-sm font-medium">{message}</p>}
                    </div>

                    {/* 일회용 로그인 (ID/PW 서버 미저장) */}
                    <details className="border-t pt-3">
                        <summary className="text-sm font-medium text-muted-foreground cursor-pointer">
                            아이디/비밀번호로 연동 (비밀번호는 서버에 저장되지 않습니다)
                        </summary>
                        <div className="space-y-3 pt-3">
                            <p className="text-xs text-muted-foreground">
                                입력한 비밀번호는 로그인에만 사용되고 즉시 폐기됩니다. 서버에 저장되지 않습니다.
                            </p>
                            <div className="grid grid-cols-3 gap-2">
                                <Select value={newAccount.site} onValueChange={(v: string | null) => setNewAccount({ ...newAccount, site: v ?? "SARAMIN" })}>
                                    <SelectTrigger><SelectValue /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="SARAMIN">사람인</SelectItem>
                                        <SelectItem value="JOBPLANET">잡플래닛</SelectItem>
                                        <SelectItem value="JOBKOREA">잡코리아</SelectItem>
                                        <SelectItem value="LINKAREER">링커리어</SelectItem>
                                    </SelectContent>
                                </Select>
                                <Input placeholder="아이디" value={newAccount.accountId}
                                    onChange={e => setNewAccount({ ...newAccount, accountId: e.target.value })} />
                                <Input type="password" placeholder="비밀번호" value={newAccount.password}
                                    onChange={e => setNewAccount({ ...newAccount, password: e.target.value })} />
                            </div>
                            <Button onClick={async () => {
                                if (!token || !newAccount.accountId || !newAccount.password) return;
                                setAccountSaving(true);
                                setMessage("");
                                try {
                                    const res = await accountApi.onetimeLogin(token, newAccount.site, newAccount.accountId, newAccount.password);
                                    setMessage(res.success ? "✅ " + res.message : "❌ " + res.message);
                                    if (res.success) {
                                        accountApi.list(token).then(setAccounts).catch(() => {});
                                        setNewAccount({ site: newAccount.site, accountId: "", password: "" });
                                    }
                                } catch { setMessage("❌ 연동 실패"); }
                                finally { setAccountSaving(false); setTimeout(() => setMessage(""), 5000); }
                            }} disabled={accountSaving}
                                variant="outline" className="w-full">
                                {accountSaving ? "로그인 중..." : "일회용 로그인으로 연동"}
                            </Button>
                        </div>
                    </details>
                </CardContent>
            </Card>

            {/* 희망 직무 설정 */}
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>희망 직무 설정</CardTitle>
                        <Button variant={prefEnabled ? "default" : "outline"} size="sm"
                            onClick={() => setPrefEnabled(!prefEnabled)}
                            className={prefEnabled ? "bg-emerald-600 hover:bg-emerald-700" : ""}>
                            {prefEnabled ? "ON" : "OFF"}
                        </Button>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    <p className="text-sm text-muted-foreground">
                        선택한 직무의 공고만 디스코드 알림 + AI 사전 생성됩니다.
                    </p>

                    {[
                        { label: "사람인", jobs: SARAMIN_JOBS, selected: saraminJobs, setSelected: setSaraminJobs, activeColor: "bg-blue-600 hover:bg-blue-700" },
                        { label: "잡플래닛", jobs: JOBPLANET_JOBS, selected: jobplanetJobs, setSelected: setJobplanetJobs, activeColor: "bg-purple-600 hover:bg-purple-700" },
                        { label: "잡코리아", jobs: JOBKOREA_JOBS, selected: jobkoreaJobs, setSelected: setJobkoreaJobs, activeColor: "bg-red-600 hover:bg-red-700" },
                        { label: "링커리어", jobs: LINKAREER_JOBS, selected: linkareerJobs, setSelected: setLinkareerJobs, activeColor: "bg-teal-600 hover:bg-teal-700" },
                    ].map(({ label, jobs, selected, setSelected, activeColor }) => (
                        <div key={label}>
                            <h4 className="text-sm font-medium mb-2">{label} 직무</h4>
                            <div className="flex flex-wrap gap-2">
                                {jobs.map(job => (
                                    <Badge key={job}
                                        className={`cursor-pointer transition-colors ${selected.includes(job) ? activeColor + " text-white" : "bg-gray-200 text-gray-700 hover:bg-gray-300"}`}
                                        onClick={() => toggleJob(selected, setSelected, job)}>
                                        {job}
                                    </Badge>
                                ))}
                            </div>
                        </div>
                    ))}

                    <Button className="w-full bg-emerald-600 hover:bg-emerald-700"
                        onClick={async () => {
                            if (!token) return;
                            setSaving(true);
                            try {
                                const existing = await preferenceApi.list(token);
                                for (const p of existing) {
                                    await preferenceApi.remove(token, p.id);
                                }
                                const siteJobs: [string, string[]][] = [
                                    ["SARAMIN", saraminJobs],
                                    ["JOBPLANET", jobplanetJobs],
                                    ["JOBKOREA", jobkoreaJobs],
                                    ["LINKAREER", linkareerJobs],
                                ];
                                for (const [site, jobs] of siteJobs) {
                                    for (const job of jobs) {
                                        await preferenceApi.add(token, site, job, job);
                                    }
                                }
                                setMessage("✅ 희망 직무 저장 완료");
                            } catch { setMessage("❌ 저장 실패"); }
                            finally { setSaving(false); setTimeout(() => setMessage(""), 3000); }
                        }}>
                        {saving ? "저장 중..." : "희망 직무 저장"}
                    </Button>
                </CardContent>
            </Card>
        </div>
    );
}
