"use client";

import ReactMarkdown from "react-markdown";

export function Markdown({ children, className = "" }: { children: string; className?: string }) {
    return (
        <div className={`prose prose-sm dark:prose-invert max-w-none
            prose-headings:text-base prose-headings:font-semibold prose-headings:mt-4 prose-headings:mb-2
            prose-p:my-1.5 prose-p:leading-relaxed
            prose-ul:my-1.5 prose-li:my-0.5
            prose-strong:text-foreground
            prose-code:text-xs prose-code:bg-muted prose-code:px-1 prose-code:py-0.5 prose-code:rounded
            ${className}`}>
            <ReactMarkdown>{children}</ReactMarkdown>
        </div>
    );
}
