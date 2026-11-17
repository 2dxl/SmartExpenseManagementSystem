# SmartExpenseManagementSystem — 4인 병렬 개발 진행 가이드 (2주 완성 기준)

이 가이드는 이미 구축된 공통 모델과 목(Mock) 데이터를 바탕으로, **4명의 팀원이 서로를 기다리지 않고 동시에 개발**한 뒤 하나로 합치기 위한 역할별 매뉴얼입니다.

---

## 0. 시작 전 모든 팀원 공통 규칙

### 이미 준비된 공용 파일 (수정 금지)
모든 팀원은 아래 4개 파일을 기준으로 개발합니다. (시그니처 변경이 필요할 때는 팀 전체 합의 후 수정합니다.)
- [Payment.java](src/model/Payment.java): 결제 1건 모델 (`matched`, `attachItems()`, `getItems()`)
- [ReceiptItem.java](src/model/ReceiptItem.java): 세부 품목 모델 (`itemName`, `unitPrice`, `quantity`, `category`, `getAmount()`)
- [LlmClient.java](src/llm/LlmClient.java): 로컬 LLM 호출 공통 인터페이스 (`ask(systemPrompt, userContent)`)
- [SampleData.java](tests/model/SampleData.java): 병렬 테스트용 샘플 결제 목록 및 가짜 `LlmClient`

> [!IMPORTANT]
> **파일 충돌 방지 원칙**: 각 팀원은 본인에게 배정된 패키지와 파일만 새로 만들거나 수정합니다. 다른 팀원이 쓰는 파일은 직접 고치지 말고, 필요한 기능이 있으면 요청하세요.

---

## 1. 팀원 A — 데이터 · CSV 파서 · 로컬 LLM 통신

**담당 파일**
- 데이터: [payments_3months.csv](data/payments_3months.csv), `data/receipts/*.txt`
- 코드: [CsvPaymentParser.java](src/parser/CsvPaymentParser.java), `src/llm/LocalLlmClient.java` (신규)

### 단계별 진행 순서
1. **3개월 데이터 확충**
   - 현재 `payments_3months.csv`(18건)와 `data/receipts/`(7건)를 실제 시연에 쓸 만큼 보강합니다.
   - 영수증 파일명은 반드시 **`<결제고유번호>.txt`**(예: `TX-20260901-01.txt`) 규칙을 유지하세요. (정기 구독 결제처럼 영수증이 없는 건은 파일이 없어도 됩니다.)
2. **`CsvPaymentParser` 예외 테스트 보강**
   - `tests/parser/CsvPaymentParserTest.java`를 만들어 빈 파일, 잘못된 헤더, 열 개수 오류, 잘못된 날짜/금액 등을 제대로 걸러내는지 확인합니다.
3. **`LocalLlmClient` 구현 (`implements LlmClient`)**
   - `java.net.http.HttpClient`로 로컬 LLM 서버(예: Ollama `http://localhost:11434/api/generate` 또는 OpenAI 호환 엔드포인트)에 `POST` 요청을 보냅니다.
   - `HttpRequest`에 타임아웃(예: `Duration.ofSeconds(30)`)을 걸어 서버가 응답하지 않을 때 무한 대기하지 않게 합니다.
   - 응답 JSON에서 실제 모델 답변 텍스트만 추출해 `String`으로 반환합니다.
4. **단독 검증**
   - 로컬 LLM 서버를 켠 상태에서 `"안녕, 테스트야"` 같은 짧은 프롬프트로 `LocalLlmClient.ask(...)`가 정상 응답을 받아오는지 확인합니다.

### 완료 기준 (Done)
- [ ] `CsvPaymentParser`가 3개월 치 CSV를 오류 없이 읽어 `List<Payment>`를 반환함
- [ ] `LocalLlmClient.ask(...)` 호출 시 로컬 LLM 응답 문자열이 반환됨
- [ ] 로컬 LLM 서버가 꺼져 있을 때 원인 알 수 있는 예외 메시지를 던짐

---

## 2. 팀원 B — 기능 1: 영수증 품목 추출 및 결제 매칭

**담당 파일**
- 코드: `src/service/ReceiptMatchingService.java` (신규)
- 테스트: `tests/service/ReceiptMatchingServiceTest.java` (신규)

