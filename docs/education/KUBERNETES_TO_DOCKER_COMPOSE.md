# Kubernetes에서 Docker Compose로 돌아온 이야기 — 컨테이너 오케스트레이션의 올바른 선택

## 배경

이끼잡 서비스를 로컬 환경에 배포하면서, "어차피 컨테이너화할 거 Kubernetes로 가자"는 판단을 내렸다.
서비스 4개(PostgreSQL, Redis, Spring Boot, Next.js), 서버 1대, 사용자 수백 명 규모.
결론부터 말하면 — **2주 만에 Docker Compose로 되돌아왔다.**

---

## 1단계: Kubernetes 도입 결정

### 당시 판단 근거

```
"클라우드 이전할 때 편하니까 처음부터 K8s로 가자"
"Helm Chart 만들어두면 어디서든 배포 가능하다"
"이력서에 Kubernetes 경험 쓸 수 있다"
```

이 판단이 왜 틀렸는지를 CS 관점에서 분석한다.

---

## 2단계: Kubernetes 구성 시도

### 필요했던 리소스 파일들

```
k8s/
├── namespace.yaml
├── postgres/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── pvc.yaml              # PersistentVolumeClaim
│   └── configmap.yaml
├── redis/
│   ├── deployment.yaml
│   └── service.yaml
├── backend/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   └── hpa.yaml              # HorizontalPodAutoscaler
├── frontend/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
└── ingress-controller/
    └── nginx-ingress.yaml
```

**서비스 4개에 YAML 파일 15개.** Docker Compose는 1개 파일이다.

### 각 리소스가 하는 일 (CS 개념)

```
[Kubernetes 리소스 계층]

Namespace (논리적 격리)
└─ Deployment (Pod 생성/관리 + ReplicaSet)
   └─ Pod (컨테이너 실행 단위)
      └─ Container (실제 프로세스)

Service (네트워크 추상화 — Pod IP는 매번 바뀌므로 고정 DNS 제공)
├─ ClusterIP: 클러스터 내부에서만 접근
├─ NodePort: 외부에서 노드 IP:Port로 접근
└─ LoadBalancer: 클라우드 LB 자동 생성

PersistentVolumeClaim (Pod가 죽어도 데이터 보존)
├─ PV (PersistentVolume): 실제 디스크
└─ PVC: "이만큼의 디스크가 필요하다"는 요청서

ConfigMap / Secret (환경변수 주입)
├─ ConfigMap: 평문 설정값
└─ Secret: Base64 인코딩된 민감 정보

Ingress (L7 라우팅)
├─ /api/* → backend Service
└─ /* → frontend Service

HPA (HorizontalPodAutoscaler)
└─ CPU 70% 초과 시 Pod 2개 → 4개 자동 스케일링
```

### 실제 작성한 backend Deployment 예시

```yaml
# k8s/backend/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jobcrawler-backend
  namespace: jobcrawler
spec:
  replicas: 1                          # 서버 1대니까 1개
  selector:
    matchLabels:
      app: jobcrawler-backend
  template:
    metadata:
      labels:
        app: jobcrawler-backend
    spec:
      containers:
        - name: backend
          image: jobcrawler-backend:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: backend-config
            - secretRef:
                name: backend-secrets
          resources:
            requests:
              memory: "512Mi"           # 최소 보장
              cpu: "250m"
            limits:
              memory: "1Gi"             # 최대 허용
              cpu: "1000m"
          readinessProbe:               # 트래픽 받을 준비 됐는지
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:                # 살아있는지
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
          volumeMounts:
            - name: uploads
              mountPath: /app/uploads
      volumes:
        - name: uploads
          persistentVolumeClaim:
            claimName: uploads-pvc
```

```yaml
# k8s/backend/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: jobcrawler-backend
  namespace: jobcrawler
spec:
  selector:
    app: jobcrawler-backend
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

```yaml
# k8s/backend/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: backend-secrets
  namespace: jobcrawler
type: Opaque
data:
  DB_PASSWORD: cGFzc3dvcmQ=           # echo -n "password" | base64
  JWT_SECRET: bXktand0LXNlY3JldA==
  OPENCLAW_API_KEY: c2stZXhhbXBsZQ==
  ENCRYPTION_KEY: bXktZW5jcnlwdGlvbi1rZXk=
