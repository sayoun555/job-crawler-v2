"use client";

import { createContext, useContext, useEffect, useState, useCallback, useRef, ReactNode } from "react";
import { authApi } from "./api";
import { connectWebSocket, disconnectWebSocket } from "./websocket";

type AuthContextType = {
    token: string | null;
    role: "USER" | "ADMIN" | null;
    login: (token: string, expiresIn?: number, refreshToken?: string) => void;
    logout: () => void;
    isLoggedIn: boolean;
    isAdmin: boolean;
};

const AuthContext = createContext<AuthContextType>({
    token: null,
    role: null,
    login: () => { },
    logout: () => { },
    isLoggedIn: false,
    isAdmin: false,
});

function parseJwtRole(token: string): "USER" | "ADMIN" {
    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        return payload.role === "ADMIN" ? "ADMIN" : "USER";
    } catch {
        return "USER";
    }
}

export function AuthProvider({ children }: { children: ReactNode }) {
    const [token, setToken] = useState<string | null>(null);
    const [role, setRole] = useState<"USER" | "ADMIN" | null>(null);
    const refreshingRef = useRef(false);

    const logout = useCallback(() => {
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("tokenExpiry");
        disconnectWebSocket();
        setToken(null);
        setRole(null);
        window.location.href = "/";
    }, []);

    const refreshToken = useCallback(async (): Promise<boolean> => {
        if (refreshingRef.current) return false;
        refreshingRef.current = true;

        const saved = localStorage.getItem("refreshToken");
        if (!saved) {
            refreshingRef.current = false;
            return false;
        }

        try {
            const result = await authApi.refresh(saved);
            localStorage.setItem("token", result.accessToken);
            if (result.refreshToken) localStorage.setItem("refreshToken", result.refreshToken);
            const expiry = Date.now() + (result.expiresIn ? result.expiresIn * 1000 : 1800000);
            localStorage.setItem("tokenExpiry", String(expiry));
            setToken(result.accessToken);
            setRole(parseJwtRole(result.accessToken));
            return true;
        } catch {
            return false;
        } finally {
            refreshingRef.current = false;
        }
    }, []);

    const isTokenExpired = useCallback(() => {
        const expiry = localStorage.getItem("tokenExpiry");
        if (!expiry) return false;
        return Date.now() > parseInt(expiry);
    }, []);

    // 만료 임박 체크 (만료 2분 전)
    const isTokenExpiringSoon = useCallback(() => {
        const expiry = localStorage.getItem("tokenExpiry");
        if (!expiry) return false;
        return Date.now() > parseInt(expiry) - 120_000;
    }, []);

    useEffect(() => {
        const saved = localStorage.getItem("token");
        if (saved) {
            if (isTokenExpired()) {
                // 만료됐으면 refresh 시도
                refreshToken().then(ok => { if (!ok) logout(); });
            } else {
                setToken(saved);
                setRole(parseJwtRole(saved));
            }
        }
    }, [isTokenExpired, refreshToken, logout]);

    // WebSocket 연결 (로그인 시)
    useEffect(() => {
        if (!token) return;
        try {
            const payload = JSON.parse(atob(token.split(".")[1]));
            const userId = Number(payload.sub);
            if (userId) connectWebSocket(userId);
        } catch {}
        return () => disconnectWebSocket();
    }, [token]);

    useEffect(() => {
        if (!token) return;
        const interval = setInterval(async () => {
            if (isTokenExpiringSoon()) {
                const ok = await refreshToken();
                if (!ok) {
                    logout();
                    window.location.href = "/login";
                }
            }
        }, 60_000);
        return () => clearInterval(interval);
    }, [token, isTokenExpiringSoon, refreshToken, logout]);

    const login = (t: string, expiresIn?: number, rt?: string) => {
        localStorage.setItem("token", t);
        if (rt) localStorage.setItem("refreshToken", rt);
        const expiry = Date.now() + (expiresIn ? expiresIn * 1000 : 1800000);
        localStorage.setItem("tokenExpiry", String(expiry));
        setToken(t);
        setRole(parseJwtRole(t));
    };

    return (
        <AuthContext.Provider value={{ token, role, login, logout, isLoggedIn: !!token, isAdmin: role === "ADMIN" }}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => useContext(AuthContext);
