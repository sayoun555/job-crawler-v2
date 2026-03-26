const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

type FetchOptions = {
    method?: string;
    body?: unknown;
    token?: string;
};

export async function api<T>(endpoint: string, options: FetchOptions = {}): Promise<T> {
    const { method = "GET", body, token } = options;

    const headers: Record<string, string> = {
        "Content-Type": "application/json",
    };

    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    const res = await fetch(`${API_BASE}${endpoint}`, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
    });

    // 401: 액세스 토큰 만료 → 리프레시 시도
    if (res.status === 401 && token) {
        const refreshToken = localStorage.getItem("refreshToken");
        if (refreshToken) {
            try {
                const refreshRes = await fetch(`${API_BASE}/auth/refresh`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ refreshToken }),
                });
                if (refreshRes.ok) {
                    const refreshJson = await refreshRes.json();
                    if (refreshJson.success) {
                        const newData = refreshJson.data;
                        localStorage.setItem("token", newData.accessToken);
                        localStorage.setItem("refreshToken", newData.refreshToken);
                        localStorage.setItem("tokenExpiry", String(Date.now() + newData.expiresIn * 1000));
                        // 원래 요청 재시도
                        const retryHeaders: Record<string, string> = { "Content-Type": "application/json", "Authorization": `Bearer ${newData.accessToken}` };
                        const retryRes = await fetch(`${API_BASE}${endpoint}`, { method, headers: retryHeaders, body: body ? JSON.stringify(body) : undefined });
                        const retryJson = await retryRes.json();
                        if (retryJson.success) return retryJson.data;
                    }
                }
            } catch { /* 리프레시 실패 */ }
        }
        // 리프레시도 실패 → 로그아웃
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("tokenExpiry");
        window.location.href = "/login";
        throw new Error("인증이 만료되었습니다. 다시 로그인해주세요.");
    }

    // 403: 권한 없음
    if (res.status === 403 && token) {
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("tokenExpiry");
        window.location.href = "/login";
        throw new Error("권한이 없습니다. 다시 로그인해주세요.");
    }

    const json = await res.json();

    if (!json.success) {
        console.error(`[API Error] ${method} ${endpoint} -> ${json.message}`);
        throw new Error(json.message || "API 요청 실패");
    }

    return json.data;
}

// === Auth ===
export const authApi = {
    signup: (data: { email: string; password: string; nickname: string }) =>
        api<{ accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }>("/auth/signup", { method: "POST", body: data }),
    login: (data: { email: string; password: string }) =>
        api<{ accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }>("/auth/login", { method: "POST", body: data }),
    refresh: (refreshToken: string) =>
        api<{ accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }>("/auth/refresh", { method: "POST", body: { refreshToken } }),
};

// === Jobs ===
export const jobsApi = {
    list: (params: Record<string, string | number | undefined>) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([k, v]) => {
            if (v !== undefined && v !== "") qs.set(k, String(v));
        });
        if (!qs.has("page")) qs.set("page", "0");
        if (!qs.has("size")) qs.set("size", "20");
        return api<PageResponse<JobPosting>>(`/jobs?${qs}`);
    },
    get: (id: number) => api<JobPosting>(`/jobs/${id}`),
    stats: () => api<{ saramin: number; jobplanet: number; linkareer: number; jobkorea: number; total: number }>("/jobs/stats"),
};

// === User ===
export const userApi = {
    me: (token: string) => api<User>("/users/me", { token }),
    profile: (token: string) => api<UserProfile>("/profile", { token }),
    updateProfile: (token: string, data: Partial<UserProfile>) =>
        api<UserProfile>("/profile", { method: "PUT", body: data, token }),
    updateWebhook: (token: string, webhookUrl: string) =>
        api<void>("/settings/discord-webhook", { method: "PUT", body: { webhookUrl }, token }),
    toggleNotification: (token: string, enabled: boolean) =>
        api<void>("/settings/notification", { method: "PATCH", body: { enabled }, token }),
    updateNotificationHours: (token: string, hours: string) =>
        api<void>("/settings/notification-hours", { method: "PUT", body: { hours }, token }),
};

export const adminApi = {
    listUsers: (token: string) => api<User[]>("/admin/users", { token }),
    approveUser: (token: string, userId: number) =>
        api<void>(`/admin/users/${userId}/approve`, { method: "PATCH", token }),
    suspendUser: (token: string, userId: number) =>
        api<void>(`/admin/users/${userId}/suspend`, { method: "PATCH", token }),
};

