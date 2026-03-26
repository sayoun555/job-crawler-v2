"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { userApi, accountApi, preferenceApi, ExternalAccount } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

type JobPreference = { id: number; site: string; categoryName: string; enabled: boolean };

const SITE_LABELS: Record<string, string> = {
    SARAMIN: "사람인", JOBPLANET: "잡플래닛", JOBKOREA: "잡코리아", LINKAREER: "링커리어",
};
const SITE_COLORS: Record<string, string> = {
    SARAMIN: "bg-blue-600", JOBPLANET: "bg-purple-600", JOBKOREA: "bg-red-600", LINKAREER: "bg-teal-600",
};

export default function DashboardPage() {
    const { token, isLoggedIn } = useAuth();
    const [nickname, setNickname] = useState("");
    const [notificationEnabled, setNotificationEnabled] = useState(false);
    const [notificationHours, setNotificationHours] = useState("");
    const [accounts, setAccounts] = useState<ExternalAccount[]>([]);
    const [preferences, setPreferences] = useState<JobPreference[]>([]);

    useEffect(() => {
        if (!token) return;
        userApi.me(token).then(u => {
            setNickname(u.nickname);
            setNotificationEnabled(u.notificationEnabled);
            setNotificationHours(u.notificationHours || "9,18");
        }).catch(() => {});
        accountApi.list(token).then(setAccounts).catch(() => {});
        preferenceApi.list(token).then(setPreferences).catch(() => {});
    }, [token]);

    if (!isLoggedIn) return <div className="text-center py-20 text-muted-foreground">로그인이 필요합니다.</div>;

    const linkedSites = accounts.map(a => a.site);
    const enabledPrefs = preferences.filter(p => p.enabled);
    const hours = notificationHours.split(",").filter(h => h.trim()).map(Number).sort((a, b) => a - b);

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <h1 className="text-2xl font-bold">{nickname}님의 관리</h1>

            {/* 알림 상태 요약 */}
            <Card>
                <CardHeader><CardTitle>알림 설정 현황</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                    <div className="flex items-center justify-between">
                        <span className="text-sm">알림 수신</span>
                        <Badge className={notificationEnabled ? "bg-emerald-600" : "bg-gray-400"}>
                            {notificationEnabled ? "ON" : "OFF"}
                        </Badge>
                    </div>
                    {notificationEnabled && (
                        <div className="flex items-center justify-between">
                            <span className="text-sm">알림 시간</span>
                            <div className="flex gap-1">
                                {hours.map(h => (
                                    <Badge key={h} variant="outline" className="text-xs">{h}시</Badge>
                                ))}
                            </div>
                        </div>
                    )}
                    <Link href="/settings" className="text-sm text-emerald-600 hover:underline block pt-1">
                        설정 변경하기 &rarr;
                    </Link>
                </CardContent>
            </Card>

            {/* 연동된 사이트 */}
            <Card>
                <CardHeader><CardTitle>연동된 사이트</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                    {["SARAMIN", "JOBPLANET", "JOBKOREA", "LINKAREER"].map(site => {
                        const linked = linkedSites.includes(site);
                        return (
                            <div key={site} className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <Badge className={SITE_COLORS[site]}>{SITE_LABELS[site]}</Badge>
                                </div>
                                <Badge variant={linked ? "default" : "outline"}
                                    className={linked ? "bg-emerald-600" : "text-muted-foreground"}>
                                    {linked ? "연동됨" : "미연동"}
                                </Badge>
                            </div>
                        );
                    })}
                    <Link href="/settings" className="text-sm text-emerald-600 hover:underline block pt-1">
                        연동 관리하기 &rarr;
                    </Link>
                </CardContent>
            </Card>

            {/* 희망 직무 */}
            <Card>
                <CardHeader><CardTitle>희망 직무</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                    {enabledPrefs.length > 0 ? (
                        <div className="space-y-2">
                            {Object.entries(
                                enabledPrefs.reduce<Record<string, string[]>>((acc, p) => {
                                    (acc[p.site] = acc[p.site] || []).push(p.categoryName);
                                    return acc;
                                }, {})
                            ).map(([site, categories]) => (
                                <div key={site}>
                                    <span className="text-sm font-medium">{SITE_LABELS[site] || site}</span>
                                    <div className="flex flex-wrap gap-1 mt-1">
                                        {categories.map(cat => (
                                            <Badge key={cat} variant="outline" className="text-xs">{cat}</Badge>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground">설정된 희망 직무가 없습니다.</p>
                    )}
                    <Link href="/settings" className="text-sm text-emerald-600 hover:underline block pt-1">
                        직무 설정하기 &rarr;
                    </Link>
                </CardContent>
            </Card>

            {/* 빠른 이동 */}
            <Card>
                <CardHeader><CardTitle>빠른 이동</CardTitle></CardHeader>
                <CardContent>
                    <div className="grid grid-cols-2 gap-3">
                        <Link href="/profile" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <div>
                                <p className="font-medium text-sm">프로필</p>
                                <p className="text-xs text-muted-foreground">이력 관리</p>
                            </div>
                        </Link>
                        <Link href="/settings" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <div>
                                <p className="font-medium text-sm">설정</p>
                                <p className="text-xs text-muted-foreground">알림 · 계정 · 직무</p>
                            </div>
                        </Link>
                        <Link href="/applications" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <div>
                                <p className="font-medium text-sm">지원 이력</p>
                                <p className="text-xs text-muted-foreground">지원한 공고 관리</p>
                            </div>
                        </Link>
                        <Link href="/templates" className="flex items-center gap-2 p-3 rounded-lg border hover:bg-muted/50 transition-colors">
                            <div>
                                <p className="font-medium text-sm">템플릿</p>
                                <p className="text-xs text-muted-foreground">자소서 · 포트폴리오</p>
                            </div>
                        </Link>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
