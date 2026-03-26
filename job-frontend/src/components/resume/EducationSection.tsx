"use client";

import { useState } from "react";
import { Resume, ResumeEducation, resumeApi } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

type Props = {
    resume: Resume;
    token: string;
    onUpdate: () => void;
    resumeId?: number;
};

const SCHOOL_TYPE_LABEL: Record<string, string> = {
    HIGH_SCHOOL: "고등학교",
    COLLEGE_2Y: "대학교(2,3년)",
    COLLEGE_4Y: "대학교(4년)",
    GRADUATE_MASTER: "대학원(석사)",
    GRADUATE_DOCTOR: "대학원(박사)",
};

const GRADUATION_STATUS_LABEL: Record<string, string> = {
    ENROLLED: "재학",
    LEAVE_OF_ABSENCE: "휴학",
    GRADUATED: "졸업",
    COMPLETED: "수료",
    DROPPED: "중퇴",
    EXPECTED: "졸업예정",
};

const emptyForm = (): Omit<ResumeEducation, "id"> => ({
    schoolType: "", schoolName: "", major: "", subMajor: "",
    startDate: "", endDate: "", graduationStatus: "", gpa: "", gpaScale: "",
});

export default function EducationSection({ resume, token, onUpdate, resumeId }: Props) {
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState(emptyForm());
    const [saving, setSaving] = useState(false);

    const handleAdd = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.addSiteEducation(token, resumeId, form) : resumeApi.addEducation(token, form));
            onUpdate();
            setForm(emptyForm());
            setShowForm(false);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleUpdate = async () => {
        if (editingId === null) return;
        setSaving(true);
        try {
            await resumeApi.updateEducation(token, editingId, form);
            onUpdate();
            setForm(emptyForm());
            setEditingId(null);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deleteEducation(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    const startEdit = (edu: ResumeEducation) => {
        setEditingId(edu.id);
        setForm({
            schoolType: edu.schoolType || "", schoolName: edu.schoolName || "",
            major: edu.major || "", subMajor: edu.subMajor || "",
            startDate: edu.startDate || "", endDate: edu.endDate || "",
            graduationStatus: edu.graduationStatus || "",
            gpa: edu.gpa || "", gpaScale: edu.gpaScale || "",
        });
        setShowForm(false);
    };

    const cancelEdit = () => {
        setEditingId(null);
        setForm(emptyForm());
    };

    const renderForm = (isEdit: boolean) => (
        <div className="space-y-3 p-4 border border-border rounded-lg bg-muted">
            <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                <div>
                    <label className="text-sm font-medium block mb-1">학교구분</label>
                    <Select value={form.schoolType} onValueChange={(v: string | null) => setForm({ ...form, schoolType: v ?? "" })}>
                        <SelectTrigger className="w-full"><SelectValue placeholder="선택" /></SelectTrigger>
                        <SelectContent>
                            {Object.entries(SCHOOL_TYPE_LABEL).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">학교명</label>
                    <Input value={form.schoolName || ""} onChange={e => setForm({ ...form, schoolName: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">전공</label>
                    <Input value={form.major || ""} onChange={e => setForm({ ...form, major: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">복수전공</label>
                    <Input value={form.subMajor || ""} onChange={e => setForm({ ...form, subMajor: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">입학일</label>
                    <Input type="date" value={form.startDate || ""} onChange={e => setForm({ ...form, startDate: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">졸업일</label>
                    <Input type="date" value={form.endDate || ""} onChange={e => setForm({ ...form, endDate: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">졸업상태</label>
                    <Select value={form.graduationStatus} onValueChange={(v: string | null) => setForm({ ...form, graduationStatus: v ?? "" })}>
                        <SelectTrigger className="w-full"><SelectValue placeholder="선택" /></SelectTrigger>
                        <SelectContent>
                            {Object.entries(GRADUATION_STATUS_LABEL).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
                <div className="grid grid-cols-2 gap-2">
                    <div>
                        <label className="text-sm font-medium block mb-1">학점</label>
                        <Input value={form.gpa || ""} onChange={e => setForm({ ...form, gpa: e.target.value })} placeholder="3.5" />
                    </div>
                    <div>
                        <label className="text-sm font-medium block mb-1">만점</label>
                        <Input value={form.gpaScale || ""} onChange={e => setForm({ ...form, gpaScale: e.target.value })} placeholder="4.5" />
                    </div>
                </div>
            </div>
            <div className="flex gap-2 justify-end">
                <Button variant="outline" size="sm" onClick={isEdit ? cancelEdit : () => { setShowForm(false); setForm(emptyForm()); }}>
                    취소
                </Button>
                <Button size="sm" onClick={isEdit ? handleUpdate : handleAdd} disabled={saving} className="bg-blue-600 hover:bg-blue-700">
                    {saving ? "저장 중..." : isEdit ? "수정" : "추가"}
                </Button>
            </div>
        </div>
    );

    return (
        <Card>
            <CardHeader>
                <div className="flex items-center justify-between">
                    <CardTitle>학력</CardTitle>
                    {!showForm && editingId === null && (
                        <Button variant="outline" size="sm" onClick={() => setShowForm(true)}>추가</Button>
                    )}
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                {resume.educations.length === 0 && !showForm && (
                    <p className="text-sm text-muted-foreground">등록된 학력이 없습니다.</p>
                )}
                {resume.educations.map(edu => (
                    editingId === edu.id ? (
                        <div key={edu.id}>{renderForm(true)}</div>
                    ) : (
                        <div key={edu.id} className="flex items-center justify-between p-3 border border-border rounded-lg bg-muted/50">
                            <div className="text-sm">
                                <p className="font-medium text-foreground">{edu.schoolName || ""} <span className="text-muted-foreground">({(edu.schoolType && SCHOOL_TYPE_LABEL[edu.schoolType]) || edu.schoolType || "-"})</span></p>
                                <p className="text-muted-foreground">
                                    {edu.major || "-"} | {edu.startDate || "?"} ~ {edu.endDate || "?"} | {(edu.graduationStatus && GRADUATION_STATUS_LABEL[edu.graduationStatus]) || edu.graduationStatus || "-"}
                                    {edu.gpa && ` | ${edu.gpa}${edu.gpaScale ? `/${edu.gpaScale}` : ""}`}
                                </p>
                            </div>
                            <div className="flex gap-1">
                                <Button variant="outline" size="sm" onClick={() => startEdit(edu)}>수정</Button>
                                <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-700" onClick={() => handleDelete(edu.id)}>삭제</Button>
                            </div>
                        </div>
                    )
                ))}
                {showForm && renderForm(false)}
            </CardContent>
        </Card>
    );
}
