"use client";

import { useState } from "react";
import { Resume, ResumeCareer, resumeApi } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

type Props = {
    resume: Resume;
    token: string;
    onUpdate: () => void;
    resumeId?: number;
};

const emptyForm = (): Omit<ResumeCareer, "id"> => ({
    companyName: "", department: "", position: "", rank: "",
    startDate: "", endDate: "", currentlyWorking: false,
    jobDescription: "", salary: "",
});

export default function CareerSection({ resume, token, onUpdate, resumeId }: Props) {
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState(emptyForm());
    const [saving, setSaving] = useState(false);

    const handleAdd = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.addSiteCareer(token, resumeId, form) : resumeApi.addCareer(token, form));
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
            await resumeApi.updateCareer(token, editingId, form);
            onUpdate();
            setForm(emptyForm());
            setEditingId(null);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deleteCareer(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    const startEdit = (career: ResumeCareer) => {
        setEditingId(career.id);
        setForm({
            companyName: career.companyName || "", department: career.department || "",
            position: career.position || "", rank: career.rank || "",
            startDate: career.startDate || "", endDate: career.endDate || "",
            currentlyWorking: career.currentlyWorking,
            jobDescription: career.jobDescription || "", salary: career.salary || "",
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
                    <label className="text-sm font-medium block mb-1">회사명</label>
                    <Input value={form.companyName || ""} onChange={e => setForm({ ...form, companyName: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">부서</label>
                    <Input value={form.department || ""} onChange={e => setForm({ ...form, department: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">직책</label>
                    <Input value={form.position || ""} onChange={e => setForm({ ...form, position: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">직급</label>
                    <Input value={form.rank || ""} onChange={e => setForm({ ...form, rank: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">입사일</label>
                    <Input type="date" className="w-full" value={form.startDate || ""} onChange={e => setForm({ ...form, startDate: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">퇴사일</label>
                    <Input type="date" className="w-full" value={form.endDate || ""} onChange={e => setForm({ ...form, endDate: e.target.value })} disabled={form.currentlyWorking} />
                </div>
                <div className="col-span-2">
                    <label className="flex items-center gap-2 cursor-pointer">
                        <input type="checkbox" checked={form.currentlyWorking} onChange={e => setForm({ ...form, currentlyWorking: e.target.checked, endDate: e.target.checked ? "" : form.endDate })} className="accent-blue-600" />
                        <span className="text-sm font-medium">재직중</span>
                    </label>
                </div>
                <div className="col-span-2">
                    <label className="text-sm font-medium block mb-1">담당업무</label>
                    <textarea
                        className="w-full px-3 py-2 border border-border rounded-lg bg-background text-foreground focus:ring-2 focus:ring-blue-500 focus:border-transparent min-h-[80px] text-sm"
                        value={form.jobDescription || ""}
                        onChange={e => setForm({ ...form, jobDescription: e.target.value })}
                        placeholder="담당했던 업무를 작성해주세요"
                    />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">연봉</label>
                    <Input value={form.salary || ""} onChange={e => setForm({ ...form, salary: e.target.value })} placeholder="3,000만원" />
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
                    <CardTitle>경력</CardTitle>
                    {!showForm && editingId === null && (
                        <Button variant="outline" size="sm" onClick={() => setShowForm(true)}>추가</Button>
                    )}
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                {resume.careers.length === 0 && !showForm && (
                    <p className="text-sm text-muted-foreground">등록된 경력이 없습니다.</p>
                )}
                {resume.careers.map(career => (
                    editingId === career.id ? (
                        <div key={career.id}>{renderForm(true)}</div>
                    ) : (
                        <div key={career.id} className="flex items-start justify-between p-3 border border-border rounded-lg bg-muted/50">
                            <div className="text-sm">
                                <p className="font-medium text-foreground">{career.companyName || ""}</p>
                                <p className="text-muted-foreground">
                                    {career.department || "-"} | {career.position || "-"} | {career.rank || "-"}
                                </p>
                                <p className="text-muted-foreground">
                                    {career.startDate || "?"} ~ {career.currentlyWorking ? "재직중" : (career.endDate || "?")}
                                    {career.salary && ` | ${career.salary}`}
                                </p>
                                {career.jobDescription && (
                                    <p className="text-muted-foreground mt-1 whitespace-pre-wrap">{career.jobDescription}</p>
                                )}
                            </div>
                            <div className="flex gap-1 shrink-0 ml-2">
                                <Button variant="outline" size="sm" onClick={() => startEdit(career)}>수정</Button>
                                <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-700" onClick={() => handleDelete(career.id)}>삭제</Button>
                            </div>
                        </div>
                    )
                ))}
                {showForm && renderForm(false)}
            </CardContent>
        </Card>
    );
}