### 단계별 진행 순서
1. **생성자로 `LlmClient` 주입받기**
   ```java
   public class ReceiptMatchingService {
       private final LlmClient llmClient;
       public ReceiptMatchingService(LlmClient llmClient) {
           this.llmClient = llmClient;
       }
   }
   ```
   - 이렇게 만들어 두면 테스트할 때는 `SampleData.createMockLlmClient()`를 넣고, 통합할 때는 팀원 A의 `LocalLlmClient`를 넣을 수 있습니다.
2. **영수증 파일 탐색 및 프롬프트 작성**
   - `matchReceipts(List<Payment> payments, Path receiptDir)` 메서드를 만듭니다.
   - `receiptDir.resolve(payment.getTransactionId() + ".txt")` 파일이 있으면 내용을 읽고, 없으면 건너뜁니다.
   - 로컬 LLM에게 **반드시 JSON 배열 형식으로만 답하라**고 프롬프트를 작성합니다.
     - 예: `[{"itemName":"아메리카노","unitPrice":4500,"quantity":1,"category":"카페/음료"}]`
3. **JSON → `List<ReceiptItem>` 변환 및 방어 처리**
   - LLM이 응답 앞뒤에 ```` ```json ```` 마크다운을 붙여서 줄 때가 많으므로, `[`부터 `]`까지만 잘라낸 뒤 파싱합니다.
   - 파싱에 성공하면 `payment.attachItems(items)`를 호출합니다.
   - 특정 영수증 1장이 파싱에 실패하더라도 전체 프로그램이 멈추지 않도록 `try-catch`로 로그만 남기고 다음 결제로 넘어가게 처리합니다.
4. **단독 검증**
   - `ReceiptMatchingServiceTest`에서 `SampleData.createMockLlmClient()`를 넣어 `payment.isMatched() == true`와 `payment.getItems()` 개수를 검증합니다.

### 완료 기준 (Done)
- [ ] `MockLlmClient` 기준으로 영수증 파일이 있는 `Payment`만 매칭(`matched = true`)됨
- [ ] LLM 응답에 ```` ```json ```` 코드블록이나 공백이 섞여도 `ReceiptItem` 리스트로 파싱됨
- [ ] 영수증 파일이 없거나 JSON 형식이 깨져도 전체 매칭이 중단되지 않음

---

## 3. 팀원 C — 기능 2·3: 소액 결제 · 정기 구독 패턴 분석

**담당 파일**
- 코드: `src/service/SmallPaymentAnalyzer.java` (신규), `src/service/SubscriptionAnalyzer.java` (신규)
- 테스트: `tests/service/AnalyzerTest.java` (신규)

### 단계별 진행 순서
1. **테스트 환경부터 바로 세팅**
   - `SampleData.createMatchedPayments()`로 3개월 치 샘플 `List<Payment>`를, `SampleData.createMockLlmClient()`로 가짜 LLM을 받아와 시작합니다.
2. **`SmallPaymentAnalyzer` 구현 (기능 2)**
   - 생성자에서 `long smallThreshold`(예: `10000`)와 `LlmClient`를 받습니다.
   - `filterSmallPayments(List<Payment> payments)`: `totalAmount <= smallThreshold`인 결제만 추립니다.
   - 추려낸 결제의 **총 건수, 총액, 매칭된 품목의 카테고리별 합계**를 자바 코드로 먼저 정확히 계산합니다. (숫자 계산은 LLM에 맡기지 말고 자바가 직접 합니다.)
   - 계산된 요약 데이터를 `llmClient.ask(...)`에 넘겨 **소비 패턴 분석 코멘트**를 받아옵니다.
3. **`SubscriptionAnalyzer` 구현 (기능 3)**
   - 3개월 치 `List<Payment>`를 가맹점(`merchant`) 기준으로 묶습니다.
   - **2회 이상 등장하고, 결제 금액이 같거나(또는 유사하고) 월별로 반복되는 가맹점**을 정기 구독 후보로 추출합니다.
   - 추출된 후보 목록과 예상 월 고정 지출을 `llmClient.ask(...)`에 넘겨 **구독 관리 요약**을 받아옵니다.
