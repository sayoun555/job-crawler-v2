import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";

let stompClient: Client | null = null;
const listeners: Map<string, ((msg: any) => void)[]> = new Map();

export function connectWebSocket(userId: number) {
    if (stompClient?.connected) return;

    stompClient = new Client({
        webSocketFactory: () => new SockJS(WS_URL) as any,
        reconnectDelay: 5000,
        onConnect: () => {
            console.log("[WebSocket] 연결됨");
            // 유저별 AI 태스크 알림 구독
            stompClient?.subscribe(`/topic/ai/${userId}`, (message) => {
                try {
                    const data = JSON.parse(message.body);
                    const callbacks = listeners.get("ai-task") || [];
                    callbacks.forEach(cb => cb(data));
                } catch (e) {
                    console.error("[WebSocket] 메시지 파싱 실패:", e);
                }
            });
        },
        onDisconnect: () => {
            console.log("[WebSocket] 연결 해제");
        },
    });

    stompClient.activate();
}

export function disconnectWebSocket() {
    stompClient?.deactivate();
    stompClient = null;
}

export function onAiTaskComplete(callback: (data: { taskId: string; status: string; result: string; type?: string }) => void) {
    const existing = listeners.get("ai-task") || [];
    existing.push(callback);
    listeners.set("ai-task", existing);

    // cleanup 함수 반환
    return () => {
        const cbs = listeners.get("ai-task") || [];
        listeners.set("ai-task", cbs.filter(cb => cb !== callback));
    };
}
