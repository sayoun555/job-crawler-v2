"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";

export default function LoginPage() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const { login, isLoggedIn } = useAuth();
    const router = useRouter();

    useEffect(() => {
        if (isLoggedIn) router.push("/");
    }, [isLoggedIn, router]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        try {
            const res = await authApi.login({ email, password });
            login(res.accessToken, res.expiresIn, res.refreshToken);
            router.push("/");
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "로그인 실패");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex justify-center items-center min-h-[60vh]">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <CardTitle className="text-2xl bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                        로그인
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <Input type="email" placeholder="이메일" value={email}
                            onChange={(e) => setEmail(e.target.value)} required />
                        <Input type="password" placeholder="비밀번호" value={password}
                            onChange={(e) => setPassword(e.target.value)} required />
                        {error && <p className="text-sm text-red-500">{error}</p>}
                        <Button type="submit" className="w-full bg-emerald-600 hover:bg-emerald-700"
                            disabled={loading}>
                            {loading ? "로그인 중..." : "로그인"}
                        </Button>
                        <p className="text-center text-sm text-muted-foreground">
                            계정이 없으신가요?{" "}
                            <Link href="/signup" className="text-emerald-500 hover:underline">
                                회원가입
                            </Link>
                        </p>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