4. **단독 검증**
   - `AnalyzerTest`에서 `SampleData.createMatchedPayments()`를 넣었을 때 소액 결제 건수/합계와 구독 후보(`유튜브 프리미엄`, `네이버페이 멤버십`)가 정확히 잡히는지 확인합니다.

### 완료 기준 (Done)
- [ ] 소액 결제 필터링 및 카테고리별 합계가 정확함
- [ ] 3개월 데이터에서 반복 결제(정기 구독) 후보와 월 고정 지출이 정확히 추출됨
- [ ] `MockLlmClient`를 통해 분석 리포트 문자열이 정상 반환됨

---

## 4. 팀원 D — Swing GUI · 비동기 처리 · 최종 통합

**담당 파일**
- 코드: `src/ui/MainFrame.java` (신규)

### 단계별 진행 순서
1. **화면 레이아웃 구성 (MVC의 View)**
   - **상단 패널**: CSV 파일 선택 버튼, 영수증 폴더 선택 버튼, **[분석 실행]** 버튼, 진행 상태 라벨(`"대기 중"` / `"분석 중..."`)
   - **중앙 상단 테이블 (`JTable`)**: 결제 내역 목록 (결제번호, 일시, 가맹점, 금액, 매칭 여부)
   - **중앙 하단 테이블 (`JTable`)**: 상단 테이블에서 특정 행을 클릭하면 해당 결제의 `payment.getItems()`(품목명, 단가, 수량, 카테고리, 합계) 표시
   - **우측 또는 하단 탭/패널**: 소액 결제 분석 결과 & 정기 구독 분석 결과 텍스트 영역(`JTextArea`)
2. **더미 데이터로 UI 먼저 완성**
   - 통합 전까지는 `[분석 실행]` 버튼을 눌렀을 때 `SampleData.createMatchedPayments()`를 테이블에 띄워 모든 화면 요소가 정상 작동하는지 확인합니다.
3. **`SwingWorker` 비동기 틀 미리 구현**
   - 버튼 클릭 이벤트 안에 바로 로직을 넣지 말고 **`SwingWorker`를 사용**합니다.
   ```java
   runButton.setEnabled(false);
   statusLabel.setText("영수증 매칭 및 분석 중...");

   new SwingWorker<Void, Void>() {
       @Override
       protected Void doInBackground() throws Exception {
           // 통합 전: Thread.sleep(1000) + SampleData 사용
           // 통합 후: CsvPaymentParser -> ReceiptMatchingService -> Analyzer 호출
           return null;
       }
       @Override
       protected void done() {
           // 테이블 및 분석 결과 텍스트 갱신 (EDT 스레드에서 안전하게 실행됨)
           runButton.setEnabled(true);
           statusLabel.setText("완료");
       }
   }.execute();
   ```
4. **통합일 연결**
   - A·B·C의 클래스가 모이면 `doInBackground()` 내부의 더미 호출만 실제 클래스 호출로 교체합니다.

### 완료 기준 (Done)
- [ ] 결제 행 클릭 시 하단 테이블에 세부 품목이 즉시 표시됨
- [ ] `SwingWorker` 실행 중에도 창 이동·스크롤이 멈추지 않고 버튼 중복 클릭이 방지됨
- [ ] `doInBackground()`에서 예외 발생 시 `done()`의 `get()`에서 잡아 사용자에게 오류 알림 창(`JOptionPane`)을 띄움

---

## 5. 통합 체크리스트 (다음 주 수요일 목표)

1. **팀원 A의 `LocalLlmClient` ↔ 팀원 B·C 연결**: `SampleData.createMockLlmClient()` 대신 `new LocalLlmClient(...)` 주입
2. **팀원 D의 `MainFrame` ↔ 팀원 A·B·C 연결**: `doInBackground()` 안에서 순서대로 호출
   - `CsvPaymentParser.parse(csvPath)` → `ReceiptMatchingService.matchReceipts(payments, receiptDir)` → `SmallPaymentAnalyzer` / `SubscriptionAnalyzer`
3. **실제 로컬 LLM E2E 테스트**: 3개월 치 전체 CSV + 영수증 파일을 넣고 끝까지 실행되는지 확인 및 프롬프트/예외 보완
