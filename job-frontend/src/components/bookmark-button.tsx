"use client";

import { useState } from "react";
import { Heart } from "lucide-react";
import { bookmarksApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";

export function BookmarkButton({
    jobId,
    initialBookmarked = false,
    size = "sm",
    onToggle,
}: {
    jobId: number;
    initialBookmarked?: boolean;
    size?: "sm" | "icon";
    onToggle?: (bookmarked: boolean) => void;
}) {
    const { token, isLoggedIn } = useAuth();
    const [bookmarked, setBookmarked] = useState(initialBookmarked);
    const [loading, setLoading] = useState(false);

    const handleToggle = async (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        if (!isLoggedIn || !token || loading) return;

        setLoading(true);
        try {
            if (bookmarked) {
                await bookmarksApi.remove(token, jobId);
                setBookmarked(false);
                onToggle?.(false);
            } else {
                await bookmarksApi.add(token, jobId);
                setBookmarked(true);
                onToggle?.(true);
            }
        } catch {
            // 이미 북마크/삭제된 경우 무시
        } finally {
            setLoading(false);
        }
    };

    if (!isLoggedIn) return null;

    return (
        <Button
            variant="ghost"
            size={size}
            onClick={handleToggle}
            disabled={loading}
            className={`p-1 h-auto ${bookmarked ? "text-red-500 hover:text-red-600" : "text-muted-foreground hover:text-red-500"}`}
            title={bookmarked ? "관심 공고 해제" : "관심 공고 추가"}
        >
            <Heart className={`h-4 w-4 ${bookmarked ? "fill-current" : ""}`} />
        </Button>
    );
}