// === Projects ===
export const projectsApi = {
    list: (token: string) => api<Project[]>("/projects", { token }),
    get: (token: string, id: number) => api<Project>(`/projects/${id}`, { token }),
    create: (token: string, data: ProjectInput) =>
        api<Project>("/projects", { method: "POST", body: data, token }),
    update: (token: string, id: number, data: ProjectInput) =>
        api<Project>(`/projects/${id}`, { method: "PUT", body: data, token }),
    delete: (token: string, id: number) =>
        api<void>(`/projects/${id}`, { method: "DELETE", token }),
    getPortfolio: (token: string, id: number) =>
        api<{ projectId: number; projectName: string; content: string }>(`/projects/${id}/portfolio`, { token }),
    updatePortfolio: (token: string, id: number, content: string) =>
        api<{ content: string }>(`/projects/${id}/portfolio`, { method: "PUT", body: { content }, token }),
    generateDiagramPrompt: (token: string, id: number) =>
        api<{ architectureDiagramPrompt?: string; featureDiagramPrompt?: string; raw?: string }>(`/projects/${id}/diagram-prompt`, { method: "POST", token }),
    uploadPortfolioImage: async (token: string, projectId: number, file: File) => {
        const formData = new FormData();
        formData.append("file", file);
        const res = await fetch(`${API_BASE}/projects/${projectId}/portfolio/images`, {
            method: "POST",
            headers: { Authorization: `Bearer ${token}` },
            body: formData,
        });
        const json = await res.json();
        if (!json.success) throw new Error(json.message);
        return json.data as { imageUrl: string };
    },
    uploadImage: async (token: string, projectId: number, file: File) => {
        const formData = new FormData();
        formData.append("file", file);
        const res = await fetch(`${API_BASE}/projects/${projectId}/images`, {
            method: "POST",
            headers: { Authorization: `Bearer ${token}` },
            body: formData,
        });
        const json = await res.json();
        if (!json.success) throw new Error(json.message);
        return json.data as { imageUrl: string };
    },
};

// === Templates ===
export const templatesApi = {
    list: (token: string) => api<Template[]>("/templates", { token }),
    create: (token: string, data: { name: string; type: string; content: string }) =>
        api<Template>("/templates", { method: "POST", body: data, token }),
    update: (token: string, id: number, data: { name: string; content: string }) =>
        api<Template>(`/templates/${id}`, { method: "PUT", body: data, token }),
    delete: (token: string, id: number) =>
        api<void>(`/templates/${id}`, { method: "DELETE", token }),
    setDefault: (token: string, id: number) =>
        api<void>(`/templates/${id}/default`, { method: "PATCH", token }),
    getPresets: () => api<Template[]>("/templates/presets", {}),
    refreshPresets: (token: string) =>
        api<{ updatedCount: number }>("/templates/presets/refresh", { method: "POST", token }),
};

