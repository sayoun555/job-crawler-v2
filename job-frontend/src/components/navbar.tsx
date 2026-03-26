"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";

export default function Navbar() {
    const { isLoggedIn, isAdmin, logout } = useAuth();

    return (
        <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
            <div className="container mx-auto px-4 sm:px-6 lg:px-8 max-w-screen-2xl flex h-14 items-center justify-between">
                <Link href="/" className="flex items-center gap-2">
                    <span className="text-xl font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                        이끼잡
                    </span>
                </Link>

                <nav className="hidden md:flex items-center gap-6 text-sm">
                    <Link href="/" className="text-muted-foreground hover:text-foreground transition-colors">
                        채용 공고
                    </Link>
                    <Link href="/cover-letters" className="text-muted-foreground hover:text-foreground transition-colors">
                        합격 자소서
                    </Link>
                    {isLoggedIn && (
                        <>
                            <Link href="/projects" className="text-muted-foreground hover:text-foreground transition-colors">
                                프로젝트
                            </Link>
                            <Link href="/portfolio" className="text-muted-foreground hover:text-foreground transition-colors">
                                포트폴리오
                            </Link>
                            <Link href="/templates" className="text-muted-foreground hover:text-foreground transition-colors">
                                템플릿
                            </Link>
                            <Link href="/resume" className="text-muted-foreground hover:text-foreground transition-colors">
                                이력서
                            </Link>
                            <Link href="/applications" className="text-muted-foreground hover:text-foreground transition-colors">
                                지원 이력
                            </Link>
                            <Link href="/dashboard" className="text-muted-foreground hover:text-foreground transition-colors">
                                내 관리
                            </Link>
                            {isAdmin && (
                                <Link href="/admin" className="text-muted-foreground hover:text-foreground transition-colors">
                                    관리자
                                </Link>
                            )}
                        </>
                    )}
                </nav>

                <div className="flex items-center gap-2">
                    {isLoggedIn ? (
                        <DropdownMenu>
                            <DropdownMenuTrigger
                                render={
                                    <Button variant="ghost" className="relative h-8 w-8 rounded-full">
                                        <Avatar className="h-8 w-8">
                                            <AvatarFallback className="bg-emerald-100 text-emerald-700">
                                                {isAdmin ? "A" : "U"}
                                            </AvatarFallback>
                                        </Avatar>
                                    </Button>
                                }
                            />
                            <DropdownMenuContent align="end">
                                <DropdownMenuItem render={<Link href="/dashboard">내 관리</Link>} />
                                <DropdownMenuItem render={<Link href="/resume">이력서</Link>} />
                                <DropdownMenuItem render={<Link href="/profile">프로필 설정</Link>} />
                                <DropdownMenuItem render={<Link href="/settings">알림 설정</Link>} />
                                {isAdmin && (
                                    <>
                                        <DropdownMenuSeparator />
                                        <DropdownMenuItem render={<Link href="/admin">관리자</Link>} />
                                    </>
                                )}
                                <DropdownMenuSeparator />
                                <DropdownMenuItem onClick={logout} className="text-red-600">
                                    로그아웃
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    ) : (
                        <div className="flex gap-2">
                            <Button variant="ghost" nativeButton={false} render={<Link href="/login">로그인</Link>} />
                            <Button className="bg-emerald-600 hover:bg-emerald-700" nativeButton={false} render={<Link href="/signup">회원가입</Link>} />
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}
