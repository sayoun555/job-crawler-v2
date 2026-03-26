"use client";

import { useState, useEffect } from "react";
import { templatesApi, Template } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
    Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog";
import {
    Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";

const PRESET_TEMPLATES = [
    {
        name: "삼성전자 (2025~2026)",
        company: "삼성 · 4문항 · 4,200자",
        content: `[1. 지원동기 및 포부] (700자 이내)
삼성전자를 지원한 이유와 입사 후 회사에서 이루고 싶은 꿈을 기술하십시오.
{{지원동기}}

[2. 성장과정] (1,500자 이내)
본인의 성장과정을 간략히 기술하되 현재의 자신에게 가장 큰 영향을 끼친 사건, 인물 등을 포함하여 기술하시기 바랍니다.
{{성장과정}}

[3. 사회이슈] (1,000자 이내)
최근 사회 이슈 중 중요하다고 생각되는 한 가지를 선택하고 이에 관한 자신의 견해를 기술해 주시기 바랍니다.
{{사회이슈}}

[4. 직무역량] (1,000자 이내)
지원 직무 관련 본인의 전문지식과 경험을 작성하고, 본인이 지원 직무에 적합한 사유를 삼성전자 제품과 서비스 사용 경험을 기반으로 기술하시기 바랍니다.
{{직무역량}}`
    },
    {
        name: "현대자동차 (2025)",
        company: "현대 · 2문항 · 2,000자",
        content: `[1. 지원동기 및 성장] (1,000자 이내)
해당 분야에 지원한 동기와 함께, 입사 후 현대자동차에서 이루고 싶은 '성장'에 대해 기술해 주십시오.
{{지원동기}}

[2. 직무역량] (1,000자 이내)
지원 분야 업무 수행에 있어 가장 중요한 역량이 무엇인지 선정하고, 그 이유를 설명하며, 해당 역량을 키우기 위해 어떤 노력을 해오셨는지 기술해 주십시오.
{{직무역량}}`
    },
    {
        name: "SK하이닉스 (2025~2026)",
        company: "SK · 3+1문항 · 2,800자",
        content: `[1. 직무 관련 경험] (1,000자 이내) [필수]
지원 분야 및 직무 역량과 관련된 프로젝트/공모전/논문/연구/학습/활동/경험 등을 작성해 주십시오.
(형식: [경험명] / [소속] / [역할])
{{직무경험}}

[2. 전문성 노력] (600자 이내) [필수]
지원하신 직무 분야의 전문성을 키우기 위해 꾸준히 노력한 경험에 대해 서술해 주십시오.
{{전문성}}

[3. 도전과 성취] (600자 이내) [필수]
도전적인 목표를 세우고 성취하기 위해 끈질기게 노력한 경험에 대해 서술해 주십시오.
{{도전성취}}

[4. 자기표현] (600자 이내) [선택]
지원자님은 어떤 사람인가요? 해시태그(#, 최대 2개)를 포함하여, 남들과는 다른 특별한 가치관, 개성, 강점 등을 자유롭게 표현해 주세요.
{{자기표현}}`
    },
    {
        name: "LG전자 (2024~2025)",
        company: "LG · 2문항 · 1,500자",
        content: `[1. 지원동기] (1,000자 이내)
LG전자에 대한 지원동기에 대하여 구체적으로 기술하여 주십시오.
{{지원동기}}

[2. 직무 향후 계획] (500자 이내)
본인이 지원한 직무관련 향후 계획에 대하여 기술하여 주십시오.
{{향후계획}}`
    },
    {
        name: "카카오 (2026 신입크루)",
        company: "카카오 · 2~3문항 · 각 1,000자",
        content: `[1. 문제의 본질] (1,000자 이내)
어떤 문제를 해결할 때, 'Why?'를 놓치지 않고 문제의 본질을 찾는 데 집중했던 경험이 있다면 적어주세요.
{{문제본질}}

[2. 끝까지 몰입] (1,000자 이내)
어떤 일을 하면서 '이 정도면 충분하다'는 생각 대신, 더 완성도를 높이고 싶어 끝까지 몰입했던 경험이 있나요? 그때 어떤 점이 가장 도전적이었고, 어떻게 극복하셨는지 적어주세요.
{{몰입경험}}

[3. 관심 분야] (1,000자 이내) [Tech 직군]
공고 내 '세부 업무'를 참고하여 Platform/Client/Server/Infra를 관심 순으로 나열하고, 각 분야에 관심을 가지게 된 계기 혹은 경험이 있다면 서술해 주세요.
{{관심분야}}`
    },
    {
        name: "포스코 (2025)",
        company: "포스코 · 2문항 · 1,200자",
        content: `[1. 지원동기 및 가치] (600자 이내)
본인이 회사를 선택할 때 가장 중시하는 가치는 무엇이며, 포스코가 그 가치에 부합하는 이유를 서술하여 주십시오.
{{지원가치}}

[2. 역량 및 성장 계획] (600자 이내)
희망하는 직무를 수행함에 있어서 요구되는 역량을 갖추기 위해 어떠한 학습 또는 도전적인 경험을 하였고, 입사 후 이를 어떻게 발전시켜 나갈 것인지 서술하여 주십시오.
{{역량성장}}`
    },
    {
        name: "한화에어로스페이스 (2026)",
        company: "한화 · 2문항 · 1,700자",
        content: `[1. 지원동기 및 커리어 계획] (1,000자 이내)
해당 직무에 지원한 이유와 앞으로 한화에서 키워 나갈 커리어 계획을 구체적으로 작성해 주세요.
{{지원동기}}

[2. 역량 발휘 경험] (700자 이내)
지원 직무 관련 본인의 역량을 발휘한 경험을 서술해 주세요.
{{역량경험}}`
    },
    {
        name: "CJ제일제당 (2025)",
        company: "CJ · 2문항 · 2,000자",
        content: `[1. 이루고 싶은 성장] (1,000자 이내)
CJ제일제당에 합류해 이루고 싶은 성장에 대해 알려주세요.
{{성장목표}}

[2. 직무 경험과 성장] (1,000자 이내)
직무와 관련해 본인이 쌓아온 경험과 그 속에서 가장 크게 성장했다고 느낀 순간을 작성해 주세요.
{{직무성장}}`
    },
    {
        name: "공기업 NCS 양식 (한전 등)",
        company: "공공 · 2~3문항 · 각 600자",
        content: `[1. 직무 관련 경험 및 활용] (600자 이내)
지원분야 수행업무와 관련된 경험 또는 경력을 기술하고, 현업에서의 활용방안에 대한 본인의 생각을 기술해 주십시오.
{{직무경험}}

[2. 인재상 관련 강점] (600자 이내)
해당 기관의 인재상 중 자신의 강점을 나타낼 수 있는 것을 선택하여 제시하고, 그와 관련한 사례나 경험을 통해 근거를 제시해 주십시오.
{{인재상강점}}`
    },
    {
        name: "일반 자소서 (자유 양식)",
        company: "공통 · 자유 분량",
        content: `[자기소개서]
지원 직무에 대한 관심과 역량을 자유롭게 작성하세요.
지원동기, 직무역량, 프로젝트 경험, 입사 후 포부를 포함하면 좋습니다.

{{content}}`
    },
];

export default function TemplatesPage() {
    const [templates, setTemplates] = useState<Template[]>([]);
    const [presets, setPresets] = useState<Template[]>([]);
    const [open, setOpen] = useState(false);
    const [form, setForm] = useState({ name: "", type: "COVER_LETTER", content: "" });
    const [message, setMessage] = useState("");
    const { token, isAdmin } = useAuth();

    // 수정 모드
    const [editId, setEditId] = useState<number | null>(null);
    const [editForm, setEditForm] = useState({ name: "", content: "" });

    const handleEdit = (t: Template) => {
        setEditId(t.id);
        setEditForm({ name: t.name, content: t.content });
    };

    const handleEditSave = async () => {
        if (!token || editId === null) return;
        try {
            await templatesApi.update(token, editId, editForm);
            setEditId(null);
            setMessage("템플릿이 수정되었습니다.");
            setTimeout(() => setMessage(""), 3000);
            load();
        } catch { setMessage("수정 실패"); }
    };

    const load = async () => {
        if (!token) return;
        try { setTemplates(await templatesApi.list(token)); } catch { /* */ }
    };

    const loadPresets = async () => {
        try {
            const dbPresets = await templatesApi.getPresets();
            if (dbPresets.length > 0) {
                setPresets(dbPresets);
            }
        } catch { /* DB 프리셋 없으면 하드코딩 폴백 사용 */ }
    };

    useEffect(() => { load(); loadPresets(); }, [token]);

    const handleCreate = async () => {
        if (!token) return;
        try {
            await templatesApi.create(token, form);
            setOpen(false);
            setForm({ name: "", type: "COVER_LETTER", content: "" });
            load();
        } catch { /* */ }
    };

    const handleUsePreset = async (preset: typeof PRESET_TEMPLATES[0]) => {
        if (!token) return;
        try {
            await templatesApi.create(token, {
                name: preset.name,
                type: "COVER_LETTER",
                content: preset.content,
            });
            setMessage(`"${preset.name}" 템플릿이 추가되었습니다.`);
            setTimeout(() => setMessage(""), 3000);
            load();
        } catch { /* */ }
    };

    const handleSetDefault = async (id: number) => {
        if (!token) return;
        await templatesApi.setDefault(token, id);
        load();
    };

    const handleDelete = async (id: number) => {
        if (!token || !confirm("삭제하시겠습니까?")) return;
        await templatesApi.delete(token, id);
        load();
    };

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold">템플릿 관리</h1>
                <Dialog open={open} onOpenChange={setOpen}>
                    <DialogTrigger render={<Button className="bg-emerald-600 hover:bg-emerald-700">직접 만들기</Button>} />
                    <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
                        <DialogHeader><DialogTitle>커스텀 템플릿 만들기</DialogTitle></DialogHeader>
                        <div className="space-y-3">
                            <Input placeholder="템플릿 이름" value={form.name}
                                onChange={e => setForm({ ...form, name: e.target.value })} />
                            <Select value={form.type} onValueChange={v => { if (v) setForm({ ...form, type: v }); }}>
                                <SelectTrigger><SelectValue /></SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="COVER_LETTER">자소서</SelectItem>
                                    <SelectItem value="PORTFOLIO">포트폴리오</SelectItem>
                                </SelectContent>
                            </Select>
                            <textarea
                                className="w-full min-h-[300px] rounded-md border bg-transparent px-3 py-2 text-sm"
                                placeholder={"문항별로 작성하세요. 예:\n[1. 지원동기] (500자)\n지원 동기를 작성하세요.\n{{지원동기}}\n\n[2. 직무역량] (500자)\n직무 역량을 작성하세요.\n{{직무역량}}"}
                                value={form.content}
                                onChange={e => setForm({ ...form, content: e.target.value })}
                            />
                            <p className="text-xs text-muted-foreground">
                                <code className="text-emerald-500">{"{{content}}"}</code> 또는 <code className="text-emerald-500">{"{{문항명}}"}</code> 플레이스홀더를 넣으면 AI가 해당 부분을 채웁니다.
                            </p>
                            <Button onClick={handleCreate} className="w-full bg-emerald-600 hover:bg-emerald-700">저장</Button>
                        </div>
                    </DialogContent>
                </Dialog>
            </div>

            {message && <p className="text-sm text-emerald-500">{message}</p>}

            {/* 프리셋 양식 */}
            <Card>
                <CardHeader>
                    <div className="flex justify-between items-center">
                        <CardTitle className="text-lg">대기업 자소서 양식</CardTitle>
                        {isAdmin && (
                            <Button variant="outline" size="sm"
                                onClick={async () => {
                                    if (!token) return;
                                    setMessage("AI가 최신 양식을 조사 중입니다... (1~2분 소요)");
                                    try {
                                        const res = await templatesApi.refreshPresets(token);
                                        setMessage(`${res.updatedCount}개 양식 갱신 완료!`);
                                        loadPresets();
                                    } catch { setMessage("갱신 실패"); }
                                    setTimeout(() => setMessage(""), 5000);
                                }}>
                                AI 양식 갱신
                            </Button>
                        )}
                    </div>
                </CardHeader>
                <CardContent className="space-y-3">
                    <p className="text-sm text-muted-foreground">
                        기업별 자소서 양식을 선택하면 내 템플릿에 추가됩니다. AI가 해당 양식에 맞춰 자소서를 생성합니다.
                    </p>
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
                        {(presets.length > 0 ? presets : PRESET_TEMPLATES.map((p, i) => ({ id: -i, ...p, type: "COVER_LETTER" as const, isDefault: false }))).map((preset) => (
                            <Button key={preset.id || preset.name} variant="outline" className="h-auto py-3 flex-col items-start text-left"
                                onClick={() => handleUsePreset({ name: preset.name, company: "", content: preset.content })}>
                                <span className="font-medium text-sm">{preset.name}</span>
                            </Button>
                        ))}
                    </div>
                </CardContent>
            </Card>

            {/* 내 템플릿 */}
            <div className="space-y-3">
                <h2 className="text-lg font-semibold">내 템플릿</h2>
                {templates.length === 0 ? (
                    <p className="text-center text-muted-foreground py-8">
                        템플릿이 없습니다. 위에서 양식을 선택하거나 직접 만들어보세요.
                    </p>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {templates.map(t => (
                            <Card key={t.id}>
                                <CardHeader>
                                    <div className="flex justify-between items-center">
                                        <CardTitle className="text-base">{t.name}</CardTitle>
                                        <div className="flex gap-1">
                                            <Badge variant="outline">
                                                {t.type === "COVER_LETTER" ? "자소서" : "포트폴리오"}
                                            </Badge>
                                            {t.isDefault && <Badge className="bg-emerald-600">기본</Badge>}
                                        </div>
                                    </div>
                                </CardHeader>
                                <CardContent className="space-y-2">
                                    {editId === t.id ? (
                                        <div className="space-y-2">
                                            <Input value={editForm.name}
                                                onChange={e => setEditForm({ ...editForm, name: e.target.value })}
                                                placeholder="템플릿 이름" />
                                            <textarea
                                                className="w-full min-h-[200px] rounded-md border bg-transparent px-3 py-2 text-sm"
                                                value={editForm.content}
                                                onChange={e => setEditForm({ ...editForm, content: e.target.value })}
                                            />
                                            <div className="flex gap-2">
                                                <Button size="sm" onClick={handleEditSave}
                                                    className="bg-emerald-600 hover:bg-emerald-700">저장</Button>
                                                <Button size="sm" variant="outline"
                                                    onClick={() => setEditId(null)}>취소</Button>
                                            </div>
                                        </div>
                                    ) : (
                                        <>
                                            <pre className="text-xs text-muted-foreground whitespace-pre-wrap line-clamp-6 bg-muted/50 p-2 rounded">
                                                {t.content}
                                            </pre>
                                            <div className="flex gap-2">
                                                <Button variant="outline" size="sm" onClick={() => handleEdit(t)}>
                                                    수정
                                                </Button>
                                                {!t.isDefault && (
                                                    <Button variant="outline" size="sm" onClick={() => handleSetDefault(t.id)}>
                                                        기본으로 설정
                                                    </Button>
                                                )}
                                                <Button variant="outline" size="sm" className="text-red-500"
                                                    onClick={() => handleDelete(t.id)}>삭제</Button>
                                            </div>
                                        </>
                                    )}
                                </CardContent>
                            </Card>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
