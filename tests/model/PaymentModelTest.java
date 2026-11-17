package model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 샘플 데이터를 이용해 Payment 및 ReceiptItem 동작을 검증하는 테스트. */
public final class PaymentModelTest {
    private static int passed;

    public static void main(String[] args) {
        // 1. 샘플 결제 데이터 생성 (매칭 전)
        Payment cafe = new Payment("TX-20260901-01", LocalDateTime.of(2026, 9, 1, 13, 20), "스타벅스", 9500);
        Payment mart = new Payment("TX-20260905-02", LocalDateTime.of(2026, 9, 5, 19, 10), "GS25", 6500);
        Payment sub = new Payment("TX-20260910-03", LocalDateTime.of(2026, 9, 10, 9, 0), "유튜브 프리미엄", 14900);

        check(!cafe.isMatched() && cafe.getItems().isEmpty(), "초기 Payment는 미매칭 상태이며 품목이 비어 있음");

        // 2. 샘플 영수증 세부 품목 생성 및 금액 계산 확인
        ReceiptItem americano = new ReceiptItem("아이스 아메리카노", 4500, 1, "카페/음료");
        ReceiptItem cake = new ReceiptItem("치즈케이크", 5000, 1, "디저트");
        check(americano.getAmount() == 4500 && cake.getAmount() == 5000, "ReceiptItem 단가 × 수량 계산");

        ReceiptItem gimbap = new ReceiptItem("참치마요 삼각김밥", 1500, 2, "간편식");
        ReceiptItem water = new ReceiptItem("생수 500ml", 1000, 1, "음료");
        ReceiptItem snack = new ReceiptItem("초코바", 2500, 1, "간식");
        check(gimbap.getAmount() == 3000, "수량이 2개 이상인 품목 금액 계산");

        // 3. 결제 건에 영수증 품목 매칭
        cafe.attachItems(Arrays.asList(americano, cake));
        mart.attachItems(Arrays.asList(gimbap, water, snack));

        check(cafe.isMatched() && cafe.getItems().size() == 2, "카페 결제 2개 품목 매칭 완료");
        check(mart.isMatched() && mart.getItems().size() == 3, "편의점 결제 3개 품목 매칭 완료");
        check(!sub.isMatched(), "영수증을 붙이지 않은 결제는 미매칭 유지");

        // 4. 불변 리스트 반환 확인
        boolean blocked = false;
        try {
            cafe.getItems().add(water);
        } catch (UnsupportedOperationException expected) {
            blocked = true;
        }
        check(blocked, "getItems()는 외부에서 직접 수정할 수 없음");

        // 5. 잘못된 입력 예외 검증
        expectInvalid(() -> new ReceiptItem("", 1000, 1, "기타"), "빈 품목명 거부");
        expectInvalid(() -> new ReceiptItem("커피", 0, 1, "카페"), "0원 이하 단가 거부");
        expectInvalid(() -> new ReceiptItem("커피", 1000, 0, "카페"), "0 이하 수량 거부");
        expectInvalid(() -> new Payment("TX-1", null, "가게", 1000), "결제 일시 null 거부");
        expectInvalid(() -> new Payment("TX-1", LocalDateTime.now(), "가게", -500), "음수 결제 금액 거부");
        expectInvalid(() -> sub.attachItems(Collections.emptyList()), "빈 품목 리스트 매칭 거부");

        // 6. 샘플 데이터 출력 확인
        System.out.println("\n=== 샘플 결제 내역 확인 ===");
        for (Payment payment : Arrays.asList(cafe, mart, sub)) {
            System.out.println(payment);
            for (ReceiptItem item : payment.getItems()) {
                System.out.println("  - " + item);
            }
        }
        System.out.println("\n통과: " + passed + "개 / 실패: 0개");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("실패: " + label);
        }
        passed++;
        System.out.println("[통과] " + label);
    }

    private static void expectInvalid(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("예외가 발생하지 않음: " + label);
        } catch (IllegalArgumentException expected) {
            check(true, label);
        }
    }
}
