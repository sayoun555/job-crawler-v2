# 사이트별 이력서 자기소개서 HTML 구조

각 사이트의 이력서 편집 페이지에서 자기소개서(자소서) 섹션의 실제 HTML 구조를 정리한 문서.
자동 지원 시 커스텀 자소서 문항을 이력서에 매핑할 때 참고.

---

## 1. 사람인 (SARAMIN)

**이력서 편집 URL**: `https://www.saramin.co.kr/zf_user/member/resume-manage/edit?res_idx={res_idx}`

### 자기소개서 섹션 구조
- 기본 상태: `display: none` (숨겨져 있음)
- "추가" 버튼 클릭으로 문항 추가

```html
<section id="introduce" class="resume_letter" style="display:none;">
  <div class="resume_tit">
    <div class="box_tit">
      <h2 class="tit">자기소개서</h2>
    </div>
    <button type="button" class="btn_add evtWriteItem">추가</button>
  </div>
  <div class="resume_none">자기소개서를 작성해주세요.</div>
</section>
```

### 문항 추가 후 예상 구조 (사람인 표준 양식)
- 제목 input + 내용 textarea
- 다중 문항 지원 (추가 버튼으로 반복)
- 글자 수 카운터 포함

### 셀렉터 전략
- 섹션: `#introduce` 또는 `section.resume_letter`
- 추가 버튼: `.btn_add.evtWriteItem` (section#introduce 내부)
- 제목: 문항별 `input[type="text"]` (name 패턴 확인 필요)
- 내용: 문항별 `textarea` (name 패턴 확인 필요)
- **NOTE**: 사람인은 SPA가 아니라 전통적 폼. 문항 추가 시 DOM이 동적으로 생성됨.

### 매핑 방식
- 커스텀 자소서 섹션 수만큼 "추가" 버튼 클릭
- 각 문항의 제목 input에 title, 내용 textarea에 content 입력

---

## 2. 잡코리아 (JOBKOREA)

**이력서 편집 URL**: `https://www.jobkorea.co.kr/User/Resume/Write`

### 자기소개서 섹션 구조
- 사이드바 "이력서 항목" 목록에서 "자기소개서" 옆 "필드추가" 버튼 클릭으로 섹션 생성
- 문항별 제목 + 내용 구조

```
heading "자기소개서" (h2)
  ├─ [문항 1]
  │   ├─ textbox "제목" (input, name 패턴 확인 필요)
  │   ├─ textbox "해당내용을 입력하세요." (textarea, multiline)
  │   ├─ 글자 수 카운터 ("0자")
  │   └─ button "단락삭제"
  ├─ button "추가" ← 문항 추가
  └─ button "순서변경"
```

### 셀렉터 전략
- 섹션 활성화: 사이드바에서 `link "자기소개서"` 옆 `button "필드추가"` 클릭
- 문항 추가: `heading "자기소개서"` 하위 `button "추가"` 클릭
- 제목: `textbox "제목"` (자기소개서 heading 하위)
- 내용: `textbox "해당내용을 입력하세요."` (multiline textarea)
- 문항 삭제: `button "단락삭제"`

### 매핑 방식
- "필드추가" 클릭 → 자기소개서 섹션 생성 (1개 문항 자동 생성)
- 추가 문항이 필요하면 "추가" 버튼 클릭 (섹션 수 - 1 번)
- 각 문항의 제목 textbox에 title, 내용 textarea에 content 입력
- **다중 문항 지원 O** — 제목+내용 쌍을 여러 개 추가 가능

---

## 3. 잡플래닛 (JOBPLANET)

**이력서 편집 URL**: `https://www.jobplanet.co.kr/profile/resumes/{id}`
**이력서 목록 URL**: `https://www.jobplanet.co.kr/profile/resumes`

### 자기소개서 섹션 구조
- heading "자기소개" (h3)
- **단일 textarea** (다중 문항 불가)

```html
<div class="resume_input_profile">
  <div class="rsm_hgroup rsm_cover_letter">
    <h3 class="rsm_ttl">자기소개</h3>
    <span class="rsm-info">작성률 +10%</span>
  </div>
  <div class="resume_one_box">
    <div class="flexible_textarea">
      <textarea class="medit"
        placeholder="자기소개를 30자 이상 작성하면 작성률이 10% 높아져요.
업무 경험과 나만의 강점을 토대로 자기소개를 작성해 보세요">
      </textarea>
    </div>
  </div>
</div>
```

### 셀렉터 전략
- 섹션: `h3.rsm_ttl` 텍스트 "자기소개" 근처
- textarea: `.rsm_cover_letter` 하위 `textarea.medit` 또는 placeholder에 "30자" 포함
- **단일 필드** — 제목 없이 내용만

### 매핑 방식
- 커스텀 자소서 전체 섹션을 합쳐서 하나의 textarea에 입력
- 형식: `[문항제목1]\n내용1\n\n[문항제목2]\n내용2\n\n...`
- **다중 문항 불가** — 반드시 합침

---

## 4. 링커리어 (LINKAREER)

**이력서 작성 URL**: `https://linkareer.com/my-career/resume/write`
**이력서 목록 URL**: `https://linkareer.com/my-career/resume`

### 자기소개서 섹션 구조
- 기본 상태: 숨겨져 있음 (사이드바에서 "섹션 추가 아이콘" 클릭으로 생성)
- 문항별 제목 + 내용 구조 (잡코리아와 유사)
- **다중 문항 지원 O**

```html
<header><h2>자기소개서</h2></header>
<hr>
<fieldset class="CoverLetterSection__StyledWrapper-sc-...">
  <fieldset class="CoverLetterSectionItem__StyledWrapper-sc-..." draggable="true">
    <!-- 드래그 아이콘 (순서 변경) -->
    <svg class="drag-icon">...</svg>

    <!-- 제목 입력 -->
    <div class="InputTextField__StyledWrapper-sc-...">
      <input type="text"
        placeholder=" "
        class="title-field"
        maxlength="100"
        name="coverLetter.0.title">
      <span class="label-text">제목<span class="required">*</span></span>
    </div>

    <!-- 내용 입력 -->
    <div class="ContentField__StyledWrapper-sc-...">
      <div class="InputTextAreaField__StyledWrapper-sc-...">
        <textarea
          placeholder=" "
          class="content-field"
          maxlength="5000"
          name="coverLetter.0.content">
        </textarea>
        <span class="label-text">내용<span class="required">*</span></span>
      </div>
      <output class="content-length-wrapper">
        <span>공백 포함 <strong>0</strong>자 /0 bytes</span>
        <span>공백 제외 <strong>0</strong>자 /0 bytes</span>
      </output>
    </div>

    <!-- 삭제 버튼 -->
    <button class="close-btn" type="button">닫기</button>
  </fieldset>
</fieldset>

<!-- 문항 추가 버튼 -->
<button type="button" class="AddFormItemButton__StyledWrapper-sc-...">
  자기소개서 항목 추가
</button>
```

### 셀렉터 전략
- 섹션 활성화: 사이드바에서 "자기소개서 아이콘" 옆 "섹션 추가 아이콘" 버튼 클릭
- 제목: `input[name="coverLetter.{idx}.title"]` (idx: 0부터 증가)
- 내용: `textarea[name="coverLetter.{idx}.content"]` (idx: 0부터 증가)
- 문항 추가: `button` 텍스트 "자기소개서 항목 추가"
- 문항 삭제: `button.close-btn`
- 순서 변경: 드래그 가능 (`draggable="true"`)

### 매핑 방식
- "섹션 추가" 클릭 → 자기소개서 섹션 생성 (1개 문항 자동 생성)
- 추가 문항이 필요하면 "자기소개서 항목 추가" 버튼 클릭
- `input[name="coverLetter.{i}.title"]`에 title 입력
- `textarea[name="coverLetter.{i}.content"]`에 content 입력

---

## 매핑 전략 요약

| 사이트 | 다중 문항 | 제목 필드 | 내용 필드 | 매핑 방식 |
|--------|----------|----------|----------|----------|
| **사람인** | O | input (제목) | textarea (내용) | 섹션별 1:1 매핑 |
| **잡코리아** | O | textbox "제목" | textarea "해당내용을 입력하세요" | 섹션별 1:1 매핑 |
| **잡플래닛** | X (단일) | 없음 | textarea.medit | 전체 합쳐서 입력 |
| **링커리어** | O | input[name="coverLetter.{i}.title"] | textarea[name="coverLetter.{i}.content"] | 섹션별 1:1 매핑 (name 인덱스) |

### 공통 플로우
1. 지원 전 해당 사이트 이력서의 자기소개서 섹션을 AI 자소서로 업데이트
2. 기존 문항 삭제 → 커스텀 섹션 수만큼 문항 추가 → 제목/내용 입력 → 저장
3. 그 다음 지원 제출 (이력서 선택 → 제출)
