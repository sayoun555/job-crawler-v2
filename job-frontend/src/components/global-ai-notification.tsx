"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { onAiTaskComplete } from "@/lib/websocket";

type Toast = {
    id: string;
    message: string;
    type: "success" | "error";
    link?: string;
};

const typeLabels: Record<string, string> = {
    COVER_LETTER: "자소서",
    PORTFOLIO: "포트폴리오",
    PROJECT_PORTFOLIO: "포트폴리오",
    PREPARE_COMPLETE: "지원서",
};

const STORAGE_KEY = "ai_notification_enabled";

export default function GlobalAiNotification() {
    const { isLoggedIn } = useAuth();
    const router = useRouter();
    const [toasts, setToasts] = useState<Toast[]>([]);
    const [enabled, setEnabled] = useState(() => {
        if (typeof window === "undefined") return true;
        return localStorage.getItem(STORAGE_KEY) !== "false";
    });

    const toggle = () => {
        const next = !enabled;
        setEnabled(next);
        localStorage.setItem(STORAGE_KEY, String(next));
    };

    useEffect(() => {
        if (!isLoggedIn || !enabled) return;

        const cleanup = onAiTaskComplete((data) => {
            const { status, result } = data;

            let label = "AI 작업";
            let link: string | undefined;

            if (data.type) {
                label = typeLabels[data.type] || "AI 작업";
            }

            // PREPARE_COMPLETE → 지원서 페이지 링크
            if (typeof result === "string" && result.includes("PREPARE_COMPLETE")) {
                label = "지원서";
                try {
                    const parsed = JSON.parse(result);
                    if (parsed.applicationId) {
                        link = `/applications/${parsed.applicationId}/preview`;
                    }
                } catch {}
            }

            // PROJECT_PORTFOLIO → 포트폴리오 페이지 링크
            if (data.type === "PROJECT_PORTFOLIO") {
                link = "/portfolio";
            }

            // COVER_LETTER → 현재 페이지에서 처리되므로 링크 불필요
            // 하지만 다른 페이지에 있을 때는 유용
            if (data.type === "COVER_LETTER" && typeof result === "string") {
                // 지원 이력 페이지로
                link = "/applications";
            }

            const toast: Toast = {
                id: Date.now().toString(),
                message: status === "COMPLETED"
                    ? `${label} 생성이 완료되었습니다.`
                    : `${label} 생성에 실패했습니다.`,
                type: status === "COMPLETED" ? "success" : "error",
                link: status === "COMPLETED" ? link : undefined,
            };

            setToasts(prev => [...prev, toast]);

            setTimeout(() => {
                setToasts(prev => prev.filter(t => t.id !== toast.id));
            }, 8000);
        });

        return cleanup;
    }, [isLoggedIn, enabled]);

    const handleClick = (toast: Toast) => {
        if (toast.link) {
            router.push(toast.link);
            setToasts(prev => prev.filter(t => t.id !== toast.id));
        }
    };

    if (!isLoggedIn) return null;

    return (
        <>
            <button
                onClick={toggle}
                className={`fixed top-16 right-4 z-50 p-2 rounded-full shadow-md text-xs transition-colors ${
                    enabled
                        ? "bg-emerald-100 dark:bg-emerald-900 text-emerald-700 dark:text-emerald-300"
                        : "bg-gray-100 dark:bg-gray-800 text-gray-400"
                }`}
                title={enabled ? "AI 알림 끄기" : "AI 알림 켜기"}
            >
                {enabled ? "🔔" : "🔕"}
            </button>

            {enabled && toasts.length > 0 && (
                <div className="fixed top-28 right-4 z-50 space-y-2 max-w-sm">
                    {toasts.map(toast => (
                        <div
                            key={toast.id}
                            onClick={() => handleClick(toast)}
                            className={`flex items-center gap-3 p-3 rounded-lg shadow-lg text-sm ${
                                toast.link ? "cursor-pointer hover:scale-[1.02] transition-transform" : ""
                            } ${
                                toast.type === "success"
                                    ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300"
                                    : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300"
                            }`}
                        >
                            <span className="flex-shrink-0">
                                {toast.type === "success" ? "✓" : "✗"}
                            </span>
                            <div className="flex-1">
                                <p>{toast.message}</p>
                                {toast.link && (
                                    <p className="text-xs opacity-70 mt-0.5">클릭하여 확인</p>
                                )}
                            </div>
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    setToasts(prev => prev.filter(t => t.id !== toast.id));
                                }}
                                className="text-current opacity-50 hover:opacity-100 text-lg leading-none"
                            >
                                &times;
                            </button>
                        </div>
                    ))}
                </div>
            )}
        </>
    );
}
