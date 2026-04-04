"use client";

import { useState, useEffect, useCallback } from "react";
import { bookmarksApi, Bookmark } from "@/lib/api";
import { JobCard } from "@/components/job-card";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { useRouter } from "next/navigation";

export default function BookmarksPage() {
    const { token, isLoggedIn } = useAuth();
    const router = useRouter();
    const [bookmarks, setBookmarks] = useState<Bookmark[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const loadBookmarks = useCallback(async () => {
        if (!token) return;
        setLoading(true);
        try {
            const data = await bookmarksApi.list(token, page);
            setBookmarks(data.content);
            setTotalPages(data.totalPages);
        } catch {
            setBookmarks([]);
        } finally {
            setLoading(false);
        }
    }, [token, page]);

    useEffect(() => {
        if (!isLoggedIn) {
            router.push("/login");
            return;
        }
        loadBookmarks();
    }, [isLoggedIn, loadBookmarks, router]);

    if (!isLoggedIn) return null;

    return (
        <div className="space-y-6">
            <h1 className="text-3xl font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                관심 공고
            </h1>

            {loading ? (
                <div className="text-center py-12 text-muted-foreground">불러오는 중...</div>
            ) : bookmarks.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                    저장한 관심 공고가 없습니다.
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {bookmarks.map((bm) => (
                        <JobCard key={bm.id} job={bm.jobPosting} bookmarked={true} />
                    ))}
                </div>
            )}

            {totalPages > 1 && (
                <div className="flex justify-center items-center gap-1">
                    <Button variant="outline" size="sm" disabled={page === 0}
                        onClick={() => setPage(p => p - 1)}>{"<"}</Button>
                    {Array.from({ length: totalPages }, (_, i) => (
                        <Button key={i} size="sm"
                            variant={i === page ? "default" : "outline"}
                            className={i === page ? "bg-emerald-600 hover:bg-emerald-700" : ""}
                            onClick={() => setPage(i)}>
                            {i + 1}
                        </Button>
                    )).slice(Math.max(0, page - 4), Math.min(totalPages, page + 6))}
                    <Button variant="outline" size="sm" disabled={page >= totalPages - 1}
                        onClick={() => setPage(p => p + 1)}>{">"}</Button>
                    <span className="ml-2 text-sm text-muted-foreground">{page + 1} / {totalPages}</span>
                </div>
            )}
        </div>
    );
}
