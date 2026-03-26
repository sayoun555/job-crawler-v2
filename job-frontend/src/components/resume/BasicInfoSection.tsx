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

export default function BasicInfoSection({ resume, token, onUpdate, resumeId }: Props) {
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        name: resume.name || "",
        phone: resume.phone || "",
        email: resume.email || "",
        gender: resume.gender || "",
        birthDate: resume.birthDate || "",
        address: resume.address || "",
    });

    const handleSave = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.updateSiteBasicInfo(token, resumeId, form) : resumeApi.updateBasicInfo(token, form));
            onUpdate();
            setEditing(false);
        } catch (e) {
            console.error(e);
        } finally {
            setSaving(false);
        }
    };

    const handleCancel = () => {
        setForm({
            name: resume.name || "",
            phone: resume.phone || "",
            email: resume.email || "",
            gender: resume.gender || "",
            birthDate: resume.birthDate || "",
            address: resume.address || "",
        });
        setEditing(false);
    };

    if (!editing) {
        return (
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <CardTitle>기본 정보</CardTitle>
                        <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                            수정
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                            <span className="text-muted-foreground">이름</span>
                            <p className="font-medium">{resume.name || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">전화번호</span>
                            <p className="font-medium">{resume.phone || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">이메일</span>
                            <p className="font-medium">{resume.email || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">성별</span>
                            <p className="font-medium">{resume.gender || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">생년월일</span>
                            <p className="font-medium">{resume.birthDate || "-"}</p>
                        </div>
                        <div>
                            <span className="text-muted-foreground">주소</span>
                            <p className="font-medium">{resume.address || "-"}</p>
                        </div>
                    </div>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>기본 정보</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                    <div>
                        <label className="text-sm font-medium block mb-1">이름</label>
                        <Input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">전화번호</label>
                        <Input value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="010-1234-5678" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">이메일</label>
                        <Input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">성별</label>
                        <div className="flex gap-4 mt-2">
                            <label className="flex items-center gap-1.5 cursor-pointer">
                                <input type="radio" name="gender" value="남성" checked={form.gender === "남성"} onChange={e => setForm({ ...form, gender: e.target.value })} className="accent-blue-600" />
                                <span className="text-sm">남성</span>
                            </label>
                            <label className="flex items-center gap-1.5 cursor-pointer">
                                <input type="radio" name="gender" value="여성" checked={form.gender === "여성"} onChange={e => setForm({ ...form, gender: e.target.value })} className="accent-blue-600" />
                                <span className="text-sm">여성</span>
                            </label>
                        </div>
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">생년월일</label>
                        <Input type="date" className="w-full" value={form.birthDate} onChange={e => setForm({ ...form, birthDate: e.target.value })} />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">주소</label>
                        <Input value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} />
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
