# Docker 로컬 배포 트러블슈팅 — Buildx 깨진 심볼릭 링크

## 현상

```bash
$ docker-compose build backend
fork/exec /Users/sanghyunyoun/.docker/cli-plugins/docker-buildx: no such file or directory
```

`docker-compose build` 명령이 Buildx 플러그인을 찾지 못해 이미지 빌드 자체가 실패했다.

---

## 원인 추적 과정

### 1단계: Docker 환경 확인

```bash
$ docker info | head -5
Client: Docker Engine - Community
 Version:    28.2.2
 Context:    colima       # ← Docker Desktop이 아닌 Colima 사용
```

Docker Desktop이 아닌 **Colima**(경량 Docker 런타임)를 사용 중이었다.

### 2단계: Buildx 플러그인 상태 확인

```bash
$ ls ~/.docker/cli-plugins/
docker-buildx  docker-compose  docker-desktop  ...

$ file ~/.docker/cli-plugins/docker-buildx
broken symbolic link to /Volumes/Docker/Docker.app/Contents/Resources/cli-plugins/docker-buildx
```

**원인 발견**: `docker-buildx` 파일이 Docker Desktop 경로(`/Volumes/Docker/Docker.app/...`)를 가리키는 **심볼릭 링크**였는데, Docker Desktop이 설치되지 않은(또는 제거된) 상태라 링크가 깨져 있었다.

### 3단계: 왜 이런 상태가 되었나

```
[시간순 추정]

1. Docker Desktop 설치 → ~/.docker/cli-plugins/에 심볼릭 링크 생성
2. Docker Desktop 제거 + Colima 설치 → 런타임은 Colima로 전환
3. 하지만 ~/.docker/cli-plugins/ 디렉토리는 정리되지 않음
4. docker-compose가 빌드 시 buildx 플러그인을 발견 → 실행 시도 → 깨진 링크 → 실패
```

---

## 해결

### 시도 1: brew install docker-buildx

```bash
$ brew install docker-buildx
Error: You have not agreed to the Xcode license.
```

Xcode 라이선스 미동의로 brew 설치 자체가 불가했다. `sudo xcodebuild -license accept`가 필요하지만 sudo 권한 없이 해결하고 싶었다.

### 시도 2: 깨진 심볼릭 링크 제거

```bash
$ rm ~/.docker/cli-plugins/docker-buildx
```

깨진 링크를 제거하면 docker-compose가 buildx를 찾지 않고 **legacy builder**로 폴백한다.

### 시도 3: Legacy builder로 빌드

```bash
$ DOCKER_BUILDKIT=0 docker-compose build
```

`DOCKER_BUILDKIT=0` 환경변수로 BuildKit을 명시적으로 비활성화하면 legacy builder를 사용한다. multi-stage 빌드는 legacy builder에서도 지원된다.

---

## 왜 이렇게 되는지 — CS 원리

### Docker 빌드 시스템의 이중 구조

```
[Docker 이미지 빌드 방식 2가지]

1. Legacy Builder (docker build)
   ├─ Docker Engine 내장
   ├─ Dockerfile → 레이어별 순차 빌드
   ├─ 캐싱: 레이어 해시 기반
   └─ 한계: 병렬 빌드 불가, 빌드 캐시 비효율

2. BuildKit (docker buildx build)
   ├─ 별도 데몬 (buildkitd)
   ├─ DAG(방향 비순환 그래프) 기반 병렬 빌드
   ├─ 캐싱: 콘텐츠 기반 (더 정밀)
   ├─ 마운트 캐시 (--mount=type=cache)
   └─ 멀티 플랫폼 빌드 (linux/amd64, linux/arm64)
```

### docker-compose build의 빌더 선택 로직

```
docker-compose build 실행 시:

1. DOCKER_BUILDKIT 환경변수 확인
   ├─ DOCKER_BUILDKIT=0 → legacy builder 사용
   └─ DOCKER_BUILDKIT=1 또는 미설정 → BuildKit 시도

2. BuildKit 사용 시:
   ├─ ~/.docker/cli-plugins/docker-buildx 존재? → 실행
   │   ├─ 정상 바이너리 → BuildKit으로 빌드
   │   └─ 깨진 심볼릭 링크 → "no such file or directory" 에러 ← 우리 상황
   └─ 없음 → legacy builder로 폴백
```

**핵심**: docker-compose는 buildx 파일이 **존재하는지**(파일 시스템 레벨)와 **실행 가능한지**(바이너리 레벨)를 구분하지 못한다. 깨진 심볼릭 링크는 `ls`에는 보이지만 `exec`하면 실패한다. 이것이 "파일이 있는데 없다"는 혼란스러운 에러의 원인이다.

### 심볼릭 링크 vs 하드 링크 (왜 깨지는가)

```
[심볼릭 링크 (Symbolic Link)]

파일 A → 경로 "/Volumes/Docker/Docker.app/.../docker-buildx"
           │
           └─ 이 경로의 파일이 존재하는지는 링크 생성 시점에 검증하지 않는다.
              원본이 삭제/이동되면 "dangling symlink" (깨진 링크)가 된다.

[하드 링크 (Hard Link)]

파일 A → inode 12345 ← 파일 B (같은 데이터 블록)
           │
           └─ 원본을 삭제해도 inode가 살아있으면 접근 가능.
              하지만 하드 링크는 같은 파일시스템 내에서만 가능.
              /Volumes (외부 볼륨)와 /Users (내부)는 다른 파일시스템이라
              Docker Desktop은 심볼릭 링크를 쓸 수밖에 없다.
```

---

---

