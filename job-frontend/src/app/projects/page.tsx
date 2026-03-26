"use client";

import { useState, useEffect } from "react";
import { projectsApi, Project } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import Link from "next/link";

export default function ProjectsPage() {
    const [projects, setProjects] = useState<Project[]>([]);
    const [loading, setLoading] = useState(true);
    const { token } = useAuth();

    const load = async () => {
        if (!token) return;
        try { setProjects(await projectsApi.list(token)); } catch { /* */ }
        finally { setLoading(false); }
    };

    useEffect(() => { load(); }, [token]);

    const handleDelete = async (id: number) => {
        if (!token || !confirm("삭제하시겠습니까?")) return;
        await projectsApi.delete(token, id);
        load();
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold">내 프로젝트</h1>
                <Link href="/projects/new">
                    <Button className="bg-emerald-600 hover:bg-emerald-700">프로젝트 추가</Button>
                </Link>
            </div>

            {loading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {Array.from({ length: 4 }).map((_, i) => (
                        <div key={i} className="border rounded-lg p-5 space-y-3 animate-pulse">
                            <div className="h-5 w-2/3 bg-muted/50 rounded" />
                            <div className="h-4 w-full bg-muted/50 rounded" />
                            <div className="h-4 w-4/5 bg-muted/50 rounded" />
                            <div className="flex gap-2 pt-2">
                                <div className="h-6 w-16 bg-muted/50 rounded-full" />
                                <div className="h-6 w-20 bg-muted/50 rounded-full" />
                            </div>
                        </div>
                    ))}
                </div>
            ) : projects.length === 0 ? (
                <p className="text-center text-muted-foreground py-12">
                    등록된 프로젝트가 없습니다. 프로젝트를 추가하면 AI가 자동으로 매칭합니다.
                </p>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {projects.map(p => (
                        <Card key={p.id} className="hover:shadow-md transition-shadow">
                            <CardHeader>
                                <CardTitle className="text-lg">{p.name}</CardTitle>
                                {p.description && (
                                    <pre className="text-sm text-muted-foreground whitespace-pre-wrap font-sans line-clamp-3">{p.description}</pre>
                                )}
                            </CardHeader>
                            <CardContent className="space-y-3">
                                {p.imageUrls && p.imageUrls.length > 0 && (
                                    <div className="flex gap-2 overflow-x-auto">
                                        {p.imageUrls.slice(0, 3).map((url, i) => (
                                            // eslint-disable-next-line @next/next/no-img-element
                                            <img key={i} src={`http://localhost:8080${url}`} alt="프로젝트 이미지"
                                                className="w-24 h-24 rounded object-cover shrink-0" />
                                        ))}
                                        {p.imageUrls.length > 3 && (
                                            <span className="flex items-center text-xs text-muted-foreground">+{p.imageUrls.length - 3}</span>
                                        )}
                                    </div>
                                )}
                                {p.techStack && (
                                    <div className="flex flex-wrap gap-1">
                                        {p.techStack.split(",").map((t, i) => (
                                            <Badge key={i} variant="secondary" className="text-xs">{t.trim()}</Badge>
                                        ))}
                                    </div>
                                )}
                                <div className="flex gap-2 text-sm">
                                    {p.githubUrl && <a href={p.githubUrl} target="_blank" className="text-emerald-500 hover:underline">GitHub</a>}
                                    {p.notionUrl && <a href={p.notionUrl} target="_blank" className="text-emerald-500 hover:underline">Notion</a>}
                                </div>
                                <div className="flex gap-2 pt-2">
                                    <Link href={`/projects/${p.id}`}>
                                        <Button variant="outline" size="sm">편집</Button>
                                    </Link>
                                    <Button variant="outline" size="sm" className="text-red-500 hover:text-red-600"
                                        onClick={() => handleDelete(p.id)}>삭제</Button>
                                </div>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}
