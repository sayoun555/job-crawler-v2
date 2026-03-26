"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

type FilterValues = {
    career: string;
    education: string;
    salary: string;
    location: string;
    saraminJobCategory: string;
    jobPlanetJobCategory: string;
    applicationMethod: string;
    minMatchScore: string;
    status: string;
    sortBy: string;
};

const INITIAL_FILTERS: FilterValues = {
    career: "", education: "", salary: "", location: "", saraminJobCategory: "", jobPlanetJobCategory: "",
    applicationMethod: "", minMatchScore: "", status: "", sortBy: "latest",
};

const SARAMIN_CATEGORIES = ["전체", "서버/네트워크/보안", "웹개발", "앱개발", "프론트엔드", "퍼블리싱/UI개발", "게임개발", "데이터/AI", "기획/PM", "QA/테스트"];
const JOBPLANET_CATEGORIES = ["전체", "웹개발", "서버개발", "프론트엔드개발", "안드로이드개발", "iOS개발", "데이터엔지니어", "기획자", "디자이너", "QA", "머신러닝개발", "시스템엔지니어"];
const LINKAREER_CATEGORIES = ["전체", "개발", "기획", "디자인", "마케팅", "영업", "데이터/AI", "생산/제조", "연구개발", "기타"];
const COMMON_CATEGORIES = ["전체", "웹개발", "서버/백엔드", "프론트엔드", "모바일", "앱개발", "데이터", "기획", "디자인", "QA", "기타"];

// 사람인/잡플래닛 벤치마킹 필터 항목
// ===== 사이트별 필터 옵션 (실제 DB 데이터 패턴 기반) =====

// 사람인: "경력무관 · 정규직", "5 ~ 12년 · 정규직", "신입 · 경력 · 정규직"
const SARAMIN_CAREER = ["전체", "신입", "경력", "경력무관", "정규직", "계약직"];
const SARAMIN_EDUCATION = ["전체", "학력무관", "고졸", "대학(2,3년)", "대학교(4년)", "석사"];
const SARAMIN_METHOD = [
    { value: "", label: "전체" },
    { value: "SARAMIN_APPLY", label: "사람인 입사지원" },
    { value: "HOMEPAGE", label: "홈페이지 지원" },
    { value: "EMAIL", label: "이메일 지원" },
    { value: "UNKNOWN", label: "기타" },
];

// 잡플래닛: 별도 패턴
const JOBPLANET_CAREER = ["전체", "신입", "경력", "인턴"];
const JOBPLANET_EDUCATION = ["전체", "학력무관", "고졸", "전문대졸", "대졸", "석사", "박사"];
const JOBPLANET_METHOD = [
    { value: "", label: "전체" },
    { value: "HOMEPAGE", label: "홈페이지 지원" },
    { value: "UNKNOWN", label: "기타" },
];

// 링커리어: "신입", "체험형 인턴", "채용연계형 인턴", "경력직", 학력 없음
const LINKAREER_CAREER = ["전체", "신입", "경력직", "인턴", "체험형 인턴", "채용연계형 인턴", "계약직"];
const LINKAREER_METHOD = [
    { value: "", label: "전체" },
    { value: "HOMEPAGE", label: "홈페이지 지원" },
];

// 잡코리아: "경력", "신입·경력", "대졸↑", "정규직"
const JOBKOREA_CATEGORIES = ["전체", "백엔드개발자", "프론트엔드개발자", "웹개발자", "앱개발자", "소프트웨어개발자", "시스템엔지니어", "네트워크엔지니어", "데이터사이언티스트", "AI/ML엔지니어", "게임개발자", "보안엔지니어", "하드웨어개발자"];
const JOBKOREA_CAREER = ["전체", "신입", "경력", "신입·경력", "인턴", "정규직", "계약직"];
const JOBKOREA_EDUCATION = ["전체", "학력무관", "고졸↑", "초대졸↑", "대졸↑", "석사↑", "박사↑"];
const JOBKOREA_METHOD = [
    { value: "", label: "전체" },
    { value: "HOMEPAGE", label: "홈페이지 지원" },
];

