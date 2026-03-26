"use client";

import { useState, useEffect } from "react";
import { userApi, UserProfile } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function ProfilePage() {
    const [profile, setProfile] = useState<UserProfile>({});
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState("");
    const { token } = useAuth();

    useEffect(() => {
        if (!token) return;
        userApi.profile(token).then(setProfile).catch(console.error);
    }, [token]);

    const handleSave = async () => {
        if (!token) return;
        setSaving(true);
        try {
            await userApi.updateProfile(token, profile);
            setMessage("✅ 프로필 저장 완료");
            setTimeout(() => setMessage(""), 3000);
        } catch {
            setMessage("저장 실패");
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <h1 className="text-2xl font-bold">프로필 설정</h1>
            <p className="text-sm text-muted-foreground">
                프로필을 상세히 입력할수록 AI가 더 정확한 자소서/포트폴리오를 생성합니다.
            </p>

            <Card>
                <CardHeader><CardTitle>기본 정보</CardTitle></CardHeader>
                <CardContent className="space-y-4">
                    <div>
                        <label className="text-sm font-medium">학력</label>
                        <Input placeholder="예: OO대학교 컴퓨터공학과 졸업"
                            value={profile.education || ""}
                            onChange={e => setProfile({ ...profile, education: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium">경력</label>
                        <textarea className="w-full min-h-[80px] rounded-md border bg-transparent px-3 py-2 text-sm"
                            placeholder="경력 사항을 입력하세요"
                            value={profile.career || ""}
                            onChange={e => setProfile({ ...profile, career: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium">자격증</label>
                        <Input placeholder="예: 정보처리기사, SQLD"
                            value={profile.certifications || ""}
                            onChange={e => setProfile({ ...profile, certifications: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium">기술 스택</label>
                        <Input placeholder="예: Java, Spring Boot, React, MySQL"
                            value={profile.techStack || ""}
                            onChange={e => setProfile({ ...profile, techStack: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium">강점 / 자기 소개</label>
                        <textarea className="w-full min-h-[100px] rounded-md border bg-transparent px-3 py-2 text-sm"
                            placeholder="자신의 강점, 핵심 역량 등을 자유롭게 작성하세요"
                            value={profile.strengths || ""}
                            onChange={e => setProfile({ ...profile, strengths: e.target.value })} />
                    </div>

                    <Button onClick={handleSave} disabled={saving}
                        className="w-full bg-emerald-600 hover:bg-emerald-700">
                        {saving ? "저장 중..." : "프로필 저장"}
                    </Button>
                    {message && <p className="text-sm text-emerald-500 text-center">{message}</p>}
                </CardContent>
            </Card>
        </div>
    );
}
