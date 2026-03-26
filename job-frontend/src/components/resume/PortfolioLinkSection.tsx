"use client";

import { useState } from "react";
import { Resume, resumeApi, ResumePortfolioLink } from "@/lib/api";
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

const LINK_TYPES = [
    { value: "github", label: "GitHub" },
    { value: "notion", label: "Notion" },
    { value: "blog", label: "Blog" },
    { value: "기타", label: "기타" },
];

const emptyForm = (): Omit<ResumePortfolioLink, "id"> => ({
    linkType: "", url: "", description: "",
});

export default function PortfolioLinkSection({ resume, token, onUpdate, resumeId }: Props) {
    const [showForm, setShowForm] = useState(false);
    const [form, setForm] = useState(emptyForm());
    const [saving, setSaving] = useState(false);

    const handleAdd = async () => {
        setSaving(true);
        try {
            await (resumeId ? resumeApi.addSitePortfolioLink(token, resumeId, form) : resumeApi.addPortfolioLink(token, form));
            onUpdate();
            setForm(emptyForm());
            setShowForm(false);
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deletePortfolioLink(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    return (
        <Card>
            <CardHeader>
                <div className="flex items-center justify-between">
                    <CardTitle>포트폴리오 링크</CardTitle>
                    {!showForm && (
                        <Button variant="outline" size="sm" onClick={() => setShowForm(true)}>추가</Button>
                    )}
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                {resume.portfolioLinks.length === 0 && !showForm && (
                    <p className="text-sm text-muted-foreground">등록된 포트폴리오 링크가 없습니다.</p>
                )}
                {resume.portfolioLinks.map(link => (
                    <div key={link.id} className="flex items-center justify-between p-3 border border-border rounded-lg bg-muted/50">
                        <div className="text-sm">
                            <p className="font-medium text-foreground">
                                <span className="text-muted-foreground">[{link.linkType || "기타"}]</span>{" "}
                                <a href={link.url || "#"} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                                    {link.url || "-"}
                                </a>
                            </p>
                            {link.description && <p className="text-muted-foreground">{link.description}</p>}
                        </div>
                        <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-700" onClick={() => handleDelete(link.id)}>삭제</Button>
                    </div>
                ))}
                {showForm && (
                    <div className="space-y-3 p-4 border border-border rounded-lg bg-muted">
                        <div className="grid grid-cols-3 gap-x-4 gap-y-3">
                            <div>
                                <label className="text-sm font-medium block mb-1">유형</label>
                                <Select value={form.linkType} onValueChange={(v: string | null) => setForm({ ...form, linkType: v ?? "" })}>
                                    <SelectTrigger className="w-full"><SelectValue placeholder="선택" /></SelectTrigger>
                                    <SelectContent>
                                        {LINK_TYPES.map(t => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="col-span-2">
                                <label className="text-sm font-medium block mb-1">URL</label>
                                <Input value={form.url || ""} onChange={e => setForm({ ...form, url: e.target.value })} placeholder="https://..." />
                            </div>
                            <div className="col-span-3">
                                <label className="text-sm font-medium block mb-1">설명</label>
                                <Input value={form.description || ""} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="간단한 설명" />
                            </div>
                        </div>
                        <div className="flex gap-2 justify-end">
                            <Button variant="outline" size="sm" onClick={() => { setShowForm(false); setForm(emptyForm()); }}>취소</Button>
                            <Button size="sm" onClick={handleAdd} disabled={saving} className="bg-blue-600 hover:bg-blue-700">
                                {saving ? "저장 중..." : "추가"}
                            </Button>
                        </div>
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
