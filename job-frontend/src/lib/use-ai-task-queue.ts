"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { onAiTaskComplete } from "./websocket";
import { aiApi } from "./api";

export type AiTaskStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "NOT_FOUND";

export type AiTask = {
    taskId: string;
    type: string;
    status: AiTaskStatus;
    result?: string;
    startedAt: number;
};

type TaskCallback = (task: AiTask) => void;

/**
 * AI 비동기 태스크 큐 훅.
 * WebSocket으로 실시간 알림을 받고, 폴링으로 fallback.
 */
const STORAGE_KEY = "ai_active_tasks";

function saveTasks(tasks: Map<string, AiTask>) {
    const active = Array.from(tasks.entries())
        .filter(([, t]) => t.status === "PENDING" || t.status === "PROCESSING");
    if (active.length > 0) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(active));
    } else {
        localStorage.removeItem(STORAGE_KEY);
    }
}

function loadTasks(): Map<string, AiTask> {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (!saved) return new Map();
        const entries: [string, AiTask][] = JSON.parse(saved);
        // 10분 이상 된 태스크는 제거
        const now = Date.now();
        return new Map(entries.filter(([, t]) => now - t.startedAt < 10 * 60 * 1000));
    } catch { return new Map(); }
}

export function useAiTaskQueue(token: string | null) {
    const [tasks, setTasks] = useState<Map<string, AiTask>>(loadTasks);
    const callbacksRef = useRef<Map<string, TaskCallback>>(new Map());
    const pollingRef = useRef<Map<string, ReturnType<typeof setInterval>>>(new Map());

    // tasks 변경 시 localStorage에 저장
    useEffect(() => { saveTasks(tasks); }, [tasks]);

    // WebSocket 리스너 등록
    useEffect(() => {
        const cleanup = onAiTaskComplete((data) => {
            const { taskId, status, result } = data;

            setTasks(prev => {
                const next = new Map(prev);
                const existing = next.get(taskId);
                if (existing) {
                    next.set(taskId, { ...existing, status: status as AiTaskStatus, result });
                }
                return next;
            });

            // 콜백 실행
            const cb = callbacksRef.current.get(taskId);
            if (cb) {
                cb({ taskId, type: "", status: status as AiTaskStatus, result, startedAt: 0 });
                if (status === "COMPLETED" || status === "FAILED") {
                    callbacksRef.current.delete(taskId);
                    // 폴링 중지
                    const interval = pollingRef.current.get(taskId);
                    if (interval) {
                        clearInterval(interval);
                        pollingRef.current.delete(taskId);
                    }
                }
            }
        });

        return cleanup;
    }, []);

    // 폴링 cleanup
    useEffect(() => {
        return () => {
            pollingRef.current.forEach(interval => clearInterval(interval));
            pollingRef.current.clear();
        };
    }, []);

    const startPolling = useCallback((taskId: string) => {
        if (!token || pollingRef.current.has(taskId)) return;

        const interval = setInterval(async () => {
            try {
                const res = await aiApi.asyncStatus(token, taskId);
                if (res.status === "COMPLETED" || res.status === "FAILED") {
                    clearInterval(interval);
                    pollingRef.current.delete(taskId);

                    setTasks(prev => {
                        const next = new Map(prev);
                        const existing = next.get(taskId);
                        if (existing) {
                            next.set(taskId, {
                                ...existing,
                                status: res.status as AiTaskStatus,
                                result: res.result,
                            });
                        }
                        return next;
                    });

                    const cb = callbacksRef.current.get(taskId);
                    if (cb) {
                        cb({
                            taskId,
                            type: res.type || "",
                            status: res.status as AiTaskStatus,
                            result: res.result,
                            startedAt: 0,
                        });
                        callbacksRef.current.delete(taskId);
                    }
                }
            } catch {
                // 폴링 에러 무시 (다음 폴링에서 재시도)
            }
        }, 3000);

        pollingRef.current.set(taskId, interval);
    }, [token]);

    // 새로고침 후 복구된 태스크가 있으면 폴링 재시작
    useEffect(() => {
        if (!token) return;
        tasks.forEach((task, taskId) => {
            if ((task.status === "PENDING" || task.status === "PROCESSING") && !pollingRef.current.has(taskId)) {
                startPolling(taskId);
            }
        });
    }, [token, startPolling]);

    /**
     * 비동기 자소서 생성 시작.
     * WebSocket으로 완료 알림을 받거나, 3초 간격 폴링 fallback.
     */
    const startCoverLetter = useCallback(async (
        jobId: number,
        templateId?: number,
        onComplete?: (result: string) => void,
        onFail?: (error: string) => void,
        projectIds?: string,
    ): Promise<string | null> => {
        if (!token) return null;

        try {
            const { taskId } = await aiApi.asyncCoverLetter(token, jobId, templateId, projectIds);

            const task: AiTask = {
                taskId,
                type: "COVER_LETTER",
                status: "PENDING",
                startedAt: Date.now(),
            };

            setTasks(prev => new Map(prev).set(taskId, task));

            callbacksRef.current.set(taskId, (completed) => {
                if (completed.status === "COMPLETED" && completed.result) {
                    onComplete?.(completed.result);
                } else if (completed.status === "FAILED") {
                    onFail?.(completed.result || "자소서 생성 실패");
                }
            });

            // WebSocket fallback 으로 폴링도 시작
            startPolling(taskId);

            return taskId;
        } catch (e) {
            onFail?.(e instanceof Error ? e.message : "자소서 생성 요청 실패");
            return null;
        }
    }, [token, startPolling]);

    /**
     * 비동기 포트폴리오 생성 시작.
     */
    const startPortfolio = useCallback(async (
        jobId: number,
        templateId?: number,
        onComplete?: (result: string) => void,
        onFail?: (error: string) => void,
        projectIds?: string,
    ): Promise<string | null> => {
        if (!token) return null;

        try {
            const { taskId } = await aiApi.asyncPortfolio(token, jobId, templateId, projectIds);

            const task: AiTask = {
                taskId,
                type: "PORTFOLIO",
                status: "PENDING",
                startedAt: Date.now(),
            };

            setTasks(prev => new Map(prev).set(taskId, task));

            callbacksRef.current.set(taskId, (completed) => {
                if (completed.status === "COMPLETED" && completed.result) {
                    onComplete?.(completed.result);
                } else if (completed.status === "FAILED") {
                    onFail?.(completed.result || "포트폴리오 생성 실패");
                }
            });

            startPolling(taskId);

            return taskId;
        } catch (e) {
            onFail?.(e instanceof Error ? e.message : "포트폴리오 생성 요청 실패");
            return null;
        }
    }, [token, startPolling]);

    /**
     * 특정 태스크 제거 (완료 후 정리).
     */
    const removeTask = useCallback((taskId: string) => {
        setTasks(prev => {
            const next = new Map(prev);
            next.delete(taskId);
            return next;
        });
        callbacksRef.current.delete(taskId);
        const interval = pollingRef.current.get(taskId);
        if (interval) {
            clearInterval(interval);
            pollingRef.current.delete(taskId);
        }
    }, []);

    const activeTasks = Array.from(tasks.values()).filter(
        t => t.status === "PENDING" || t.status === "PROCESSING"
    );

    const hasActiveTasks = activeTasks.length > 0;

    return {
        tasks,
        activeTasks,
        hasActiveTasks,
        startCoverLetter,
        startPortfolio,
        removeTask,
    };
}
