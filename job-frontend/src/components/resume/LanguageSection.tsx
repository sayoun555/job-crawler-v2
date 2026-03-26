"use client";

import { useState } from "react";
import { Resume, ResumeLanguage, resumeApi } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

type Props = {
    resume: Resume;
    token: string;
    onUpdate: () => void;
    resumeId?: number;
};

const emptyForm = (): Omit<ResumeLanguage, "id"> => ({
    languageName: "", examName: "", score: "", grade: "", examDate: "",
});

export default function LanguageSection({ resume, token, onUpdate, resumeId }: Props) {
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState(emptyForm());
    const [saving, setSaving] = useState(false);

    const handleAdd = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.addSiteLanguage(token, resumeId, form) : resumeApi.addLanguage(token, form));
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
            await resumeApi.updateLanguage(token, editingId, form);
            onUpdate();
            setForm(emptyForm());
            setEditingId(null);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deleteLanguage(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    const startEdit = (lang: ResumeLanguage) => {
        setEditingId(lang.id);
        setForm({
            languageName: lang.languageName || "", examName: lang.examName || "",
            score: lang.score || "", grade: lang.grade || "", examDate: lang.examDate || "",
        });
        setShowForm(false);
    };

    const cancelEdit = () => { setEditingId(null); setForm(emptyForm()); };

    const renderForm = (isEdit: boolean) => (
        <div className="space-y-3 p-4 border border-border rounded-lg bg-muted">
            <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                <div>
                    <label className="text-sm font-medium block mb-1">언어</label>
                    <Input value={form.languageName || ""} onChange={e => setForm({ ...form, languageName: e.target.value })} placeholder="영어" />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">시험명</label>
                    <Input value={form.examName || ""} onChange={e => setForm({ ...form, examName: e.target.value })} placeholder="TOEIC" />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">점수</label>
                    <Input value={form.score || ""} onChange={e => setForm({ ...form, score: e.target.value })} placeholder="900" />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">등급</label>
                    <Input value={form.grade || ""} onChange={e => setForm({ ...form, grade: e.target.value })} placeholder="1급" />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">시험일</label>
                    <Input type="date" className="w-full" value={form.examDate || ""} onChange={e => setForm({ ...form, examDate: e.target.value })} />
                </div>
            </div>
            <div className="flex gap-2 justify-end">
                <Button variant="outline" size="sm" onClick={isEdit ? cancelEdit : () => { setShowForm(false); setForm(emptyForm()); }}>취소</Button>
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
                    <CardTitle>어학</CardTitle>
                    {!showForm && editingId === null && (
                        <Button variant="outline" size="sm" onClick={() => setShowForm(true)}>추가</Button>
                    )}
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                {resume.languages.length === 0 && !showForm && (
                    <p className="text-sm text-muted-foreground">등록된 어학 정보가 없습니다.</p>
                )}
                {resume.languages.map(lang => (
                    editingId === lang.id ? (
                        <div key={lang.id}>{renderForm(true)}</div>
                    ) : (
                        <div key={lang.id} className="flex items-center justify-between p-3 border border-border rounded-lg bg-muted/50">
                            <div className="text-sm">
                                <p className="font-medium text-foreground">{lang.languageName || "-"} - {lang.examName || "-"}</p>
                                <p className="text-muted-foreground">
                                    {lang.score && `점수: ${lang.score}`}
                                    {lang.grade && ` | 등급: ${lang.grade}`}
                                    {lang.examDate && ` | ${lang.examDate}`}
                                </p>
                            </div>
                            <div className="flex gap-1">
                                <Button variant="outline" size="sm" onClick={() => startEdit(lang)}>수정</Button>
                                <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-700" onClick={() => handleDelete(lang.id)}>삭제</Button>
                            </div>
                        </div>
                    )
                ))}
                {showForm && renderForm(false)}
            </CardContent>
        </Card>
    );
}
