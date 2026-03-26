"use client";

import { useState } from "react";
import { Resume, ResumeActivity, resumeApi } from "@/lib/api";
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

const ACTIVITY_TYPE_LABEL: Record<string, string> = {
    INTERN: "인턴",
    EXTERNAL_ACTIVITY: "대외활동",
    EDUCATION: "교육이수",
    AWARD: "수상",
    OVERSEAS: "해외경험",
};

const emptyForm = (): Omit<ResumeActivity, "id"> => ({
    activityType: "", activityName: "", organization: "",
    description: "", startDate: "", endDate: "",
});

export default function ActivitySection({ resume, token, onUpdate, resumeId }: Props) {
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState(emptyForm());
    const [saving, setSaving] = useState(false);

    const handleAdd = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.addSiteActivity(token, resumeId, form) : resumeApi.addActivity(token, form));
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
            await resumeApi.updateActivity(token, editingId, form);
            onUpdate();
            setForm(emptyForm());
            setEditingId(null);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deleteActivity(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    const startEdit = (act: ResumeActivity) => {
        setEditingId(act.id);
        setForm({
            activityType: act.activityType || "", activityName: act.activityName || "",
            organization: act.organization || "", description: act.description || "",
            startDate: act.startDate || "", endDate: act.endDate || "",
        });
        setShowForm(false);
    };

    const cancelEdit = () => { setEditingId(null); setForm(emptyForm()); };

    const renderForm = (isEdit: boolean) => (
        <div className="space-y-3 p-4 border border-border rounded-lg bg-muted">
            <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                <div>
                    <label className="text-sm font-medium block mb-1">활동유형</label>
                    <Select value={form.activityType} onValueChange={(v: string | null) => setForm({ ...form, activityType: v ?? "" })}>
                        <SelectTrigger className="w-full"><SelectValue placeholder="선택" /></SelectTrigger>
                        <SelectContent>
                            {Object.entries(ACTIVITY_TYPE_LABEL).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">활동명</label>
                    <Input value={form.activityName || ""} onChange={e => setForm({ ...form, activityName: e.target.value })} />
                </div>
                <div>
                    <label className="text-sm font-medium block mb-1">기관명</label>
                    <Input value={form.organization || ""} onChange={e => setForm({ ...form, organization: e.target.value })} />
                </div>
                <div className="flex gap-2">
                    <div className="flex-1">
                        <label className="text-sm font-medium block mb-1">시작일</label>
                        <Input type="date" className="w-full" value={form.startDate || ""} onChange={e => setForm({ ...form, startDate: e.target.value })} />
                    </div>
                    <div className="flex-1">
                        <label className="text-sm font-medium block mb-1">종료일</label>
                        <Input type="date" className="w-full" value={form.endDate || ""} onChange={e => setForm({ ...form, endDate: e.target.value })} />
                    </div>
                </div>
                <div className="col-span-2">
                    <label className="text-sm font-medium block mb-1">설명</label>
                    <textarea
                        className="w-full px-3 py-2 border border-border rounded-lg bg-background text-foreground focus:ring-2 focus:ring-blue-500 focus:border-transparent min-h-[80px] text-sm"
                        value={form.description || ""}
                        onChange={e => setForm({ ...form, description: e.target.value })}
                        placeholder="활동 내용을 작성해주세요"
                    />
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
                    <CardTitle>활동/경험</CardTitle>
                    {!showForm && editingId === null && (
                        <Button variant="outline" size="sm" onClick={() => setShowForm(true)}>추가</Button>
                    )}
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                {resume.activities.length === 0 && !showForm && (
                    <p className="text-sm text-muted-foreground">등록된 활동이 없습니다.</p>
                )}
                {resume.activities.map(act => (
                    editingId === act.id ? (
                        <div key={act.id}>{renderForm(true)}</div>
                    ) : (
                        <div key={act.id} className="flex items-start justify-between p-3 border border-border rounded-lg bg-muted/50">
                            <div className="text-sm">
                                <p className="font-medium text-foreground">{act.activityName || "-"} <span className="text-muted-foreground">({(act.activityType && ACTIVITY_TYPE_LABEL[act.activityType]) || act.activityType || "-"})</span></p>
                                <p className="text-muted-foreground">{act.organization || "-"} | {act.startDate || "?"} ~ {act.endDate || "?"}</p>
                                {act.description && <p className="text-muted-foreground mt-1 whitespace-pre-wrap">{act.description}</p>}
                            </div>
                            <div className="flex gap-1 shrink-0 ml-2">
                                <Button variant="outline" size="sm" onClick={() => startEdit(act)}>수정</Button>
                                <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-700" onClick={() => handleDelete(act.id)}>삭제</Button>
                            </div>
                        </div>
                    )
                ))}
                {showForm && renderForm(false)}
            </CardContent>
        </Card>
    );
}
