"use client";

import { useEffect, useRef, useState } from "react";

interface MermaidDiagramProps {
    code: string;
    title: string;
}

export default function MermaidDiagram({ code, title }: MermaidDiagramProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const [error, setError] = useState<string | null>(null);
    const [showCode, setShowCode] = useState(false);

    useEffect(() => {
        if (!code || !containerRef.current) return;
        setError(null);

        const render = async () => {
            try {
                const mermaid = (await import("mermaid")).default;
                mermaid.initialize({
                    startOnLoad: false,
                    theme: "default",
                    securityLevel: "loose",
                });
                const id = `mermaid-${title.replace(/\s/g, "-")}-${Date.now()}`;
                const { svg } = await mermaid.render(id, code);
                if (containerRef.current) {
                    containerRef.current.innerHTML = svg;
                }
            } catch (e) {
                setError(e instanceof Error ? e.message : "Mermaid 렌더링 실패");
            }
        };

        render();
    }, [code, title]);

    const handleDownloadPng = async () => {
        if (!containerRef.current) return;
        const svgEl = containerRef.current.querySelector("svg");
        if (!svgEl) return;

        const svgData = new XMLSerializer().serializeToString(svgEl);
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        const img = new Image();
        img.onload = () => {
            canvas.width = img.width * 2;
            canvas.height = img.height * 2;
            ctx.scale(2, 2);
            ctx.fillStyle = "#ffffff";
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, 0, 0);
            const link = document.createElement("a");
            link.download = `${title}.png`;
            link.href = canvas.toDataURL("image/png");
            link.click();
        };
        img.src = "data:image/svg+xml;base64," + btoa(unescape(encodeURIComponent(svgData)));
    };

    if (!code) return null;

    return (
        <div className="space-y-2">
            <div className="flex items-center justify-between">
                <span className="text-sm font-medium">{title}</span>
                <div className="flex gap-1.5">
                    <button
                        className="text-xs px-2 py-1 rounded border hover:bg-muted transition-colors"
                        onClick={() => setShowCode(!showCode)}>
                        {showCode ? "다이어그램" : "코드 보기"}
                    </button>
                    <button
                        className="text-xs px-2 py-1 rounded border hover:bg-muted transition-colors"
                        onClick={() => { navigator.clipboard.writeText(code); }}>
                        코드 복사
                    </button>
                    {!error && !showCode && (
                        <button
                            className="text-xs px-2 py-1 rounded border hover:bg-muted transition-colors"
                            onClick={handleDownloadPng}>
                            PNG 저장
                        </button>
                    )}
                </div>
            </div>
            {showCode ? (
                <pre className="p-3 border rounded-md text-xs bg-muted/30 overflow-x-auto whitespace-pre-wrap">{code}</pre>
            ) : error ? (
                <div className="p-3 border border-red-200 rounded-md bg-red-50 dark:bg-red-950">
                    <p className="text-xs text-red-500 mb-2">렌더링 실패: {error}</p>
                    <pre className="text-xs text-muted-foreground whitespace-pre-wrap">{code}</pre>
                </div>
            ) : (
                <div ref={containerRef} className="p-3 border rounded-md bg-white dark:bg-gray-950 overflow-x-auto" />
            )}
        </div>
    );
}