// 전체 탭: 모든 사이트 통합
const COMMON_CAREER = ["전체", "신입", "경력", "인턴", "정규직", "계약직"];
const COMMON_EDUCATION = ["전체", "학력무관", "고졸", "대학(2,3년)", "대학교(4년)", "석사"];
const COMMON_METHOD = [
    { value: "", label: "전체" },
    { value: "HOMEPAGE", label: "홈페이지 지원" },
    { value: "UNKNOWN", label: "기타" },
];

const LOCATION_OPTIONS = [
    "전체", "서울", "경기", "인천", "부산", "대구", "대전", "광주",
    "울산", "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
];
const STATUS_OPTIONS = [
    { value: "", label: "전체" },
    { value: "NOT_APPLIED", label: "미지원" },
    { value: "PENDING", label: "대기 중" },
    { value: "APPLIED", label: "지원 완료" },
    { value: "MANUALLY_MARKED", label: "수동 지원" },
    { value: "FAILED", label: "지원 실패" },
];
const SORT_OPTIONS = [
    { value: "latest", label: "최신순" },
    { value: "deadline", label: "마감임박순" },
    { value: "salary", label: "연봉순" },
];

export default function FilterPanel({
    source,
    initialFilters,
    onApply,
}: {
    source?: string;
    initialFilters?: Partial<FilterValues>;
    onApply: (filters: FilterValues) => void;
}) {
    const [filters, setFilters] = useState<FilterValues>({ ...INITIAL_FILTERS, ...initialFilters });
    const activeCount = Object.values(filters).filter((v) => v && v !== "latest").length;

    const getCategories = () => {
        if (source === "SARAMIN") return SARAMIN_CATEGORIES;
        if (source === "JOBPLANET") return JOBPLANET_CATEGORIES;
        if (source === "LINKAREER") return LINKAREER_CATEGORIES;
        if (source === "JOBKOREA") return JOBKOREA_CATEGORIES;
        return COMMON_CATEGORIES;
    };

    const getCareerOptions = () => {
        if (source === "SARAMIN") return SARAMIN_CAREER;
        if (source === "JOBPLANET") return JOBPLANET_CAREER;
        if (source === "LINKAREER") return LINKAREER_CAREER;
        if (source === "JOBKOREA") return JOBKOREA_CAREER;
        return COMMON_CAREER;
    };

    const getEducationOptions = () => {
        if (source === "SARAMIN") return SARAMIN_EDUCATION;
        if (source === "JOBPLANET") return JOBPLANET_EDUCATION;
        if (source === "LINKAREER") return [];
        if (source === "JOBKOREA") return JOBKOREA_EDUCATION;
        return COMMON_EDUCATION;
    };

    const getMethodOptions = () => {
        if (source === "SARAMIN") return SARAMIN_METHOD;
        if (source === "JOBPLANET") return JOBPLANET_METHOD;
        if (source === "LINKAREER") return LINKAREER_METHOD;
        if (source === "JOBKOREA") return JOBKOREA_METHOD;
        return COMMON_METHOD;
    };

    const update = (key: keyof FilterValues, value: string | null) => {
        const v = value ?? "";
        setFilters((prev) => ({ ...prev, [key]: v === "all" ? "" : v }));
    };

    const reset = () => setFilters(INITIAL_FILTERS);

    const FilterContent = () => (
        <div className="space-y-6">
            {/* 사이트별 직무 카테고리 (해당 탭에서만 표시) */}
            {source === "SARAMIN" && (
            <div>
                <label className="text-sm font-medium mb-2 block text-blue-700">직무</label>
                <Select value={filters.saraminJobCategory} onValueChange={(v) => update("saraminJobCategory", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {SARAMIN_CATEGORIES.map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "all" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            )}

            {source === "JOBPLANET" && (
            <div>
                <label className="text-sm font-medium mb-2 block text-purple-700">직무</label>
                <Select value={filters.jobPlanetJobCategory} onValueChange={(v) => update("jobPlanetJobCategory", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {JOBPLANET_CATEGORIES.map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "all" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            )}

            {source === "LINKAREER" && (
            <div>
                <label className="text-sm font-medium mb-2 block text-green-700">직무</label>
                <Select value={filters.saraminJobCategory} onValueChange={(v) => update("saraminJobCategory", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {LINKAREER_CATEGORIES.map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "all" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            )}

            {source === "JOBKOREA" && (
            <div>
                <label className="text-sm font-medium mb-2 block text-red-700">직무</label>
                <Select value={filters.saraminJobCategory} onValueChange={(v) => update("saraminJobCategory", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {JOBKOREA_CATEGORIES.map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "all" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            )}

            {/* 경력/채용형태 */}
            <div>
                <label className="text-sm font-medium mb-2 block">경력/채용형태</label>
                <Select value={filters.career} onValueChange={(v) => update("career", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {getCareerOptions().map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "all" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>

            {/* 학력 (링커리어는 학력 데이터 없으므로 숨김) */}
            {getEducationOptions().length > 0 && (
            <div>
                <label className="text-sm font-medium mb-2 block">학력</label>
                <Select value={filters.education} onValueChange={(v) => update("education", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {getEducationOptions().map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            )}

            {/* 근무 지역 */}
            <div>
                <label className="text-sm font-medium mb-2 block">근무 지역</label>
                <Select value={filters.location} onValueChange={(v) => update("location", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {LOCATION_OPTIONS.map((o) => (
                            <SelectItem key={o} value={o === "전체" ? "" : o}>{o}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>

            {/* 지원 방식 (사이트 선택 시만 표시) */}
            {source && (
            <div>
                <label className="text-sm font-medium mb-2 block">지원 방식</label>
                <Select value={filters.applicationMethod} onValueChange={(v) => update("applicationMethod", v)}>
                    <SelectTrigger><SelectValue placeholder="전체" /></SelectTrigger>
                    <SelectContent>
                        {getMethodOptions().map((o) => (
                            <SelectItem key={o.value || "all"} value={o.value || "all"}>{o.label}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
            )}

            {/* 정렬 */}
            <div>
                <label className="text-sm font-medium mb-2 block">정렬</label>
                <Select value={filters.sortBy} onValueChange={(v) => update("sortBy", v)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                        {SORT_OPTIONS.map((o) => (
                            <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>

            <div className="flex gap-2 pt-4">
                <Button variant="outline" className="flex-1" onClick={reset}>초기화</Button>
                <Button className="flex-1" onClick={() => onApply(filters)}>적용</Button>
            </div>
        </div>
    );

    return (
        <>
            {/* 데스크탑: 사이드바 */}
            <aside className="hidden lg:block w-64 shrink-0">
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center justify-between text-base">
                            필터
                            {activeCount > 0 && <Badge variant="secondary">{activeCount}</Badge>}
                        </CardTitle>
                    </CardHeader>
                    <CardContent><FilterContent /></CardContent>
                </Card>
            </aside>

            {/* 모바일/태블릿: Sheet */}
            <div className="lg:hidden">
                <Sheet>
                    <SheetTrigger render={
                        <Button variant="outline" size="sm" className="gap-2">
                            필터 {activeCount > 0 && <Badge variant="secondary">{activeCount}</Badge>}
                        </Button>
                    } />
                    <SheetContent side="left" className="w-80 overflow-y-auto">
                        <SheetHeader><SheetTitle>필터</SheetTitle></SheetHeader>
                        <div className="mt-6"><FilterContent /></div>
                    </SheetContent>
                </Sheet>
            </div>
        </>
    );
}
