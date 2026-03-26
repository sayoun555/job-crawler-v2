import Image from "@tiptap/extension-image";
import { ReactNodeViewRenderer } from "@tiptap/react";
import ResizableImageView from "./resizable-image";

export const ResizableImage = Image.extend({
    addAttributes() {
        return {
            ...this.parent?.(),
            width: {
                default: null,
                parseHTML: (element) => element.getAttribute("width") || element.style.width?.replace("px", "") || null,
                renderHTML: (attributes) => {
                    if (!attributes.width) return {};
                    return { width: attributes.width, style: `width: ${attributes.width}px` };
                },
            },
            dataAlign: {
                default: "center",
                parseHTML: (element) => element.getAttribute("data-align") || "center",
                renderHTML: (attributes) => {
                    return { "data-align": attributes.dataAlign };
                },
            },
        };
    },
    addNodeView() {
        return ReactNodeViewRenderer(ResizableImageView);
    },
});
