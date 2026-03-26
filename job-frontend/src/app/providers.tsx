"use client";

import { AuthProvider } from "@/lib/auth-context";
import Navbar from "@/components/navbar";
import GlobalAiNotification from "@/components/global-ai-notification";

export function Providers({ children }: { children: React.ReactNode }) {
    return (
        <AuthProvider>
            <div className="min-h-screen bg-background">
                <Navbar />
                <GlobalAiNotification />
                <main className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 max-w-screen-2xl">{children}</main>
            </div>
        </AuthProvider>
    );
}
