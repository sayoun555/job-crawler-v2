"use client";

import { JobPosting } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import Link from "next/link";

const sourceBadge: Record<string, { label: string; className: string }> = {
    SARAMIN: { label: "사람인", className: "bg-blue-600 hover:bg-blue-700" },
    JOBPLANET: { label: "잡플래닛", className: "bg-purple-600 hover:bg-purple-700" },
    LINKAREER: { label: "링커리어", className: "bg-green-600 hover:bg-green-700" },
    JOBKOREA: { label: "잡코리아", className: "bg-red-600 hover:bg-red-700" },
};

function matchScoreColor(score: number): string {
    if (score >= 70) return "bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300";
    if (score >= 40) return "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300";
    return "bg-red-100 text-red-600 dark:bg-red-900 dark:text-red-300";
}

const methodBadge: Record<string, string> = {
    DIRECT_APPLY: "즉시지원",
    HOMEPAGE: "홈페이지",
    EMAIL: "이메일",
    UNKNOWN: "-",
};

export function JobCard({ job, matchScore }: { job: JobPosting; matchScore?: number }) {
    const src = sourceBadge[job.source];
    return (
        <Link href={`/jobs/${job.id}`} target="_blank">
            <Card className="h-full hover:shadow-lg hover:border-emerald-500/50 transition-all duration-200 cursor-pointer group">
                <CardHeader className="pb-2">
                    <div className="flex justify-between items-start gap-2">
                        <CardTitle className="text-base font-semibold line-clamp-2 group-hover:text-emerald-500 transition-colors">
                            {job.title}
                        </CardTitle>
                        {matchScore != null && matchScore > 0 && (
                            <Badge variant="secondary" className={`shrink-0 ${matchScoreColor(matchScore)}`}>
                                {matchScore}%
                            </Badge>
                        )}
                    </div>
                    <p className="text-sm text-muted-foreground">{job.company}</p>
                </CardHeader>
                <CardContent className="space-y-2">
                    <div className="flex flex-wrap gap-1">
                        <Badge className={src.className}>{src.label}</Badge>
                        <Badge variant="outline">{methodBadge[job.applicationMethod]}</Badge>
                        {job.location && <Badge variant="outline">{job.location}</Badge>}
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {job.career && <span className="text-xs text-muted-foreground">{job.career}</span>}
                        {job.career && job.education && <span className="text-xs text-muted-foreground">·</span>}
                        {job.education && <span className="text-xs text-muted-foreground">{job.education}</span>}
                        {job.salary && <span className="text-xs text-muted-foreground">· {job.salary}</span>}
                    </div>
                    {job.techStack && (
                        <div className="flex flex-wrap gap-1">
                            {(() => {
                                const raw = typeof job.techStack === "string" ? job.techStack : (job.techStack as { value?: string })?.value;
                                if (!raw) return null;
                                return raw.split(",").slice(0, 5).map((t, i) => (
                                    <Badge key={i} variant="secondary" className="text-xs">{t.trim()}</Badge>
                                ));
                            })()}
                        </div>
                    )}
                    <div className="flex justify-between items-center pt-1">
                        {job.deadline && (
                            <span className="text-xs text-orange-500">마감: {job.deadline}</span>
                        )}
                    </div>
                </CardContent>
            </Card>
        </Link>
    );
}

export function JobListItem({ job, matchScore }: { job: JobPosting; matchScore?: number }) {
    const src = sourceBadge[job.source];
    return (
        <Link href={`/jobs/${job.id}`} target="_blank" className="block">
            <div className="flex items-center gap-4 px-4 py-3 hover:bg-muted/50 rounded-lg transition-colors border-b last:border-0">
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                        <span className="font-medium truncate">{job.title}</span>
                        {matchScore != null && matchScore > 0 && (
                            <Badge className={`shrink-0 text-xs ${matchScoreColor(matchScore)}`}>
                                {matchScore}%
                            </Badge>
                        )}
                    </div>
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <span>{job.company}</span>
                        {job.location && <><span>·</span><span>{job.location}</span></>}
                        {job.career && <><span>·</span><span>{job.career}</span></>}
                    </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                    <Badge className={`${src.className} text-xs`}>{src.label}</Badge>
                    <Badge variant="outline" className="text-xs">{methodBadge[job.applicationMethod]}</Badge>
                </div>
            </div>
        </Link>
    );
}