// === AI ===
export const aiApi = {
    matchScore: (token: string, jobId: number, force?: boolean) =>
        api<{ score: number; reason?: string }>(`/ai/match-score/${jobId}${force ? "?force=true" : ""}`, { method: "POST", token }),
    matchProjects: (token: string, jobId: number) =>
        api<Project[]>(`/ai/match-projects/${jobId}`, { token }),
    coverLetter: (token: string, jobId: number, templateId?: number) =>
        api<{ coverLetter: string }>(`/ai/cover-letter/${jobId}${templateId ? `?templateId=${templateId}` : ""}`, { method: "POST", token }),
    portfolio: (token: string, jobId: number, templateId?: number) =>
        api<{ portfolio: string }>(`/ai/portfolio/${jobId}${templateId ? `?templateId=${templateId}` : ""}`, { method: "POST", token }),
    companyAnalysis: (token: string, jobId: number) =>
        api<{ analysis: string }>(`/ai/company-analysis/${jobId}`, { token }),
    getSavedResults: (token: string, jobId: number) =>
        api<{ matchScore?: number; matchScoreReason?: string; companyAnalysis?: string }>(`/ai/results/${jobId}`, { token }),
    summarizeProject: (token: string, projectId: number) =>
        api<{ summary: string }>(`/ai/summarize-project/${projectId}`, { method: "POST", token }),
    analyzeCoverLetter: (token: string, coverLetterId: number) =>
        api<{ structure?: string[]; pattern?: string; keywords?: string[]; strengths?: string[]; template?: string; raw?: string }>(`/ai/cover-letter-analysis/${coverLetterId}`, { method: "POST", token }),
    // 비동기 AI 태스크 (WebSocket + 폴링)
    asyncCoverLetter: (token: string, jobId: number, templateId?: number, projectIds?: string) => {
        const params = new URLSearchParams();
        if (templateId) params.set("templateId", String(templateId));
        if (projectIds) params.set("projectIds", projectIds);
        const qs = params.toString();
        return api<{ taskId: string }>(`/ai/async/cover-letter/${jobId}${qs ? `?${qs}` : ""}`, { method: "POST", token });
    },
    asyncPortfolio: (token: string, jobId: number, templateId?: number, projectIds?: string) => {
        const params = new URLSearchParams();
        if (templateId) params.set("templateId", String(templateId));
        if (projectIds) params.set("projectIds", projectIds);
        const qs = params.toString();
        return api<{ taskId: string }>(`/ai/async/portfolio/${jobId}${qs ? `?${qs}` : ""}`, { method: "POST", token });
    },
    asyncProjectPortfolio: (token: string, projectId: number) =>
        api<{ taskId: string }>(`/ai/async/portfolio/project/${projectId}`, { method: "POST", token }),
    asyncStatus: (token: string, taskId: string) =>
        api<{ status: string; type?: string; userId?: string; result?: string; taskId: string }>(`/ai/async/status/${taskId}`, { token }),
    batchMatchScores: (token: string, jobIds: number[]) =>
        api<Record<number, number>>(`/ai/match-scores/batch`, { method: "POST", body: jobIds, token }),
};

// === Applications ===
export const applicationsApi = {
    prepare: (token: string, jobId: number, templateId?: number) =>
        api<JobApplication>(`/applications/prepare/${jobId}${templateId ? `?templateId=${templateId}` : ""}`, { method: "POST", token }),
    submit: (token: string, id: number) =>
        api<JobApplication>(`/applications/${id}/submit`, { method: "POST", token }),
    manualApply: (token: string, id: number) =>
        api<JobApplication>(`/applications/${id}/manual-apply`, { method: "PATCH", token }),
    retry: (token: string, id: number) =>
        api<JobApplication>(`/applications/${id}/retry`, { method: "POST", token }),
    list: (token: string, page?: number) =>
        api<PageResponse<JobApplication>>(`/applications?page=${page || 0}&size=20`, { token }),
    get: (token: string, id: number) =>
        api<JobApplication>(`/applications/${id}`, { token }),
    updateDocs: (token: string, id: number, coverLetter: string, portfolio: string) =>
        api<JobApplication>(`/applications/${id}/documents`, { method: "PUT", body: { coverLetter, portfolio }, token }),
    updateMatchedProjects: (token: string, id: number, projectIds: string) =>
        api<JobApplication>(`/applications/${id}/matched-projects`, { method: "PATCH", body: { projectIds }, token }),
    regenerate: (token: string, id: number, projectIds: string, templateId?: number) =>
        api<JobApplication>(`/applications/${id}/regenerate?projectIds=${projectIds}${templateId ? `&templateId=${templateId}` : ""}`, { method: "POST", token }),
    prepareCustom: (token: string, jobId: number, sections: { title: string; rule: string }[], additionalRequest: string,
        portfolioSections?: { title: string; rule: string }[], portfolioAdditionalRequest?: string) =>
        api<JobApplication>(`/applications/prepare-custom/${jobId}`, {
            method: "POST",
            body: {
                sections, additionalRequest,
                ...(portfolioSections && portfolioSections.length > 0
                    ? { portfolioSections, portfolioAdditionalRequest: portfolioAdditionalRequest || "" }
                    : {}),
            },
            token,
        }),
};