## 추가 트러블슈팅: Credential Helper + Colima 메모리 부족

### 현상 2: docker-credential-desktop not found

```bash
$ DOCKER_BUILDKIT=0 docker-compose build backend
error listing credentials - err: exec: "docker-credential-desktop": executable file not found in $PATH
Cannot connect to the Docker daemon
```

### 원인

`~/.docker/config.json`에 `"credsStore": "desktop"` 설정이 남아있었다. Docker Desktop 전용 credential helper인데 Colima 환경에는 없다.

### 해결

```json
// ~/.docker/config.json — "credsStore" 제거
{
  "auths": {},
  "currentContext": "colima"
}
```

### CS 원리: Docker Credential Store

```
[Docker 이미지 풀 시 인증 흐름]

docker pull → config.json의 credsStore 확인
├─ "desktop" → docker-credential-desktop 바이너리 호출
│   └─ macOS Keychain에서 레지스트리 토큰 조회
├─ "osxkeychain" → docker-credential-osxkeychain 호출
│   └─ macOS Keychain 직접 접근
└─ (없음) → config.json의 auths 섹션에서 Base64 토큰 조회
    └─ Docker Hub public 이미지는 인증 불필요 → 바로 풀
```

Docker Desktop을 제거해도 config.json은 유저 홈에 남으므로 수동 정리가 필요하다.

---

### 현상 3: Gradle build daemon disappeared unexpectedly

```bash
Step 7/14 : RUN ./gradlew bootJar --no-daemon -x test
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
```

### 원인

Colima VM 기본 메모리가 **2GB**인데, Docker 빌드 컨텍스트 안에서:
- JDK 21 자체: ~300MB
- Gradle Daemon: ~500MB
- Spring Boot 컴파일 (annotation processing 포함): ~800MB
- 합계: ~1.6GB, 나머지 시스템에 400MB밖에 안 남음 → OOM Kill

```
[Colima VM 메모리 배분 — 2GB]

┌─────────────── 2048MB ──────────────┐
│ Linux Kernel + systemd    ~200MB    │
│ Docker Engine             ~150MB    │
│ 이미지 레이어 캐시          ~300MB    │
│ JDK 21                    ~300MB    │
│ Gradle Daemon             ~500MB    │
│ Spring Boot 컴파일         ~800MB    │ ← 여기서 OOM
│ 남은 공간                   ~-200MB  │ ← 부족
└─────────────────────────────────────┘
```

### 해결

```bash
colima stop
colima start --memory 6 --cpu 4
```

```
[Colima VM 메모리 배분 — 6GB]

┌─────────────── 6144MB ──────────────┐
│ Linux Kernel + systemd    ~200MB    │
│ Docker Engine             ~150MB    │
│ 이미지 레이어 캐시          ~300MB    │
│ JDK 21                    ~300MB    │
│ Gradle Daemon             ~500MB    │
│ Spring Boot 컴파일         ~800MB    │
│ 남은 공간                  ~3894MB   │ ← 충분
└─────────────────────────────────────┘
```

### CS 원리: OOM Killer

Linux 커널의 OOM(Out of Memory) Killer는 메모리가 부족하면 가장 메모리를 많이 쓰는 프로세스를 강제 종료한다.

```
[OOM Killer 동작 방식]

1. 가용 메모리가 임계치 이하로 떨어짐
2. 커널이 oom_score가 가장 높은 프로세스를 선택
   - oom_score = 메모리 사용량 / 총 메모리 × 비례계수
   - JVM(Gradle Daemon)은 메모리 대식가 → 높은 oom_score
3. SIGKILL(9) 전송 → 프로세스 즉시 종료
4. Gradle은 "daemon disappeared unexpectedly"로 보고

dmesg에서 확인 가능:
[12345.678] Out of memory: Kill process 1234 (java) score 850
```

Docker 빌드 컨테이너 안에서 OOM이 발생하면 `docker build`는 에러 코드만 반환하고, 구체적인 OOM 메시지는 VM의 dmesg에만 남는다. 이것이 에러 메시지가 모호한 이유다.

## 교훈

1. **Docker Desktop → Colima 전환 시**: `~/.docker/cli-plugins/` 디렉토리를 정리해야 한다. 깨진 심볼릭 링크가 빌드를 막을 수 있다.

2. **에러 메시지 해석**: "no such file or directory"가 항상 "파일이 없다"는 뜻은 아니다. 깨진 심볼릭 링크처럼 "파일은 있지만 가리키는 대상이 없다"일 수 있다. `file` 명령으로 확인하라.

3. **BuildKit vs Legacy**: multi-stage 빌드는 양쪽 다 지원한다. BuildKit이 없으면 `DOCKER_BUILDKIT=0`으로 폴백하면 된다. BuildKit의 장점(병렬 빌드, 캐시 마운트)은 이미지가 크거나 CI에서 반복 빌드할 때 체감된다.

4. **디버깅 순서**: `docker info` → `file 바이너리경로` → `ls -la` 순으로 환경부터 확인하라. 코드 문제가 아니라 환경 문제인 경우가 많다.

5. **Colima 메모리**: 기본 2GB는 간단한 컨테이너 실행용이다. JVM 기반 빌드(Gradle, Maven)는 최소 4GB, 권장 6GB 이상. `colima start --memory 6`으로 시작하라.

6. **Docker Desktop 잔재 정리 체크리스트**:
   - `~/.docker/cli-plugins/` — 깨진 심볼릭 링크 제거
   - `~/.docker/config.json` — `credsStore: "desktop"` 제거
   - `/Volumes/Docker/` — Docker Desktop 볼륨 마운트 잔재 확인
