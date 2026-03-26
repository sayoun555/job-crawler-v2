"use client";

import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { ResizableImage } from "./image-extension";
import Placeholder from "@tiptap/extension-placeholder";
import { useCallback, useRef, useEffect } from "react";
import {
    Bold, Italic, Strikethrough, Heading2, Heading3,
    List, ListOrdered, Quote, Minus, ImagePlus,
} from "lucide-react";

type RichEditorProps = {
    content: string;
    onChange: (html: string) => void;
    onImageUpload?: (file: File) => Promise<string>;
    placeholder?: string;
    editable?: boolean;
};

function MenuBar({ editor, onImageUpload }: {
    editor: ReturnType<typeof useEditor> | null;
    onImageUpload?: (file: File) => Promise<string>;
}) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleImageUpload = useCallback(async () => {
        fileInputRef.current?.click();
    }, []);

    const handleFileChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file || !editor || !onImageUpload) return;

        try {
            const url = await onImageUpload(file);
            editor.chain().focus().setImage({ src: url }).run();
        } catch (err) {
            alert("이미지 업로드 실패: " + (err instanceof Error ? err.message : String(err)));
        }

        if (fileInputRef.current) fileInputRef.current.value = "";
    }, [editor, onImageUpload]);

    if (!editor) return null;

    const btnClass = (active: boolean) =>
        `px-2 py-1 text-xs rounded transition-colors ${active ? "bg-emerald-600 text-white" : "bg-muted hover:bg-muted/80 text-foreground"}`;

    const Btn = ({ onClick, active, icon: Icon, label }: {
        onClick: () => void; active: boolean; icon: React.ElementType; label: string;
    }) => (
        <button type="button" onClick={onClick} title={label}
            className={btnClass(active)}>
            <Icon size={16} />
        </button>
    );

    return (
        <div className="flex flex-wrap items-center gap-1 p-2 border-b bg-muted/30">
            <Btn onClick={() => editor.chain().focus().toggleBold().run()}
                active={editor.isActive("bold")} icon={Bold} label="굵게" />
            <Btn onClick={() => editor.chain().focus().toggleItalic().run()}
                active={editor.isActive("italic")} icon={Italic} label="기울임" />
            <Btn onClick={() => editor.chain().focus().toggleStrike().run()}
                active={editor.isActive("strike")} icon={Strikethrough} label="취소선" />

            <span className="w-px h-5 bg-border mx-1" />

            <Btn onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
                active={editor.isActive("heading", { level: 2 })} icon={Heading2} label="제목 (대)" />
            <Btn onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
                active={editor.isActive("heading", { level: 3 })} icon={Heading3} label="제목 (소)" />

            <span className="w-px h-5 bg-border mx-1" />

            <Btn onClick={() => editor.chain().focus().toggleBulletList().run()}
                active={editor.isActive("bulletList")} icon={List} label="글머리 목록" />
            <Btn onClick={() => editor.chain().focus().toggleOrderedList().run()}
                active={editor.isActive("orderedList")} icon={ListOrdered} label="번호 목록" />

            <span className="w-px h-5 bg-border mx-1" />

            <Btn onClick={() => editor.chain().focus().toggleBlockquote().run()}
                active={editor.isActive("blockquote")} icon={Quote} label="인용" />
            <Btn onClick={() => editor.chain().focus().setHorizontalRule().run()}
                active={false} icon={Minus} label="구분선" />

            {onImageUpload && (
                <>
                    <span className="w-px h-5 bg-border mx-1" />
                    <Btn onClick={handleImageUpload} active={false} icon={ImagePlus} label="이미지 삽입" />
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={handleFileChange}
                    />
                </>
            )}
        </div>
    );
}

export default function RichEditor({ content, onChange, onImageUpload, placeholder, editable = true }: RichEditorProps) {
    const editor = useEditor({
        immediatelyRender: false,
        extensions: [
            StarterKit,
            ResizableImage.configure({ inline: false, allowBase64: false }),
            Placeholder.configure({ placeholder: placeholder || "내용을 입력하세요..." }),
        ],
        content,
        editable,
        onUpdate: ({ editor }) => {
            onChange(editor.getHTML());
        },
        editorProps: {
            attributes: {
                class: "prose prose-sm max-w-none p-4 min-h-[400px] focus:outline-none text-gray-900 [&_img]:max-w-full [&_img]:rounded-lg [&_img]:my-3 [&_h2]:text-xl [&_h2]:font-extrabold [&_h2]:text-gray-900 [&_h2]:border-b-2 [&_h2]:border-gray-300 [&_h2]:pb-2 [&_h2]:mb-4 [&_h2]:mt-8 [&_h3]:text-sm [&_h3]:font-semibold [&_h3]:text-emerald-700 [&_h3]:mt-4 [&_h3]:mb-1 [&_h3]:pl-2 [&_h3]:border-l-3 [&_h3]:border-emerald-500 [&_blockquote]:border-l-3 [&_blockquote]:border-emerald-400 [&_blockquote]:bg-emerald-50 [&_blockquote]:py-1 [&_blockquote]:px-3 [&_blockquote]:text-gray-700",
            },
            handleDrop: (view, event, _slice, moved) => {
                if (moved || !onImageUpload) return false;
                const files = event.dataTransfer?.files;
                if (!files?.length) return false;

                const file = files[0];
                if (!file.type.startsWith("image/")) return false;

                event.preventDefault();
                onImageUpload(file).then((url) => {
                    const { schema } = view.state;
                    const node = schema.nodes.image.create({ src: url });
                    const pos = view.posAtCoords({ left: event.clientX, top: event.clientY });
                    if (pos) {
                        const tr = view.state.tr.insert(pos.pos, node);
                        view.dispatch(tr);
                    }
                });
                return true;
            },
            handlePaste: (view, event) => {
                if (!onImageUpload) return false;
                const items = event.clipboardData?.items;
                if (!items) return false;

                for (const item of items) {
                    if (item.type.startsWith("image/")) {
                        event.preventDefault();
                        const file = item.getAsFile();
                        if (!file) return false;

                        onImageUpload(file).then((url) => {
                            const { schema } = view.state;
                            const node = schema.nodes.image.create({ src: url });
                            const tr = view.state.tr.replaceSelectionWith(node);
                            view.dispatch(tr);
                        });
                        return true;
                    }
                }
                return false;
            },
        },
    });

    // content가 외부에서 변경될 때 동기화
    useEffect(() => {
        if (editor && content !== editor.getHTML()) {
            editor.commands.setContent(content);
        }
    }, [content, editor]);

    return (
        <div className="border rounded-md overflow-hidden bg-white">
            <MenuBar editor={editor} onImageUpload={onImageUpload} />
            <EditorContent editor={editor} />
        </div>
    );
}