// === Crawler ===
export const crawlerApi = {
    cancel: (token: string) =>
        api<{ cancelled: boolean }>("/crawler/cancel", { method: "POST", token }),
    crawlAll: (token: string, keyword?: string, maxPages?: number) =>
        api<{ keyword: string; savedCount: number }>(`/crawler/crawl${keyword ? `?keyword=${keyword}` : ""}${keyword && maxPages ? `&maxPages=${maxPages}` : maxPages ? `?maxPages=${maxPages}` : ""}`, { method: "POST", token }),
    crawlBySite: (token: string, site: string, keyword?: string, maxPages?: number) =>
        api<{ keyword: string; savedCount: number }>(`/crawler/crawl/${site}${keyword ? `?keyword=${keyword}` : ""}${keyword && maxPages ? `&maxPages=${maxPages}` : maxPages ? `?maxPages=${maxPages}` : ""}`, { method: "POST", token }),
    crawlBySites: (token: string, sites: string[], keyword?: string, maxPages?: number) =>
        api<{ sites: string[]; keyword: string; savedCount: number }>("/crawler/crawl/sites", { method: "POST", body: { sites, keyword, maxPages }, token }),
    getSchedule: (token: string) =>
        api<{ schedule1: string; schedule2: string; maxPages: number; enabled: boolean }>("/crawler/schedule", { token }),
    updateSchedule: (token: string, schedule1: string, schedule2: string, maxPages: number) =>
        api<{ schedule1: string; schedule2: string; maxPages: number; enabled: boolean }>("/crawler/schedule", { method: "PUT", body: { schedule1, schedule2, maxPages }, token }),
    toggleSchedule: (token: string) =>
        api<{ schedule1: string; schedule2: string; maxPages: number; enabled: boolean }>("/crawler/schedule/toggle", { method: "PATCH", token }),
    deleteJob: (token: string, id: number) =>
        api<void>(`/crawler/jobs/${id}`, { method: "DELETE", token }),
    deleteJobs: (token: string, ids: number[]) =>
        api<void>("/crawler/jobs", { method: "DELETE", body: { ids }, token }),
    deleteAllJobs: (token: string) =>
        api<void>("/crawler/jobs/all", { method: "DELETE", token }),
    deleteJobsBySite: (token: string, site: string) =>
        api<void>(`/crawler/jobs/site/${site}`, { method: "DELETE", token }),
    validateUrls: (token: string) =>
        api<{ closedCount: number }>("/crawler/validate-urls", { method: "POST", token }),
    getClosedJobs: (token: string, page = 0, size = 20) =>
        api<{ content: JobPosting[]; totalElements: number; totalPages: number; currentPage: number }>(
            `/crawler/jobs/closed?page=${page}&size=${size}`, { token }),
};

// === Job Preferences ===
export const preferenceApi = {
    list: (token: string, site?: string) =>
        api<JobPreference[]>(site ? `/preferences?site=${site}` : "/preferences", { token }),
    add: (token: string, site: string, categoryCode: string, categoryName: string) =>
        api<JobPreference>("/preferences", { method: "POST", body: { site, categoryCode, categoryName }, token }),
    remove: (token: string, id: number) =>
        api<void>(`/preferences/${id}`, { method: "DELETE", token }),
    toggle: (token: string, id: number, enabled: boolean) =>
        api<void>(`/preferences/${id}/toggle`, { method: "PATCH", body: { enabled }, token }),
    disableAll: (token: string, site: string) =>
        api<void>(`/preferences/disable-all?site=${site}`, { method: "POST", token }),
};

// === External Accounts ===
export const accountApi = {
    list: (token: string) => api<ExternalAccount[]>("/accounts", { token }),
    create: (token: string, data: { site: string; accountId: string; password: string }) =>
        api<ExternalAccount>("/accounts", { method: "POST", body: data, token }),
    delete: (token: string, id: number) =>
        api<void>(`/accounts/${id}`, { method: "DELETE", token }),
    verify: (token: string, id: number) =>
        api<{ valid: boolean }>(`/accounts/${id}/verify`, { method: "POST", token }),
    loginPopup: (token: string, site: string) =>
        api<{ success: boolean; message: string }>("/accounts/login-popup", { method: "POST", body: { site }, token }),
    onetimeLogin: (token: string, site: string, loginId: string, password: string) =>
        api<{ success: boolean; message: string }>("/accounts/onetime-login", { method: "POST", body: { site, loginId, password }, token }),
};

