"use client";

import { useState, useEffect, useCallback, Suspense } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import { jobsApi, aiApi, JobPosting } from "@/lib/api";
import { JobCard, JobListItem } from "@/components/job-card";
import { useAuth } from "@/lib/auth-context";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import FilterPanel from "@/components/filter-panel";

type ViewMode = "card" | "list";

type Filters = {
  career: string; education: string; salary: string; location: string;
  saraminJobCategory: string; jobPlanetJobCategory: string; applicationMethod: string; minMatchScore: string; status: string; sortBy: string;
};

function HomeContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const { token } = useAuth();

  const [jobs, setJobs] = useState<JobPosting[]>([]);
  const [loading, setLoading] = useState(true);
  const [matchScores, setMatchScores] = useState<Record<number, number>>({});
  
  const keyword = searchParams.get("keyword") || "";
  const [searchInput, setSearchInput] = useState(keyword);
  
  const source = searchParams.get("source") || undefined;
  const page = Number(searchParams.get("page")) || 0;
  
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [totalPages, setTotalPages] = useState(0);
  const [stats, setStats] = useState({ saramin: 0, jobplanet: 0, linkareer: 0, jobkorea: 0, total: 0 });

  const filters: Filters = {
    career: searchParams.get("career") || "",
    education: searchParams.get("education") || "",
    salary: searchParams.get("salary") || "",
    location: searchParams.get("location") || "",
    saraminJobCategory: searchParams.get("saraminJobCategory") || "",
    jobPlanetJobCategory: searchParams.get("jobPlanetJobCategory") || "",
    applicationMethod: searchParams.get("applicationMethod") || "",
    minMatchScore: searchParams.get("minMatchScore") || "",
    status: searchParams.get("status") || "",
    sortBy: searchParams.get("sortBy") || "latest",
  };

  const updateUrl = useCallback((newParams: Record<string, string | number | undefined>) => {
    const params = new URLSearchParams(searchParams.toString());
    Object.entries(newParams).forEach(([k, v]) => {
      if (v === undefined || v === "" || v === "latest" || v === "all" || (k === "page" && v === 0)) {
        params.delete(k);
      } else {
        params.set(k, String(v));
      }
    });
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }, [searchParams, pathname, router]);

  const loadJobs = useCallback(async () => {
    setLoading(true);
    try {
      const currentSource = searchParams.get("source") || undefined;
      const currentKeyword = searchParams.get("keyword") || "";
      const currentPage = Number(searchParams.get("page")) || 0;
      
      const saraminCat = searchParams.get("saraminJobCategory") || "";
      const jpCat = searchParams.get("jobPlanetJobCategory") || "";
      
      let activeJobCategory = "";
      if (currentSource === "SARAMIN") {
        activeJobCategory = saraminCat;
      } else if (currentSource === "JOBPLANET") {
        activeJobCategory = jpCat;
      } else if (currentSource === "LINKAREER") {
        activeJobCategory = saraminCat;
      } else if (currentSource === "JOBKOREA") {
        activeJobCategory = saraminCat;
      }

      // 정렬 매핑
      const sortByParam = searchParams.get("sortBy") || "latest";
      const sortMap: Record<string, string> = {
        latest: "createdAt,DESC",
        deadline: "deadline,ASC",
        salary: "salary,DESC",
      };

      const data = await jobsApi.list({
        source: currentSource,
        keyword: currentKeyword || undefined,
        page: currentPage,
        size: 20,
        jobCategory: activeJobCategory || undefined,
        career: searchParams.get("career") || undefined,
        education: searchParams.get("education") || undefined,
        location: searchParams.get("location") || undefined,
        applicationMethod: searchParams.get("applicationMethod") || undefined,
        sort: sortMap[sortByParam] || "createdAt,DESC",
      });
      setJobs(data.content);
      setTotalPages(data.totalPages);

      // 로그인 상태면 유저별 적합률 조회
      if (token && data.content.length > 0) {
        try {
          const jobIds = data.content.map((j: JobPosting) => j.id);
          const scores = await aiApi.batchMatchScores(token, jobIds);
          setMatchScores(scores);
        } catch { /* 적합률 조회 실패해도 공고 목록은 표시 */ }
      } else {
        setMatchScores({});
      }
    } catch {
      setJobs([]);
    } finally {
      setLoading(false);
    }
  }, [searchParams, token]);

  const loadStats = async () => {
    try {
      const s = await jobsApi.stats();
      setStats(s);
    } catch { /* ignore */ }
  };

  useEffect(() => { loadJobs(); }, [loadJobs]);
  useEffect(() => { loadStats(); }, []);
  
  // URL에서 외부 진입 시 검색어(searchInput) 동기화용
  useEffect(() => { setSearchInput(keyword); }, [keyword]);

  const handleSearch = () => {
    updateUrl({ keyword: searchInput, page: 0 });
  };

  const handleTabChange = (v: string) => {
    updateUrl({ source: v === "all" ? undefined : v, page: 0 });
  };

  const handleFilterApply = (newFilters: Filters) => {
    updateUrl({ ...newFilters, page: 0 });
  };

  return (
    <div className="flex gap-6">
      {/* 필터 사이드바 (데스크탑) + 모바일 시트 */}
      <FilterPanel source={source} initialFilters={filters} onApply={handleFilterApply} />

      {/* 메인 콘텐츠 */}
      <div className="flex-1 space-y-6 min-w-0">
        {/* 헤더 + 통계 */}
        <div className="space-y-3">
          <div className="flex items-end justify-between">
            <h1 className="text-3xl font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
              채용 공고
            </h1>
            <div className="flex gap-2">
              <Badge variant="outline" className="px-3 py-1">전체 {stats.total}</Badge>
              <Badge className="bg-blue-600 px-3 py-1">사람인 {stats.saramin}</Badge>
              <Badge className="bg-purple-600 px-3 py-1">잡플래닛 {stats.jobplanet}</Badge>
              <Badge className="bg-green-600 px-3 py-1">링커리어 {stats.linkareer}</Badge>
              <Badge className="bg-red-600 px-3 py-1">잡코리아 {stats.jobkorea}</Badge>
            </div>
          </div>
        </div>

        {/* 검색 + 뷰 토글 */}
        <div className="flex gap-2 flex-wrap">
          <Input
            placeholder="키워드 검색 (제목, 회사명)"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            className="max-w-md flex-1"
          />
          <Button onClick={handleSearch} className="bg-emerald-600 hover:bg-emerald-700">검색</Button>
          <div className="ml-auto flex gap-1">
            <Button variant={viewMode === "card" ? "default" : "outline"} size="sm"
              onClick={() => setViewMode("card")}>카드</Button>
            <Button variant={viewMode === "list" ? "default" : "outline"} size="sm"
              onClick={() => setViewMode("list")}>리스트</Button>
          </div>
        </div>

        {/* 사이트별 탭 */}
        <Tabs value={source || "all"} onValueChange={handleTabChange}>
          <TabsList>
            <TabsTrigger value="all">전체</TabsTrigger>
            <TabsTrigger value="SARAMIN">사람인</TabsTrigger>
            <TabsTrigger value="JOBPLANET">잡플래닛</TabsTrigger>
            <TabsTrigger value="LINKAREER">링커리어</TabsTrigger>
            <TabsTrigger value="JOBKOREA">잡코리아</TabsTrigger>
          </TabsList>

          <TabsContent value="all" className="mt-4">
            <JobList jobs={jobs} loading={loading} viewMode={viewMode} matchScores={matchScores} />
          </TabsContent>
          <TabsContent value="SARAMIN" className="mt-4">
            <JobList jobs={jobs} loading={loading} viewMode={viewMode} matchScores={matchScores} />
          </TabsContent>
          <TabsContent value="JOBPLANET" className="mt-4">
            <JobList jobs={jobs} loading={loading} viewMode={viewMode} matchScores={matchScores} />
          </TabsContent>
          <TabsContent value="LINKAREER" className="mt-4">
            <JobList jobs={jobs} loading={loading} viewMode={viewMode} matchScores={matchScores} />
          </TabsContent>
          <TabsContent value="JOBKOREA" className="mt-4">
            <JobList jobs={jobs} loading={loading} viewMode={viewMode} matchScores={matchScores} />
          </TabsContent>
        </Tabs>

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
                  className={p === page ? "bg-emerald-600 hover:bg-emerald-700" : ""}
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
    </div>
  );
}

export default function HomePage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-muted-foreground">URL 데이터를 불러오는 중...</div>}>
      <HomeContent />
    </Suspense>
  );
}

function JobList({ jobs, loading, viewMode, matchScores }: {
  jobs: JobPosting[]; loading: boolean; viewMode: ViewMode; matchScores: Record<number, number>;
}) {
  if (loading) {
    const { CardSkeleton } = require("@/components/ui/skeleton");
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {Array.from({ length: 6 }).map((_: unknown, i: number) => (
          <CardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (jobs.length === 0) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        검색 결과가 없습니다.
      </div>
    );
  }

  if (viewMode === "card") {
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {jobs.map((job) => <JobCard key={job.id} job={job} matchScore={matchScores[job.id]} />)}
      </div>
    );
  }

  return (
    <div className="border rounded-lg">
      {jobs.map((job) => <JobListItem key={job.id} job={job} matchScore={matchScores[job.id]} />)}
    </div>
  );
}