```

**이게 backend 하나의 설정이다.** 이걸 4개 서비스에 전부 해야 한다.

---

## 3단계: 로컬 K8s 환경 구축의 고통

### minikube 설치 및 리소스 소모

```bash
# minikube 설치
brew install minikube
minikube start --memory=4096 --cpus=2

# 결과: minikube VM이 메모리 4GB, CPU 2코어를 점유
```

```
[메모리 사용량 비교]

Docker Compose 방식:
├─ PostgreSQL     ~50MB
├─ Redis          ~10MB
├─ Backend (JVM)  ~300MB
├─ Frontend       ~100MB
└─ 합계: ~460MB

Kubernetes (minikube) 방식:
├─ minikube VM           ~700MB    ← K8s 자체가 먹는 메모리
├─ etcd                  ~100MB    ← 분산 키-값 저장소
├─ kube-apiserver        ~250MB    ← API 서버
├─ kube-scheduler        ~50MB     ← Pod 배치 결정
├─ kube-controller-mgr   ~70MB     ← 상태 관리 컨트롤러
├─ coredns               ~30MB     ← 클러스터 내부 DNS
├─ kube-proxy            ~30MB     ← 네트워크 프록시
├─ ingress-controller    ~100MB    ← Nginx Ingress
├─ PostgreSQL Pod        ~50MB
├─ Redis Pod             ~10MB
├─ Backend Pod           ~300MB
├─ Frontend Pod          ~100MB
└─ 합계: ~1,790MB
```

**같은 서비스를 돌리는데 메모리가 4배.** K8s 컨트롤 플레인 자체가 ~1.3GB를 먹는다.

### CS 원리: 왜 K8s는 이렇게 무거운가

```
[Kubernetes 아키텍처 — 컨트롤 플레인]

                    ┌─────────────────────────────┐
                    │       kube-apiserver         │
                    │  (모든 통신의 중앙 게이트웨이) │
                    └──────────┬──────────────────┘
                               │ REST API
           ┌───────────────────┼───────────────────┐
           │                   │                   │
   ┌───────▼──────┐   ┌───────▼──────┐   ┌───────▼──────┐
   │ kube-scheduler│   │ controller-  │   │    etcd      │
   │              │   │ manager      │   │ (분산 KV)    │
   │ "이 Pod는    │   │ "현재 상태와  │   │ "클러스터의  │
   │  어느 노드에  │   │  원하는 상태  │   │  모든 상태를 │
   │  배치할까?"  │   │  를 맞춘다"   │   │  여기 저장"  │
   └──────────────┘   └──────────────┘   └──────────────┘
```

**etcd**: Raft 합의 알고리즘 기반 분산 저장소. 클러스터의 모든 상태(Pod, Service, Secret 등)를
여기에 저장한다. 단일 노드에서도 이 합의 프로토콜이 돌아간다 — 분산 시스템 오버헤드를 혼자 감당하는 셈이다.

```
[Raft 합의 알고리즘 — etcd의 핵심]

1. Leader Election: 노드 중 하나가 리더가 됨
2. Log Replication: 리더가 변경사항을 팔로워에게 전파
3. Commit: 과반수가 확인하면 적용

문제: 노드가 1개뿐이면?
→ 리더 = 팔로워 = 본인. 합의 프로토콜의 모든 단계를 혼자 수행.
→ heartbeat, 타임아웃 체크, WAL(Write-Ahead Log) 기록 등
   분산을 위한 메커니즘이 단일 노드에서도 그대로 동작.
→ 불필요한 오버헤드.
```

**kube-apiserver**: 모든 컴포넌트의 통신이 여기를 거친다. kubectl 명령, 컨트롤러의 상태 조회,
스케줄러의 Pod 배치 결정 — 전부 REST API로 apiserver를 호출한다.

```
[요청 흐름: Pod 하나 생성하기]

kubectl apply → apiserver → etcd에 저장
                         → scheduler가 Watch로 감지
                         → scheduler → apiserver에 "노드 X에 배치"
                         → apiserver → etcd에 업데이트
                         → kubelet(노드)이 Watch로 감지
                         → kubelet → 컨테이너 런타임에 컨테이너 생성
                         → kubelet → apiserver에 상태 보고
                         → apiserver → etcd에 업데이트

Docker Compose:
docker compose up → 컨테이너 바로 생성. 끝.
```

**컨테이너 하나 띄우는데 K8s는 6단계, Compose는 1단계.**

---

## 4단계: 네트워킹의 복잡성

### K8s 네트워크 모델

```
[Kubernetes 네트워킹 계층]