// === Resume ===
export const resumeApi = {
    get: (token: string) => api<Resume>("/resume", { token }),
    updateBasicInfo: (token: string, data: Partial<Resume>) =>
        api<Resume>("/resume/basic-info", { method: "PUT", body: data, token }),
    updateIntroduction: (token: string, introduction: string, selfIntroduction: string) =>
        api<Resume>("/resume/introduction", { method: "PUT", body: { introduction, selfIntroduction }, token }),
    updateDesiredConditions: (token: string, data: Partial<Resume>) =>
        api<Resume>("/resume/desired-conditions", { method: "PUT", body: data, token }),
    // 학력
    addEducation: (token: string, data: Omit<ResumeEducation, "id">) =>
        api<ResumeEducation>("/resume/educations", { method: "POST", body: data, token }),
    updateEducation: (token: string, id: number, data: Omit<ResumeEducation, "id">) =>
        api<ResumeEducation>(`/resume/educations/${id}`, { method: "PUT", body: data, token }),
    deleteEducation: (token: string, id: number) =>
        api<void>(`/resume/educations/${id}`, { method: "DELETE", token }),
    // 경력
    addCareer: (token: string, data: Omit<ResumeCareer, "id">) =>
        api<ResumeCareer>("/resume/careers", { method: "POST", body: data, token }),
    updateCareer: (token: string, id: number, data: Omit<ResumeCareer, "id">) =>
        api<ResumeCareer>(`/resume/careers/${id}`, { method: "PUT", body: data, token }),
    deleteCareer: (token: string, id: number) =>
        api<void>(`/resume/careers/${id}`, { method: "DELETE", token }),
    // 스킬
    addSkill: (token: string, skillName: string) =>
        api<ResumeSkill>("/resume/skills", { method: "POST", body: { skillName }, token }),
    deleteSkill: (token: string, id: number) =>
        api<void>(`/resume/skills/${id}`, { method: "DELETE", token }),
    // 자격증
    addCertification: (token: string, data: Omit<ResumeCertification, "id">) =>
        api<ResumeCertification>("/resume/certifications", { method: "POST", body: data, token }),
    updateCertification: (token: string, id: number, data: Omit<ResumeCertification, "id">) =>
        api<ResumeCertification>(`/resume/certifications/${id}`, { method: "PUT", body: data, token }),
    deleteCertification: (token: string, id: number) =>
        api<void>(`/resume/certifications/${id}`, { method: "DELETE", token }),
    // 어학
    addLanguage: (token: string, data: Omit<ResumeLanguage, "id">) =>
        api<ResumeLanguage>("/resume/languages", { method: "POST", body: data, token }),
    updateLanguage: (token: string, id: number, data: Omit<ResumeLanguage, "id">) =>
        api<ResumeLanguage>(`/resume/languages/${id}`, { method: "PUT", body: data, token }),
    deleteLanguage: (token: string, id: number) =>
        api<void>(`/resume/languages/${id}`, { method: "DELETE", token }),
    // 활동
    addActivity: (token: string, data: Omit<ResumeActivity, "id">) =>
        api<ResumeActivity>("/resume/activities", { method: "POST", body: data, token }),
    updateActivity: (token: string, id: number, data: Omit<ResumeActivity, "id">) =>
        api<ResumeActivity>(`/resume/activities/${id}`, { method: "PUT", body: data, token }),
    deleteActivity: (token: string, id: number) =>
        api<void>(`/resume/activities/${id}`, { method: "DELETE", token }),
    // 포트폴리오 링크
    addPortfolioLink: (token: string, data: Omit<ResumePortfolioLink, "id">) =>
        api<ResumePortfolioLink>("/resume/portfolio-links", { method: "POST", body: data, token }),
    deletePortfolioLink: (token: string, id: number) =>
        api<void>(`/resume/portfolio-links/${id}`, { method: "DELETE", token }),
    // 사이트별 이력서 수정 (resumeId 지정)
    getById: (token: string, resumeId: number) =>
        api<Resume>(`/resume/${resumeId}`, { token }),
    updateSiteBasicInfo: (token: string, resumeId: number, data: Partial<Resume>) =>
        api<Resume>(`/resume/${resumeId}/basic-info`, { method: "PUT", body: data, token }),
    updateSiteIntroduction: (token: string, resumeId: number, introduction: string, selfIntroduction: string) =>
        api<Resume>(`/resume/${resumeId}/introduction`, { method: "PUT", body: { introduction, selfIntroduction }, token }),
    updateSiteDesiredConditions: (token: string, resumeId: number, data: Partial<Resume>) =>
        api<Resume>(`/resume/${resumeId}/desired-conditions`, { method: "PUT", body: data, token }),
    addSiteEducation: (token: string, resumeId: number, data: Omit<ResumeEducation, "id">) =>
        api<ResumeEducation>(`/resume/${resumeId}/educations`, { method: "POST", body: data, token }),
    addSiteCareer: (token: string, resumeId: number, data: Omit<ResumeCareer, "id">) =>
        api<ResumeCareer>(`/resume/${resumeId}/careers`, { method: "POST", body: data, token }),
    addSiteSkill: (token: string, resumeId: number, skillName: string) =>
        api<ResumeSkill>(`/resume/${resumeId}/skills`, { method: "POST", body: { skillName }, token }),
    addSiteCertification: (token: string, resumeId: number, data: Omit<ResumeCertification, "id">) =>
        api<ResumeCertification>(`/resume/${resumeId}/certifications`, { method: "POST", body: data, token }),
    addSiteLanguage: (token: string, resumeId: number, data: Omit<ResumeLanguage, "id">) =>
        api<ResumeLanguage>(`/resume/${resumeId}/languages`, { method: "POST", body: data, token }),
    addSiteActivity: (token: string, resumeId: number, data: Omit<ResumeActivity, "id">) =>
        api<ResumeActivity>(`/resume/${resumeId}/activities`, { method: "POST", body: data, token }),
    addSitePortfolioLink: (token: string, resumeId: number, data: Omit<ResumePortfolioLink, "id">) =>
        api<ResumePortfolioLink>(`/resume/${resumeId}/portfolio-links`, { method: "POST", body: data, token }),
    // 연동
    syncToSite: (token: string, site: string) =>
        api<ResumeSyncResult>(`/resume/sync/${site}`, { method: "POST", token }),
    syncToAll: (token: string) =>
        api<Record<string, ResumeSyncResult>>("/resume/sync/all", { method: "POST", token }),
    verify: (token: string, site: string) =>
        api<ResumeSyncResult>(`/resume/verify/${site}`, { method: "POST", token }),
    importFromSite: (token: string, site: string) =>
        api<{ success: boolean; sessionExpired: boolean; message: string; importedCount: number }>(`/resume/import/${site}`, { method: "POST", token }),
    getSiteResumes: (token: string, site: string) =>
        api<(Resume & { sourceSite?: string; resumeTitle?: string })[]>(`/resume/site/${site}`, { token }),
    getAllResumes: (token: string) =>
        api<(Resume & { sourceSite?: string })[]>(`/resume/all`, { token }),
};

