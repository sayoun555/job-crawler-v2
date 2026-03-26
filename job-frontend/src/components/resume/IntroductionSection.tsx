"use client";

import { useState } from "react";
import { Resume, resumeApi } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

type Props = {
    resume: Resume;
    token: string;
    onUpdate: () => void;
    resumeId?: number;
};

export default function IntroductionSection({ resume, token, onUpdate, resumeId }: Props) {
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [introduction, setIntroduction] = useState(resume.introduction || "");
    const [selfIntroduction, setSelfIntroduction] = useState(resume.selfIntroduction || "");

    const handleSave = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.updateSiteIntroduction(token, resumeId, introduction, selfIntroduction) : resumeApi.updateIntroduction(token, introduction, selfIntroduction));
            onUpdate();
            setEditing(false);
        } catch (e) {
            console.error(e);
        } finally {
            setSaving(false);
        }
    };

    const handleCancel = () => {
        setIntroduction(resume.introduction || "");
        setSelfIntroduction(resume.selfIntroduction || "");
        setEditing(false);
    };

    if (!editing) {
        return (
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>소개</CardTitle>
                        <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                            수정
                        </Button>
                    </div>
                </CardHeader>
                <CardContent className="space-y-3">
                    <div>
                        <span className="text-sm text-muted-foreground">한 줄 소개</span>
                        <p className="font-medium text-sm">{resume.introduction || "-"}</p>
                    </div>
                    <div>
                        <span className="text-sm text-muted-foreground">자기소개서</span>
                        <p className="text-sm whitespace-pre-wrap">{resume.selfIntroduction || "-"}</p>
                    </div>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>소개</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                <div>
                    <label className="text-sm font-medium block mb-1">한 줄 소개</label>
                    <Input value={introduction} onChange={e => setIntroduction(e.target.value)} maxLength={100} placeholder="한 줄로 자신을 소개해주세요 (최대 100자)" />
                    <p className="text-xs text-muted-foreground mt-1">{introduction.length}/100</p>
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">자기소개서</label>
                    <textarea
                        className="w-full px-3 py-2 border border-border rounded-lg bg-background text-foreground focus:ring-2 focus:ring-blue-500 focus:border-transparent min-h-[200px] text-sm"
                        value={selfIntroduction}
                        onChange={e => setSelfIntroduction(e.target.value)}
                        placeholder="자기소개서를 작성해주세요"
                    />
                </div>
                <div className="flex gap-2 justify-end">
                    <Button variant="outline" onClick={handleCancel}>취소</Button>
                    <Button onClick={handleSave} disabled={saving} className="bg-blue-600 hover:bg-blue-700">
                        {saving ? "저장 중..." : "저장"}
                    </Button>
                </div>
            </CardContent>
        </Card>
    );
}
