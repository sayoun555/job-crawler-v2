import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const jobListTrend = new Trend('job_list_duration');
const jobDetailTrend = new Trend('job_detail_duration');
const statsTrend = new Trend('stats_duration');

const BASE_URL = 'http://localhost:8080/api/v1';

// 테스트 시나리오: 점진적 부하 증가
export const options = {
    stages: [
        { duration: '10s', target: 10 },   // 10초간 10명까지 증가
        { duration: '20s', target: 50 },   // 20초간 50명까지 증가
        { duration: '20s', target: 100 },  // 20초간 100명까지 증가
        { duration: '20s', target: 200 },  // 20초간 200명까지 증가
        { duration: '10s', target: 0 },    // 10초간 종료
    ],
    thresholds: {
        http_req_duration: ['p(95)<2000'],  // 95% 요청이 2초 이내
        errors: ['rate<0.1'],               // 에러율 10% 미만
    },
};

export default function () {
    // 1. 채용 공고 목록 조회 (가장 빈번한 요청)
    const listRes = http.get(`${BASE_URL}/jobs?page=0&size=20&sort=createdAt,DESC`);
    jobListTrend.add(listRes.timings.duration);
    check(listRes, {
        'job list 200': (r) => r.status === 200,
    }) || errorRate.add(1);

    sleep(0.5);

    // 2. 공고 통계 조회 (캐시 테스트)
    const statsRes = http.get(`${BASE_URL}/jobs/stats`);
    statsTrend.add(statsRes.timings.duration);
    check(statsRes, {
        'stats 200': (r) => r.status === 200,
    }) || errorRate.add(1);

    sleep(0.5);

    // 3. 공고 상세 조회 (랜덤 ID)
    const jobId = Math.floor(Math.random() * 100) + 3700;
    const detailRes = http.get(`${BASE_URL}/jobs/${jobId}`);
    jobDetailTrend.add(detailRes.timings.duration);
    check(detailRes, {
        'job detail 200 or 404': (r) => r.status === 200 || r.status === 404,
    }) || errorRate.add(1);

    sleep(0.5);
}
