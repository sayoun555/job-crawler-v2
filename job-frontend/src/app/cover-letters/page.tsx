"use client";

import { useState, useEffect, useCallback, Suspense } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import { coverLettersApi, CoverLetterItem } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import Link from "next/link";

const SORT_OPTIONS = [
    { value: "createdAt,DESC", label: "최신순" },
    { value: "scrapCount,DESC", label: "조회순" },
    { value: "gpa,DESC", label: "학점 높은순" },
    { value: "gpa,ASC", label: "학점 낮은순" },
    { value: "company,ASC", label: "기업명순" },
];

function CoverLetterContent() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const pathname = usePathname();

    const keyword = searchParams.get("keyword") || "";
    const school = searchParams.get("school") || "";
    const sortParam: string = searchParams.get("sortBy") || "createdAt,DESC";
    const [searchInput, setSearchInput] = useState(keyword);
    const [schoolInput, setSchoolInput] = useState(school);
    const page = Number(searchParams.get("page")) || 0;

    const [items, setItems] = useState<CoverLetterItem[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);

    const updateUrl = useCallback((newParams: Record<string, string | number | undefined>) => {
        const params = new URLSearchParams(searchParams.toString());
        Object.entries(newParams).forEach(([k, v]) => {
            if (v === undefined || v === "" || (k === "page" && v === 0)) {
                params.delete(k);
            } else {
                params.set(k, String(v));
            }
        });
        router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    }, [searchParams, pathname, router]);

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const data = await coverLettersApi.list({
                keyword: searchParams.get("keyword") || undefined,
                school: searchParams.get("school") || undefined,
                page: Number(searchParams.get("page")) || 0,
                size: 20,
                sortBy: searchParams.get("sortBy") || "createdAt,DESC",
            });
            setItems(data.content);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch { setItems([]); }
        finally { setLoading(false); }
    }, [searchParams]);

    useEffect(() => { loadData(); }, [loadData]);
    useEffect(() => { setSearchInput(keyword); }, [keyword]);
    useEffect(() => { setSchoolInput(school); }, [school]);

    const handleSearch = () => {
        updateUrl({ keyword: searchInput, school: schoolInput, page: 0 });
    };

    return (
        <div className="max-w-5xl mx-auto space-y-6">
            <div className="flex items-end justify-between">
                <h1 className="text-3xl font-bold bg-gradient-to-r from-orange-500 to-red-500 bg-clip-text text-transparent">
                    합격 자소서
                </h1>
                <Badge variant="outline" className="px-3 py-1">전체 {totalElements}건</Badge>
            </div>

            {/* 검색 + 필터 */}
            <div className="flex gap-2 flex-wrap">
                <Input
                    placeholder="회사명, 직무 검색"
                    value={searchInput}
                    onChange={e => setSearchInput(e.target.value)}
                    onKeyDown={e => e.key === "Enter" && handleSearch()}
                    className="max-w-xs flex-1"
                />
                <Input
                    placeholder="학교 필터"
                    value={schoolInput}
                    onChange={e => setSchoolInput(e.target.value)}
                    onKeyDown={e => e.key === "Enter" && handleSearch()}
                    className="max-w-[150px]"
                />
                <Button onClick={handleSearch} className="bg-orange-600 hover:bg-orange-700">검색</Button>
                <select
                    className="ml-auto rounded-md border px-3 py-2 text-sm bg-background"
                    value={sortParam}
                    onChange={e => updateUrl({ sortBy: e.target.value, page: 0 })}>
                    {SORT_OPTIONS.map(o => (
                        <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                </select>
            </div>

            {loading ? (
                <div className="flex justify-center py-12">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-orange-500" />
                </div>
            ) : items.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">자소서가 없습니다.</div>
            ) : (
                <div className="space-y-3">
                    {items.map(item => (
                        <Link key={item.id} href={`/cover-letters/${item.id}`}>
                            <Card className="hover:shadow-lg hover:border-orange-500/50 transition-all cursor-pointer mb-3">
                                <CardContent className="p-4">
                                    <div className="space-y-2">
                                        {/* 1줄: 회사 / 직무 */}
                                        <div className="flex items-center justify-between gap-2">
                                            <div className="flex items-center gap-2 min-w-0">
                                                <span className="font-semibold text-orange-600">{item.company}</span>
                                                {item.position && (
                                                    <span className="text-sm text-muted-foreground truncate">{item.position}</span>
                                                )}
                                            </div>
                                            <div className="flex gap-1 shrink-0">
                                                {[item.period, item.companyType, item.careerType]
                                                    .filter(Boolean)
                                                    .map((tag, i) => (
                                                        <CoverLetterBadge key={i} text={tag!} />
                                                    ))}
                                            </div>
                                        </div>
                                        {/* 2줄: 학교 / 전공 / 학점 */}
                                        <div className="flex items-center gap-2 text-xs">
                                            {item.school && <Badge variant="secondary">{item.school}</Badge>}
                                            {item.major && <span className="text-muted-foreground">{item.major}</span>}
                                            {item.gpa && <span className="font-medium text-emerald-600">{item.gpa}</span>}
                                        </div>
                                        {/* 3줄: 스펙 */}
                                        {item.specs && (
                                            <p className="text-xs text-muted-foreground line-clamp-1">{item.specs}</p>
                                        )}
                                    </div>
                                </CardContent>
                            </Card>
                        </Link>
                    ))}
                </div>
            )}

            {/* 페이지네이션 */}
            {totalPages > 1 && (
                <div className="flex justify-center items-center gap-1 flex-wrap">
                    <Button variant="outline" size="sm" disabled={page === 0}
                        onClick={() => updateUrl({ page: 0 })}>{"<<"}</Button>
                    <Button variant="outline" size="sm" disabled={page === 0}
                        onClick={() => updateUrl({ page: page - 1 })}>{"<"}</Button>
                    {(() => {
                        const pages: number[] = [];
                        const start = Math.max(0, Math.min(page - 4, totalPages - 10));
                        const end = Math.min(totalPages, start + 10);
                        for (let i = start; i < end; i++) pages.push(i);
                        return pages.map(p => (
                            <Button key={p} size="sm"
                                variant={p === page ? "default" : "outline"}
                                className={p === page ? "bg-orange-600 hover:bg-orange-700" : ""}
                                onClick={() => updateUrl({ page: p })}>
                                {p + 1}
                            </Button>
                        ));
                    })()}
                    <Button variant="outline" size="sm" disabled={page >= totalPages - 1}
                        onClick={() => updateUrl({ page: page + 1 })}>{">"}</Button>
                    <Button variant="outline" size="sm" disabled={page >= totalPages - 1}
                        onClick={() => updateUrl({ page: totalPages - 1 })}>{">>"}</Button>
                    <span className="ml-2 text-sm text-muted-foreground">{page + 1} / {totalPages}</span>
                </div>
            )}
        </div>
    );
}

function CoverLetterBadge({ text }: { text: string }) {
    const isPeriod = /20\d{2}/.test(text) && (/상반기|하반기/.test(text));
    const isCompanyType = /대기업|중견|공기업|스타트업|외국계/.test(text);

    if (isPeriod) {
        return <span className="inline-flex items-center rounded-md bg-orange-600 px-2 py-0.5 text-xs font-medium text-white">{text}</span>;
    }
    if (isCompanyType) {
        return <span className="inline-flex items-center rounded-md border px-2 py-0.5 text-xs text-muted-foreground">{text}</span>;
    }
    return <span className="inline-flex items-center rounded-md bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">{text}</span>;
}

export default function CoverLettersPage() {
    return (
        <Suspense fallback={<div className="p-8 text-center text-muted-foreground">로딩 중...</div>}>
            <CoverLetterContent />
        </Suspense>
    );
}
