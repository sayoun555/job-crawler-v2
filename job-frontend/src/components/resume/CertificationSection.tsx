"use client";

import { useState } from "react";
import { Resume, ResumeCertification, resumeApi } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

type Props = {
    resume: Resume;
    token: string;
    onUpdate: () => void;
    resumeId?: number;
};

const emptyForm = (): Omit<ResumeCertification, "id"> => ({
    certName: "", issuingOrganization: "", acquiredDate: "",
});

export default function CertificationSection({ resume, token, onUpdate, resumeId }: Props) {
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState(emptyForm());
    const [saving, setSaving] = useState(false);

    const handleAdd = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.addSiteCertification(token, resumeId, form) : resumeApi.addCertification(token, form));
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
            await resumeApi.updateCertification(token, editingId, form);
            onUpdate();
            setForm(emptyForm());
            setEditingId(null);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deleteCertification(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    const startEdit = (cert: ResumeCertification) => {
        setEditingId(cert.id);
        setForm({
            certName: cert.certName || "",
            issuingOrganization: cert.issuingOrganization || "",
            acquiredDate: cert.acquiredDate || "",
        });
        setShowForm(false);
    };

    const cancelEdit = () => { setEditingId(null); setForm(emptyForm()); };

    const renderForm = (isEdit: boolean) => (
        <div className="space-y-3 p-4 border border-border rounded-lg bg-muted">
            <div className="grid grid-cols-3 gap-x-4 gap-y-3">
                <div>
                    <label className="text-sm font-medium block mb-1">자격증명</label>
                    <Input value={form.certName || ""} onChange={e => setForm({ ...form, certName: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">발급기관</label>
                    <Input value={form.issuingOrganization || ""} onChange={e => setForm({ ...form, issuingOrganization: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">취득일</label>
                    <Input type="date" className="w-full" value={form.acquiredDate || ""} onChange={e => setForm({ ...form, acquiredDate: e.target.value })} />
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
                    <CardTitle>자격증</CardTitle>
                    {!showForm && editingId === null && (
                        <Button variant="outline" size="sm" onClick={() => setShowForm(true)}>추가</Button>
                    )}
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                {resume.certifications.length === 0 && !showForm && (
                    <p className="text-sm text-muted-foreground">등록된 자격증이 없습니다.</p>
                )}
                {resume.certifications.map(cert => (
                    editingId === cert.id ? (
                        <div key={cert.id}>{renderForm(true)}</div>
                    ) : (
                        <div key={cert.id} className="flex items-center justify-between p-3 border border-border rounded-lg bg-muted/50">
                            <div className="text-sm">
                                <p className="font-medium text-foreground">{cert.certName || "-"}</p>
                                <p className="text-muted-foreground">{cert.issuingOrganization || "-"} | {cert.acquiredDate || "-"}</p>
                            </div>
                            <div className="flex gap-1">
                                <Button variant="outline" size="sm" onClick={() => startEdit(cert)}>수정</Button>
                                <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-700" onClick={() => handleDelete(cert.id)}>삭제</Button>
                            </div>
                        </div>
                    )
                ))}
                {showForm && renderForm(false)}
            </CardContent>
        </Card>
    );
}