// === Cover Letters ===
export const coverLettersApi = {
    list: (params: { keyword?: string; school?: string; page?: number; size?: number; sortBy?: string }) => {
        const qs = new URLSearchParams();
        if (params.keyword) qs.set("keyword", params.keyword);
        if (params.school) qs.set("school", params.school);
        qs.set("page", String(params.page || 0));
        qs.set("size", String(params.size || 20));
        // Spring Data format: sort=field,direction
        if (params.sortBy) qs.set("sort", params.sortBy);
        return api<PageResponse<CoverLetterItem>>(`/cover-letters?${qs}`);
    },
    get: (id: number) => api<CoverLetterItem>(`/cover-letters/${id}`),
    stats: () => api<{ total: number }>("/cover-letters/stats"),
    crawl: (token: string, maxPages?: number) =>
        api<{ savedCount: number }>(`/cover-letters/crawl?maxPages=${maxPages || 5}`, { method: "POST", token }),
    deleteAll: (token: string) =>
        api<void>("/cover-letters/all", { method: "DELETE", token }),
};

// === Test Checklist (Step 10) ===
export const testApi = {
    getChecklist: () => api<ChecklistItem[]>("/test-checklist"),
    toggleItem: (id: string, checked: boolean) =>
        api<Record<string, boolean>>(`/test-checklist/${id}`, { method: "PATCH", body: { checked } }),
    getState: () => api<Record<string, boolean>>("/test-checklist/state"),
};

// === Types ===
export type User = {
    id: number; email: string; nickname: string;
    discordWebhookUrl?: string; notificationEnabled: boolean;
    notificationHours?: string;
    role: "USER" | "ADMIN";
    status: "PENDING" | "ACTIVE" | "SUSPENDED";
};

