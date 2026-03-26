"use client";

import { AiTask } from "@/lib/use-ai-task-queue";

const typeLabels: Record<string, string> = {
    COVER_LETTER: "자소서 생성",
    PORTFOLIO: "포트폴리오 생성",
};

function getElapsed(startedAt: number): string {
    const seconds = Math.floor((Date.now() - startedAt) / 1000);
    if (seconds < 60) return `${seconds}초`;
    return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
}

/**
 * 비동기 AI 태스크 진행 상태를 표시하는 인라인 컴포넌트.
 * 페이지 내에서 사용 (고정 위치 아님).
 */
export function AiTaskProgress({ tasks }: { tasks: AiTask[] }) {
    if (tasks.length === 0) return null;

    return (
        <div className="space-y-2">
            {tasks.map(task => (
                <div
                    key={task.taskId}
                    className="flex items-center gap-3 p-3 rounded-lg bg-blue-50 dark:bg-blue-950 border border-blue-200 dark:border-blue-800 text-sm"
                >
                    <span className="animate-spin rounded-full h-4 w-4 border-2 border-blue-500 border-t-transparent flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                        <p className="font-medium text-blue-700 dark:text-blue-300">
                            {typeLabels[task.type] || "AI 처리"} 중...
                        </p>
                        <p className="text-xs text-blue-600/70 dark:text-blue-400/70">
                            {task.status === "PENDING" && "대기열에서 처리 순서를 기다리고 있습니다"}
                            {task.status === "PROCESSING" && "AI가 생성하고 있습니다"}
                            {" · "}
                            {getElapsed(task.startedAt)} 경과
                        </p>
                    </div>
                </div>
            ))}
        </div>
    );
}

/**
 * AI 태스크 완료/실패 알림.
 */
export function AiTaskNotification({
    message,
    type,
    onClose,
}: {
    message: string;
    type: "success" | "error";
    onClose: () => void;
}) {
    return (
        <div
            className={`flex items-center gap-3 p-3 rounded-lg text-sm ${
                type === "success"
                    ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300"
                    : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300"
            }`}
        >
            <span className="flex-shrink-0">
                {type === "success" ? "✓" : "✗"}
            </span>
            <p className="flex-1">{message}</p>
            <button
                onClick={onClose}
                className="text-current opacity-50 hover:opacity-100 text-lg leading-none"
            >
                &times;
            </button>
        </div>
    );
}
