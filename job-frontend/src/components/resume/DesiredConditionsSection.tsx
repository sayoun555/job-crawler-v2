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

export default function DesiredConditionsSection({ resume, token, onUpdate, resumeId }: Props) {
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        desiredSalary: resume.desiredSalary || "",
        desiredEmploymentType: resume.desiredEmploymentType || "",
        desiredLocation: resume.desiredLocation || "",
        militaryStatus: resume.militaryStatus || "",
        disabilityStatus: resume.disabilityStatus || "",
        veteranStatus: resume.veteranStatus || "",
    });

    const handleSave = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.updateSiteDesiredConditions(token, resumeId, form) : resumeApi.updateDesiredConditions(token, form));
            onUpdate();
            setEditing(false);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleCancel = () => {
        setForm({
            desiredSalary: resume.desiredSalary || "",
            desiredEmploymentType: resume.desiredEmploymentType || "",
            desiredLocation: resume.desiredLocation || "",
            militaryStatus: resume.militaryStatus || "",
            disabilityStatus: resume.disabilityStatus || "",
            veteranStatus: resume.veteranStatus || "",
        });
        setEditing(false);
    };

    if (!editing) {
        return (
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>희망 조건 및 기타</CardTitle>
                        <Button variant="outline" size="sm" onClick={() => setEditing(true)}>수정</Button>
                    </div>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                            <span className="text-muted-foreground">희망연봉</span>
                            <p className="font-medium">{resume.desiredSalary || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">희망고용형태</span>
                            <p className="font-medium">{resume.desiredEmploymentType || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">희망근무지</span>
                            <p className="font-medium">{resume.desiredLocation || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">병역</span>
                            <p className="font-medium">{resume.militaryStatus || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">장애</span>
                            <p className="font-medium">{resume.disabilityStatus || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">보훈</span>
                            <p className="font-medium">{resume.veteranStatus || "-"}</p>
                        </div>
                    </div>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>희망 조건 및 기타</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                    <div>
                        <label className="text-sm font-medium block mb-1">희망연봉</label>
                        <Input value={form.desiredSalary} onChange={e => setForm({ ...form, desiredSalary: e.target.value })} placeholder="3,000만원" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">희망고용형태</label>
                        <Input value={form.desiredEmploymentType} onChange={e => setForm({ ...form, desiredEmploymentType: e.target.value })} placeholder="정규직" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">희망근무지</label>
                        <Input value={form.desiredLocation} onChange={e => setForm({ ...form, desiredLocation: e.target.value })} placeholder="서울" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">병역</label>
                        <Input value={form.militaryStatus} onChange={e => setForm({ ...form, militaryStatus: e.target.value })} placeholder="군필" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">장애</label>
                        <Input value={form.disabilityStatus} onChange={e => setForm({ ...form, disabilityStatus: e.target.value })} placeholder="해당없음" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">보훈</label>
                        <Input value={form.veteranStatus} onChange={e => setForm({ ...form, veteranStatus: e.target.value })} placeholder="해당없음" />
                    </div>
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
