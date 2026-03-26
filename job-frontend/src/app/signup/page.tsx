"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";

export default function SignupPage() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [nickname, setNickname] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const router = useRouter();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError("");
        try {
            await authApi.signup({ email, password, nickname });
            alert("회원가입이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
            router.push("/login");
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "회원가입 실패");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex justify-center items-center min-h-[60vh]">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <CardTitle className="text-2xl bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                        회원가입
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <Input type="email" placeholder="이메일" value={email}
                            onChange={(e) => setEmail(e.target.value)} required />
                        <Input type="password" placeholder="비밀번호 (8자 이상)" value={password}
                            onChange={(e) => setPassword(e.target.value)} required minLength={8} />
                        <Input placeholder="닉네임" value={nickname}
                            onChange={(e) => setNickname(e.target.value)} required />
                        {error && <p className="text-sm text-red-500">{error}</p>}
                        <Button type="submit" className="w-full bg-emerald-600 hover:bg-emerald-700"
                            disabled={loading}>
                            {loading ? "가입 중..." : "회원가입"}
                        </Button>
                        <p className="text-center text-sm text-muted-foreground">
                            이미 계정이 있으신가요?{" "}
                            <Link href="/login" className="text-emerald-500 hover:underline">
                                로그인
                            </Link>
                        </p>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