Pod Network (CNI — Container Network Interface)
├─ 각 Pod는 고유 IP를 가짐 (172.17.0.x)
├─ Pod끼리는 NAT 없이 직접 통신 가능
└─ CNI 플러그인이 이 네트워크를 구성 (Calico, Flannel 등)

Service Network (kube-proxy)
├─ Service는 가상 IP (ClusterIP)를 가짐
├─ kube-proxy가 iptables/IPVS 규칙으로 라우팅
└─ Pod가 죽고 새로 뜨면 IP가 바뀌지만 Service IP는 고정

DNS (CoreDNS)
├─ Service 이름으로 접근 가능
│   예: "jobcrawler-backend.jobcrawler.svc.cluster.local"
└─ Pod 간 통신을 DNS로 추상화
```

```
[서비스 4개의 네트워크 토폴로지 — K8s]

                 ┌──── Ingress (외부 → 클러스터) ────┐
                 │  /api/* → backend:8080            │
                 │  /*     → frontend:3000           │
                 └───────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
    ┌─────────▼──┐   ┌───────▼────┐   ┌──────▼──────┐
    │ frontend   │   │ backend    │   │ (외부 접근  │
    │ Service    │   │ Service    │   │  불필요)    │
    │ :3000      │   │ :8080      │   │             │
    └─────────┬──┘   └──┬────────┘   └─────────────┘
              │         │
              │    ┌────┼────────────┐
              │    │    │            │
              │  ┌─▼────▼─┐  ┌──────▼──────┐
              │  │postgres │  │   redis     │
              │  │Service  │  │   Service   │
              │  │:5432    │  │   :6379     │
              │  └─────────┘  └─────────────┘
              │
    kube-proxy가 iptables 규칙으로 Service IP → Pod IP 변환
    CoreDNS가 Service 이름 → Service IP 변환
```

### Docker Compose 네트워킹

```
[서비스 4개의 네트워크 — Docker Compose]

docker compose up 시 자동으로 bridge 네트워크 1개 생성

┌─── jobcrawler_default (bridge network) ───┐
│                                           │
│  frontend:3000 ←→ backend:8080            │
│                    ↕          ↕            │
│              postgres:5432  redis:6379     │
│                                           │
│  서비스 이름이 곧 호스트명 (Docker DNS)     │
│  backend → "postgres:5432" 로 바로 접근    │
└───────────────────────────────────────────┘

외부 접근: ports 매핑 (8080:8080, 3000:3000)
```

**K8s**: CNI + kube-proxy + CoreDNS + Ingress Controller + iptables 규칙
**Compose**: Docker 내장 DNS + bridge 네트워크. 끝.

### CS 원리: 왜 K8s 네트워킹이 이렇게 복잡한가

K8s는 **수백 개 노드에서 수천 개 Pod가 통신**하는 환경을 전제로 설계되었다.

```
[K8s가 해결하는 네트워크 문제]

1. Pod IP 불안정성
   Pod가 죽고 다시 뜨면 IP가 바뀐다.
   → Service가 안정적인 가상 IP를 제공.
   → 하지만 서비스 4개, 서버 1대에서는 Pod가 죽을 일이 거의 없다.

2. 노드 간 통신
   노드 A의 Pod → 노드 B의 Pod 통신에 오버레이 네트워크 필요.
   → CNI가 VXLAN 터널링으로 해결.
   → 서버 1대에서는 노드 간 통신 자체가 없다.

3. 서비스 디스커버리
   Pod가 동적으로 생성/삭제되면 "어디에 있는지" 알아야 한다.
   → CoreDNS + Endpoints Controller가 자동 업데이트.
   → 서비스 4개 고정이면 docker-compose.yml에 이름 쓰면 끝이다.

4. 외부 트래픽 라우팅
   L7 라우팅(경로 기반 분기)을 클러스터 레벨에서 처리.
   → Ingress Controller (Nginx) 별도 배포 필요.
   → Compose에서는 Nginx 컨테이너 하나 추가하면 된다.
```

---

## 5단계: Playwright 크롤러의 K8s 호환성 문제

### 현상

```bash
# Backend Pod 로그
ERROR: browserType.launch: Failed to launch chromium
Error: Failed to launch browser: cannot open display
```

### 원인

Playwright는 **실제 Chromium 브라우저**를 띄운다. K8s Pod 안에서 브라우저를 실행하려면:

```
[Playwright in K8s — 문제점]

1. 브라우저 바이너리 크기
   Chromium ~400MB + 의존성 라이브러리 ~200MB
   → Docker 이미지 크기 1.5GB+ (alpine 기반 불가)
   → Pod 시작 시간 30초+ (이미지 풀)

2. 공유 메모리 (shm)
   Chromium은 /dev/shm을 사용하여 탭 간 메모리 공유.
   K8s Pod의 기본 /dev/shm 크기: 64MB
   Chromium이 여러 탭 열면 shm 부족 → 크래시

   해결: Pod spec에 emptyDir.medium: Memory 볼륨 추가
   volumes:
     - name: dshm
       emptyDir:
         medium: Memory
         sizeLimit: "1Gi"

3. 보안 컨텍스트
   Chromium sandbox는 Linux namespace를 사용.
   K8s Pod 안에서 다시 namespace를 만들면 중첩 → 권한 에러.

   해결: --no-sandbox 플래그 (보안 취약) 또는
         securityContext.capabilities.add: ["SYS_ADMIN"]

4. 세션 유지
   Playwright userDataDir에 로그인 쿠키/세션 저장.
   Pod가 재시작되면 이 디렉토리가 사라짐 → 4개 사이트 로그인 날아감.

   해결: PVC로 userDataDir 마운트.
         하지만 PVC는 단일 Pod에만 마운트 가능 (ReadWriteOnce).
         → Pod 재스케줄링 시 다른 노드에 뜨면 접근 불가.
         → 서버 1대라서 당장은 문제없지만, K8s의 이점(노드 분산)을 못 씀.
```

### Docker Compose에서는?

```yaml
# docker-compose.yml — Playwright 문제가 없다
services:
  backend:
    build: .
    shm_size: '2gb'          # shm 크기 직접 지정. 끝.
    volumes:
      - ./uploads:/app/uploads
      - playwright-data:/app/.playwright    # 세션 유지
```

**K8s에서 4가지 문제를 각각 해결해야 하는 것을 Compose는 2줄로 해결한다.**

---

## 6단계: 운영 복잡도 비교

### 배포 명령

```bash
# Kubernetes
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/redis/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress-controller/

# 또는 Helm으로 패키징했다면
helm install jobcrawler ./helm-chart --values values.yaml

# 상태 확인
kubectl get pods -n jobcrawler
kubectl logs -f deployment/jobcrawler-backend -n jobcrawler
kubectl describe pod jobcrawler-backend-xxx -n jobcrawler
```

```bash
# Docker Compose
docker compose up -d

# 상태 확인
docker compose ps
docker compose logs -f backend
```

### 디버깅 시나리오: "Backend가 안 뜬다"

```
[K8s 디버깅 플로우]

1. kubectl get pods → STATUS: CrashLoopBackOff
2. kubectl describe pod xxx → Events 섹션 확인
   - ImagePullBackOff? → 이미지 이름/태그 확인
   - OOMKilled? → resources.limits.memory 증가
   - Readiness probe failed? → health check 경로 확인
3. kubectl logs xxx → 애플리케이션 로그 확인
4. kubectl exec -it xxx -- /bin/sh → 컨테이너 진입
5. kubectl get events -n jobcrawler → 클러스터 이벤트

CrashLoopBackOff의 재시작 간격:
1회: 10초, 2회: 20초, 3회: 40초, ... 최대 5분
→ 오류 수정 후에도 5분 대기해야 할 수 있음

[Docker Compose 디버깅 플로우]

1. docker compose ps → STATUS: Exit 1
2. docker compose logs backend → 로그 바로 확인
3. 코드 수정
4. docker compose up -d backend → 즉시 재시작
```

### 설정 변경 시나리오: "환경변수 하나 추가"

```
[K8s]
1. k8s/backend/configmap.yaml 수정
2. kubectl apply -f k8s/backend/configmap.yaml
3. kubectl rollout restart deployment/jobcrawler-backend -n jobcrawler
   (ConfigMap 변경은 Pod에 자동 반영 안 됨 — 재시작 필요)

[Docker Compose]
1. .env 파일에 한 줄 추가
2. docker compose up -d backend
```

---

## 7단계: 리소스 오버헤드 정량 비교

### 실측 데이터

```
[시스템 리소스 사용량]

                        Docker Compose    Kubernetes (minikube)
──────────────────────────────────────────────────────────────
메모리 (서비스)            460MB             460MB
메모리 (오케스트레이터)      0MB            1,330MB
메모리 (합계)              460MB           1,790MB
──────────────────────────────────────────────────────────────
CPU (유휴 시)              ~2%              ~8%
CPU (K8s 컨트롤러 루프)     -               ~5%
──────────────────────────────────────────────────────────────
디스크 (이미지)            ~2GB             ~2GB
디스크 (K8s 바이너리)       -              ~500MB
디스크 (etcd WAL)          -              ~200MB
──────────────────────────────────────────────────────────────
컨테이너 시작 시간         ~10초            ~45초
YAML 파일 수               1개             15개+
```

### CS 원리: 컨트롤 루프와 리소스 소모

K8s의 핵심 철학은 **선언적 상태 관리(Declarative State Management)**이다.

```
[선언적 vs 명령적]

명령적 (Docker Compose):
"PostgreSQL 컨테이너를 시작해라" → 시작됨. 끝.
컨테이너가 죽으면? restart: always 정책으로 데몬이 재시작.

선언적 (Kubernetes):
"PostgreSQL Pod가 1개 있어야 한다" → 컨트롤러가 현재 상태를 계속 확인.

[컨트롤 루프 — Reconciliation Loop]

while (true) {
    현재상태 = etcd에서 조회();        // 실제 Pod 몇 개인지
    원하는상태 = Deployment spec 조회(); // replicas: 1

    if (현재상태 != 원하는상태) {
        조치();  // Pod 생성 또는 삭제
    }

    sleep(동기화주기);  // 기본 10초
}

이 루프가 컨트롤러마다 독립적으로 돈다:
├─ Deployment Controller
├─ ReplicaSet Controller
├─ Node Controller
├─ Service Controller
├─ Endpoint Controller
├─ Namespace Controller
└─ ... 20개+

→ 서비스 4개뿐인데도 20개 컨트롤러가 10초마다 etcd를 조회하고
  apiserver와 통신한다. 이게 유휴 시 CPU 5%의 원인이다.
```

---

## 8단계: K8s가 빛을 발하는 조건

### 이런 경우에는 K8s가 맞다

```
[K8s가 필요한 규모]

1. 서비스 수 10개+
   - API Gateway, Auth Service, User Service, Crawl Service,
     AI Service, Notification Service, Scheduler, ...
   - 서비스 간 의존성이 복잡해서 서비스 디스커버리가 필수

2. 서버(노드) 3대+
   - 노드 장애 시 다른 노드로 Pod 자동 이동 (self-healing)
   - 이게 K8s의 진짜 가치. 서버 1대면 이동할 곳이 없다.

3. 트래픽 변동이 큰 경우
   - HPA: CPU 70% 넘으면 Pod 자동 추가
   - VPA: Pod의 리소스 요청량 자동 조정
   - Cluster Autoscaler: 노드 자체를 추가/삭제
   - 일정한 트래픽(사용자 수백 명)에서는 오토스케일링이 불필요

4. 무중단 배포가 핵심인 경우
   - Rolling Update: Pod를 하나씩 교체
   - Blue/Green: 새 버전 전체 배포 후 트래픽 전환
   - Canary: 10% 트래픽만 새 버전으로 → 안정 확인 후 100%
   - 포트폴리오 프로젝트에서 5초 다운타임은 문제가 안 된다
```

### 이끼잡의 현실

```
서비스: 4개         → K8s 임계치(10+)의 40%
서버: 1대           → K8s의 핵심 가치(노드 분산)를 못 씀
트래픽: 수백 명      → 오토스케일링 불필요
다운타임 허용: 높음   → 무중단 배포 불필요
```

**4개 항목 전부 K8s가 필요 없는 영역이다.**

---

## 9단계: Docker Compose로 복귀

### 최종 docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:15-alpine
    container_name: jobcrawler-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: ${DB_USERNAME:-jobcrawler}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-password}
      POSTGRES_DB: jobcrawler
    volumes:
      - postgres-data:/var/lib/postgresql/data
    restart: always
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U jobcrawler"]
      interval: 10s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: jobcrawler-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    restart: always
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      retries: 5

  backend:
    build:
      context: ./job-crawler
      dockerfile: Dockerfile
    container_name: jobcrawler-backend
    ports:
      - "8080:8080"
    env_file:
      - .env
    shm_size: '2gb'
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    volumes:
      - ./uploads:/app/uploads
      - playwright-data:/app/.playwright
    restart: always

  frontend:
    build:
      context: ./job-frontend
      dockerfile: Dockerfile
    container_name: jobcrawler-frontend
    ports:
      - "3000:3000"
    env_file:
      - .env
    depends_on:
      - backend
    restart: always

volumes:
  postgres-data:
  redis-data:
  playwright-data:
```

**YAML 15개 → 1개. 설정량 90% 감소.**

---

## 10단계: 의사결정 프레임워크

### 컨테이너 오케스트레이션 선택 기준

```
[의사결정 트리]

서비스 몇 개?
├─ 1~5개 → Docker Compose
├─ 5~15개 → Docker Compose 또는 Docker Swarm
└─ 15개+ → Kubernetes

서버 몇 대?
├─ 1대 → Docker Compose (K8s의 핵심 가치를 쓸 수 없음)
├─ 2~5대 → Docker Swarm 또는 K8s (k3s 경량판)
└─ 5대+ → Kubernetes

무중단 배포 필수?
├─ 아니오 → Docker Compose + restart: always
└─ 예 → Docker Swarm (간단) 또는 Kubernetes (정교)

트래픽 변동 심한가?
├─ 아니오 → Docker Compose
└─ 예 → Kubernetes HPA
```

### 이끼잡의 최적 경로

```
[현재] 로컬 1대 → Docker Compose
           │
           ▼ (사용자 1000명+ / 서버 2대+로 성장)
[미래] 클라우드 → Docker Compose on EC2 (서비스 10개 미만이면 여전히 충분)
           │
           ▼ (마이크로서비스 분리 / 노드 5대+)
[필요 시] Kubernetes (EKS/GKE)
```

---

## 교훈

### 1. 기술 선택은 현재 규모에 맞춰야 한다

```
"나중에 K8s 쓸 거니까 처음부터" → 틀림
"지금 Compose로 시작하고, K8s가 필요해지면 그때" → 맞음

Dockerfile은 K8s에서도 그대로 쓴다.
Compose → K8s 전환 비용은 K8s YAML 작성뿐이다.
반대로 K8s → Compose 전환은 복잡한 설정을 걷어내는 작업이다.
작은 것에서 큰 것으로 가는 게 큰 것에서 작은 것으로 오는 것보다 쉽다.
```

### 2. 오버헤드의 정당성을 따져야 한다

```
K8s 컨트롤 플레인 1.3GB는 "노드 장애 시 자동 복구"를 위한 비용이다.
노드가 1대면 복구할 곳이 없으므로 이 비용은 100% 낭비다.

K8s 네트워크 스택(CNI + kube-proxy + CoreDNS)은
"수천 개 Pod의 동적 디스커버리"를 위한 비용이다.
Pod 4개 고정이면 docker-compose.yml에 이름 적으면 끝이다.
```

### 3. 복잡성은 비용이다

```
YAML 15개 = 관리 포인트 15개 = 버그 가능성 15배
디버깅 6단계 vs 2단계 = 장애 대응 시간 3배
CrashLoopBackOff 5분 대기 vs 즉시 재시작 = 개발 속도 차이

"이력서에 K8s 쓸 수 있다"는 이 비용을 정당화하지 못한다.
오히려 "왜 K8s 대신 Compose를 선택했는가"를 설명할 수 있는 것이
더 높은 수준의 엔지니어링 판단력을 보여준다.
```

### 4. 디버깅 팁

```
K8s를 쓰다가 "이게 맞나?" 싶으면:
- 컨트롤 플레인 메모리 사용량을 측정하라 (kubectl top nodes)
- 서비스 메모리보다 오케스트레이터 메모리가 크면 → 오버엔지니어링
- YAML 파일 수가 서비스 수의 3배 이상이면 → 오버엔지니어링
- kubectl 명령어 치트시트가 필요하면 → 일상 운영에 맞지 않는 도구

Docker Compose가 한계에 부딪히는 신호:
- docker compose logs가 서비스 10개+ 로그로 읽기 불가능할 때
- 서버 1대로 감당이 안 돼서 2대 이상 필요할 때
- 배포 시 다운타임 0초가 비즈니스 요구사항일 때
→ 이때 K8s를 검토해도 늦지 않다.
```
