"use client";

import { NodeViewWrapper, type NodeViewProps } from "@tiptap/react";
import { useState, useCallback, useRef, useEffect } from "react";
import { AlignLeft, AlignCenter, AlignRight, Trash2, Maximize2, Minimize2 } from "lucide-react";

export default function ResizableImageView({ node, updateAttributes, deleteNode, selected }: NodeViewProps) {
    const { src, alt, title, width } = node.attrs;
    const [isResizing, setIsResizing] = useState(false);
    const [currentWidth, setCurrentWidth] = useState<number>(width || 0);
    const [align, setAlign] = useState<string>(node.attrs.dataAlign || "center");
    const imgRef = useRef<HTMLImageElement>(null);
    const startX = useRef(0);
    const startWidth = useRef(0);

    useEffect(() => {
        if (width) setCurrentWidth(width);
    }, [width]);

    const onMouseDown = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        setIsResizing(true);
        startX.current = e.clientX;
        startWidth.current = imgRef.current?.offsetWidth || currentWidth || 400;

        const onMouseMove = (ev: MouseEvent) => {
            const diff = ev.clientX - startX.current;
            const newWidth = Math.max(100, startWidth.current + diff);
            setCurrentWidth(newWidth);
        };

        const onMouseUp = (ev: MouseEvent) => {
            document.removeEventListener("mousemove", onMouseMove);
            document.removeEventListener("mouseup", onMouseUp);
            setIsResizing(false);
            const diff = ev.clientX - startX.current;
            const finalWidth = Math.max(100, startWidth.current + diff);
            updateAttributes({ width: finalWidth });
        };

        document.addEventListener("mousemove", onMouseMove);
        document.addEventListener("mouseup", onMouseUp);
    }, [currentWidth, updateAttributes]);

    const handleAlign = (newAlign: string) => {
        setAlign(newAlign);
        updateAttributes({ dataAlign: newAlign });
    };

    const handlePresetSize = (preset: "small" | "medium" | "full") => {
        const sizes = { small: 300, medium: 500, full: 0 };
        const w = sizes[preset];
        setCurrentWidth(w);
        updateAttributes({ width: w || null });
    };

    const justifyClass = align === "left" ? "justify-start" : align === "right" ? "justify-end" : "justify-center";

    return (
        <NodeViewWrapper className={`flex ${justifyClass} my-3`} data-drag-handle>
            <div className="relative group inline-block">
                <img
                    ref={imgRef}
                    src={src}
                    alt={alt || ""}
                    title={title || ""}
                    style={currentWidth ? { width: `${currentWidth}px` } : { maxWidth: "100%" }}
                    className={`rounded-lg transition-shadow ${selected ? "ring-2 ring-emerald-500 shadow-lg" : "hover:shadow-md"}`}
                    draggable={false}
                />

                {/* 리사이즈 핸들 (우하단) */}
                <div
                    onMouseDown={onMouseDown}
                    className={`absolute bottom-1 right-1 w-4 h-4 bg-emerald-500 rounded-sm cursor-se-resize opacity-0 group-hover:opacity-100 transition-opacity ${isResizing ? "opacity-100" : ""}`}
                    title="드래그하여 크기 조절"
                />

                {/* 이미지 툴바 (선택 시) */}
                {selected && (
                    <div className="absolute -top-10 left-1/2 -translate-x-1/2 flex items-center gap-1 bg-white border rounded-lg shadow-lg px-2 py-1 z-10">
                        <button type="button" onClick={() => handleAlign("left")} title="왼쪽 정렬"
                            className={`p-1 rounded ${align === "left" ? "bg-emerald-100 text-emerald-700" : "hover:bg-gray-100"}`}>
                            <AlignLeft size={14} />
                        </button>
                        <button type="button" onClick={() => handleAlign("center")} title="가운데 정렬"
                            className={`p-1 rounded ${align === "center" ? "bg-emerald-100 text-emerald-700" : "hover:bg-gray-100"}`}>
                            <AlignCenter size={14} />
                        </button>
                        <button type="button" onClick={() => handleAlign("right")} title="오른쪽 정렬"
                            className={`p-1 rounded ${align === "right" ? "bg-emerald-100 text-emerald-700" : "hover:bg-gray-100"}`}>
                            <AlignRight size={14} />
                        </button>

                        <span className="w-px h-4 bg-gray-200 mx-1" />

                        <button type="button" onClick={() => handlePresetSize("small")} title="작게 (300px)"
                            className="p-1 rounded hover:bg-gray-100">
                            <Minimize2 size={14} />
                        </button>
                        <button type="button" onClick={() => handlePresetSize("full")} title="원본 크기"
                            className="p-1 rounded hover:bg-gray-100">
                            <Maximize2 size={14} />
                        </button>

                        <span className="w-px h-4 bg-gray-200 mx-1" />

                        <button type="button" onClick={deleteNode} title="삭제"
                            className="p-1 rounded hover:bg-red-100 text-red-500">
                            <Trash2 size={14} />
                        </button>
                    </div>
                )}
            </div>
        </NodeViewWrapper>
    );
}
