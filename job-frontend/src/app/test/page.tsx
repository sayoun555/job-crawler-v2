"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { testApi, type ChecklistItem } from "@/lib/api";

/**
 * Step 10: AI 테스트 페이지 (기능 구현 검증 체크리스트).
 * 섹션별로 체크박스 형태로 기능 검증 항목을 표시.
 */

const SECTION_NAMES: Record<string, string> = {
    "10.1": "인증 및 사용자 관리",
    "10.2": "크롤러 엔진",
    "10.3": "데이터 파이프라인",
    "10.4": "프로젝트 관리",
    "10.5": "자소서/포트폴리오 자동화",
    "10.6": "프론트엔드",
    "10.7": "Auto-Apply (자동 지원)",
    "10.8": "디스코드 알림",
    "10.9": "보안 점검",
};

export default function TestPage() {
    const [items, setItems] = useState<ChecklistItem[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadChecklist();
    }, []);

    const loadChecklist = async () => {
        try {
            const data = await testApi.getChecklist();
            setItems(data);
        } catch {
            console.error("체크리스트 로딩 실패");
        } finally {
            setLoading(false);
        }
    };

    const handleToggle = async (item: ChecklistItem) => {
        const newChecked = !item.checked;
        setItems((prev) =>
            prev.map((i) => (i.id === item.id ? { ...i, checked: newChecked } : i))
        );
        try {
            await testApi.toggleItem(item.id, newChecked);
        } catch {
            // 롤백
            setItems((prev) =>
                prev.map((i) => (i.id === item.id ? { ...i, checked: !newChecked } : i))
            );
        }
    };

    if (loading) return <div className="flex justify-center py-20">로딩 중...</div>;

    // 섹션별 그룹핑
    const grouped = items.reduce<Record<string, ChecklistItem[]>>((acc, item) => {
        if (!acc[item.section]) acc[item.section] = [];
        acc[item.section].push(item);
        return acc;
    }, {});

    const totalChecked = items.filter((i) => i.checked).length;
    const totalItems = items.length;
    const progress = totalItems > 0 ? Math.round((totalChecked / totalItems) * 100) : 0;

    return (
        <div className="container mx-auto py-6 px-4 max-w-4xl">
            <h1 className="text-2xl font-bold mb-2">AI 테스트 체크리스트</h1>
            <p className="text-muted-foreground mb-6">기능 구현 후 하나씩 체크해가며 검증합니다.</p>

            {/* 진행률 */}
            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-medium">전체 진행률</span>
                        <span className="text-sm font-bold">{totalChecked}/{totalItems} ({progress}%)</span>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-3">
                        <div
                            className="h-3 rounded-full transition-all duration-500"
                            style={{
                                width: `${progress}%`,
                                background: progress === 100
                                    ? "linear-gradient(90deg, #22c55e, #16a34a)"
                                    : "linear-gradient(90deg, #3b82f6, #6366f1)",
                            }}
                        />
                    </div>
                </CardContent>
            </Card>

            {/* 섹션별 체크리스트 */}
            <div className="space-y-4">
                {Object.entries(grouped).map(([section, sectionItems]) => {
                    const sectionChecked = sectionItems.filter((i) => i.checked).length;
                    const sectionTotal = sectionItems.length;
                    const allDone = sectionChecked === sectionTotal;

                    return (
                        <Card key={section} className={allDone ? "border-green-300 bg-green-50/30" : ""}>
                            <CardHeader className="pb-3">
                                <CardTitle className="flex items-center justify-between text-base">
                                    <span>
                                        {allDone ? "✅" : "📋"} {section} {SECTION_NAMES[section] || section}
                                    </span>
                                    <span className="text-sm text-muted-foreground font-normal">
                                        {sectionChecked}/{sectionTotal}
                                    </span>
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                {sectionItems.map((item) => (
                                    <label
                                        key={item.id}
                                        className="flex items-center gap-3 p-2 rounded-md hover:bg-muted/50 cursor-pointer transition-colors"
                                    >
                                        <input
                                            type="checkbox"
                                            checked={item.checked}
                                            onChange={() => handleToggle(item)}
                                            className="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                        />
                                        <span className={item.checked ? "line-through text-muted-foreground" : ""}>
                                            {item.label}
                                        </span>
                                    </label>
                                ))}
                            </CardContent>
                        </Card>
                    );
                })}
            </div>
        </div>
    );
}
