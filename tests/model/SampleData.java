package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import llm.LlmClient;

/**
 * 병렬 개발 시 팀원 B(매칭), C(분석), D(UI)가 대기 없이 바로 사용할 수 있는 공용 더미 데이터/목(Mock) 제공 클래스.
 */
public final class SampleData {
    private SampleData() {
    }

    /** 소액 결제, 일반 결제, 정기 구독, 매칭 완료/미완료가 포함된 3개월 샘플 결제 목록을 반환합니다. */
    public static List<Payment> createMatchedPayments() {
        List<Payment> list = new ArrayList<>();

        Payment p1 = new Payment("TX-20260901-01", LocalDateTime.of(2026, 9, 1, 13, 20), "스타벅스", 9500);
        p1.attachItems(Arrays.asList(
                new ReceiptItem("아이스 아메리카노", 4500, 1, "카페/음료"),
                new ReceiptItem("치즈케이크", 5000, 1, "디저트")));
        list.add(p1);

        Payment p2 = new Payment("TX-20260905-02", LocalDateTime.of(2026, 9, 5, 19, 10), "GS25", 6500);
        p2.attachItems(Arrays.asList(
                new ReceiptItem("참치마요 삼각김밥", 1500, 2, "간편식"),
                new ReceiptItem("생수 500ml", 1000, 1, "음료"),
                new ReceiptItem("초코바", 2500, 1, "간식")));
        list.add(p2);

        // 정기 구독 (9~11월 3회 반복, 영수증 미매칭 상태)
        list.add(new Payment("TX-20260910-03", LocalDateTime.of(2026, 9, 10, 9, 0), "유튜브 프리미엄", 14900));
        list.add(new Payment("TX-20261010-02", LocalDateTime.of(2026, 10, 10, 9, 0), "유튜브 프리미엄", 14900));
        list.add(new Payment("TX-20261110-02", LocalDateTime.of(2026, 11, 10, 9, 0), "유튜브 프리미엄", 14900));

        // 소액 정기 구독 (9~11월 3회 반복)
        list.add(new Payment("TX-20260915-04", LocalDateTime.of(2026, 9, 15, 10, 0), "네이버페이 멤버십", 4900));
        list.add(new Payment("TX-20261015-04", LocalDateTime.of(2026, 10, 15, 10, 0), "네이버페이 멤버십", 4900));
        list.add(new Payment("TX-20261115-04", LocalDateTime.of(2026, 11, 15, 10, 0), "네이버페이 멤버십", 4900));

        Payment p3 = new Payment("TX-20261112-03", LocalDateTime.of(2026, 11, 12, 15, 40), "교보문고", 32000);
        p3.attachItems(Arrays.asList(
                new ReceiptItem("자바 프로그래밍 입문", 32000, 1, "도서")));
        list.add(p3);

        return list;
    }

    /** 실제 로컬 LLM 서버 없이도 팀원 B·C가 개발·테스트할 수 있는 가짜 LlmClient를 반환합니다. */
    public static LlmClient createMockLlmClient() {
        return (systemPrompt, userContent) -> {
            if (systemPrompt != null && systemPrompt.contains("품목")) {
                return "[{\"itemName\":\"아이스 아메리카노\",\"unitPrice\":4500,\"quantity\":1,\"category\":\"카페/음료\"}]";
            }
            return "[Mock LLM 요약] 소액 결제는 카페/간편식 비중이 높으며, 월 고정 구독료는 19,800원입니다.";
        };
    }
}
