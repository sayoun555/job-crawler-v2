"use client";

import { useState } from "react";
import { Resume, resumeApi } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

type Props = {
    resume: Resume;
    token: string;
    onUpdate: () => void;
    resumeId?: number;
};

export default function SkillSection({ resume, token, onUpdate, resumeId }: Props) {
    const [input, setInput] = useState("");

    const handleAdd = async () => {
        const skillName = input.trim();
        if (!skillName) return;
        try {
            await (resumeId ? resumeApi.addSiteSkill(token, resumeId, skillName) : resumeApi.addSkill(token, skillName));
            onUpdate();
            setInput("");
        } catch (e) { console.error(e); }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "Enter") {
            e.preventDefault();
            handleAdd();
        }
    };

    const handleDelete = async (id: number) => {
        try {
            await resumeApi.deleteSkill(token, id);
            onUpdate();
        } catch (e) { console.error(e); }
    };

    return (
        <Card>
            <CardHeader>
                <CardTitle>스킬</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
                <div className="flex gap-2">
                    <Input
                        value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="스킬명 입력 후 Enter"
                        className="flex-1"
                    />
                </div>
                <div className="flex flex-wrap gap-2">
                    {resume.skills.length === 0 && (
                        <p className="text-sm text-muted-foreground">등록된 스킬이 없습니다.</p>
                    )}
                    {resume.skills.map(skill => (
                        <Badge key={skill.id} className="bg-blue-100 text-blue-800 hover:bg-blue-200 cursor-default flex items-center gap-1 px-3 py-1">
                            {skill.skillName}
                            <button
                                onClick={() => handleDelete(skill.id)}
                                className="ml-1 text-blue-500 hover:text-red-500 font-bold"
                            >
                                x
                            </button>
                        </Badge>
                    ))}
                </div>
            </CardContent>
        </Card>
    );
}