export type UserProfile = {
    education?: string; career?: string; certifications?: string;
    techStack?: string; strengths?: string;
};

export type JobPosting = {
    id: number; title: string; company: string; companyLogoUrl?: string;
    location?: string; url: string; description?: string;
    source: "SARAMIN" | "JOBPLANET" | "LINKAREER" | "JOBKOREA";
    applicationMethod: "DIRECT_APPLY" | "HOMEPAGE" | "EMAIL" | "UNKNOWN";
    education?: string; career?: string; salary?: string;
    deadline?: string; techStack?: string;
    requirements?: string; companyImages?: string;
    closed: boolean; createdAt: string;
};

export type Project = {
    id: number; name: string; description?: string;
    githubUrl?: string; notionUrl?: string; techStack?: string;
    aiSummary?: string; aiPortfolioContent?: string; imageUrls: string[];
};
export type ProjectInput = Omit<Project, "id" | "imageUrls" | "aiPortfolioContent">;

export type Template = {
    id: number; name: string; type: "COVER_LETTER" | "PORTFOLIO";
    content: string; isDefault: boolean;
};

export type PortfolioEntry = {
    projectId: number; projectName: string; content: string;
};

export type JobApplication = {
    id: number; status: string; coverLetter?: string;
    portfolioContent?: string; matchedProjectIds?: string;
    coverLetterSections?: string; // JSON: [{"title":"..","rule":"..","content":".."}]
    appliedAt?: string; failReason?: string; createdAt: string;
    jobPosting: JobPosting;
};

export type ExternalAccount = {
    id: number; site: string; accountId?: string; loginId?: string;
    authType?: string; isValid?: boolean; sessionValid?: boolean;
    resumeSyncedAt?: string; resumeSyncStatus?: string; resumeSyncMessage?: string;
};

export type JobPreference = {
    id: number; site: string; categoryCode: string; categoryName: string; enabled: boolean;
};

export type ChecklistItem = {
    id: string; section: string; label: string; checked: boolean;
};

export type CoverLetterItem = {
    id: number; company: string; position?: string; period?: string;
    companyType?: string; careerType?: string; school?: string;
    major?: string; gpa?: string; specs?: string; content: string;
    scrapCount: number; sourceUrl: string; createdAt: string;
};

export type PageResponse<T> = {
    content: T[]; totalPages: number; totalElements: number;
    number: number; size: number;
};

// === Resume Types ===
export type Resume = {
    id: number;
    name?: string; phone?: string; email?: string; gender?: string;
    birthDate?: string; address?: string;
    introduction?: string; selfIntroduction?: string;
    desiredSalary?: string; desiredEmploymentType?: string; desiredLocation?: string;
    militaryStatus?: string; disabilityStatus?: string; veteranStatus?: string;
    educations: ResumeEducation[];
    careers: ResumeCareer[];
    skills: ResumeSkill[];
    certifications: ResumeCertification[];
    languages: ResumeLanguage[];
    activities: ResumeActivity[];
    portfolioLinks: ResumePortfolioLink[];
};

export type ResumeEducation = {
    id: number; schoolType?: string; schoolName?: string;
    major?: string; subMajor?: string;
    startDate?: string; endDate?: string;
    graduationStatus?: string; gpa?: string; gpaScale?: string;
};

export type ResumeCareer = {
    id: number; companyName?: string; department?: string;
    position?: string; rank?: string;
    startDate?: string; endDate?: string; currentlyWorking: boolean;
    jobDescription?: string; salary?: string;
};

export type ResumeSkill = { id: number; skillName: string; };

export type ResumeCertification = {
    id: number; certName?: string; issuingOrganization?: string; acquiredDate?: string;
};

export type ResumeLanguage = {
    id: number; languageName?: string; examName?: string;
    score?: string; grade?: string; examDate?: string;
};

export type ResumeActivity = {
    id: number; activityType?: string; activityName?: string;
    organization?: string; description?: string;
    startDate?: string; endDate?: string;
};

export type ResumePortfolioLink = {
    id: number; linkType?: string; url?: string; description?: string;
};

export type ResumeSyncResult = {
    status: "SUCCESS" | "PARTIAL_SUCCESS" | "FAILED";
    message?: string;
    sectionResults?: Record<string, { success: boolean; message?: string }>;
    sessionExpired?: boolean;
};

