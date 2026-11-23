package app;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import llm.LlmClient;
import llm.LocalLlmClient;
import model.Payment;
import model.ReceiptItem;
import parser.CsvPaymentParser;

/**
 * 3개월 결제 CSV와 영수증 파일을 읽어 매칭·소액·구독 분석을 실행하는 진입점.
 */
public final class Main {
    private static final long SMALL_PAYMENT_THRESHOLD = 10000;

    public static void main(String[] args) {
        Path csvPath = Paths.get(args.length > 0 ? args[0] : "data/국내승인내역.csv");
        Path receiptDir = Paths.get(args.length > 1 ? args[1] : "data/receipts");

        System.out.println("=== SmartExpenseManagementSystem (3개월 데이터) ===");
        try {
            // 1단계: 카드사 결제 CSV 로드
            CsvPaymentParser parser = new CsvPaymentParser();
            List<Payment> payments = parser.parse(csvPath);
            System.out.println("\n[1단계] 결제 내역 로드 완료: " + payments.size() + "건 (" + csvPath + ")");

            // 2단계: 영수증 파일 매칭 (LocalLlmClient 연동 준비, 매칭 로직은 추후 구현)
            LlmClient llmClient = LocalLlmClient.forSunmoonLlmFromConfig();
            matchReceiptFiles(payments, receiptDir, llmClient);
            long matchedCount = payments.stream().filter(Payment::isMatched).count();
            System.out.println("[2단계] 영수증 매칭 완료: " + matchedCount + "/" + payments.size() + "건");

            for (Payment payment : payments) {
                System.out.println("  " + payment);
                for (ReceiptItem item : payment.getItems()) {
                    System.out.println("    - " + item);
                }
            }

            // 3단계: 3개월 월별 요약 + 소액 결제 + 정기 구독 패턴 분석
            printMonthlySummary(payments);
            printSmallPaymentSummary(payments);
            printSubscriptionSummary(payments);
        } catch (IOException e) {
            System.out.println("파일 처리 오류: " + e.getMessage());
        }
    }

    private static void matchReceiptFiles(List<Payment> payments, Path receiptDir, LlmClient llmClient) {
        // TODO: 추후 ReceiptMatchingService(llmClient) 연동
    }

    private static void printMonthlySummary(List<Payment> payments) {
        Map<YearMonth, Long> monthlyTotals = new TreeMap<>();
        for (Payment p : payments) {
            monthlyTotals.merge(YearMonth.from(p.getDateTime()), p.getTotalAmount(), Long::sum);
        }
        System.out.println("\n[요약] 월별 총지출:");
        for (Map.Entry<YearMonth, Long> entry : monthlyTotals.entrySet()) {
            System.out.printf("  - %s: %,d원%n", entry.getKey(), entry.getValue());
        }
    }

    private static void printSmallPaymentSummary(List<Payment> payments) {
        long smallTotal = 0;
        int smallCount = 0;
        Map<String, Long> categoryTotals = new LinkedHashMap<>();

        for (Payment p : payments) {
            if (p.getTotalAmount() <= SMALL_PAYMENT_THRESHOLD) {
                smallCount++;
                smallTotal += p.getTotalAmount();
                for (ReceiptItem item : p.getItems()) {
                    categoryTotals.merge(item.getCategory(), item.getAmount(), Long::sum);
                }
            }
        }
        System.out.printf("%n[3-1단계] 소액 결제 분석 (기준: %,d원 이하): %d건 / 총 %,d원%n",
                SMALL_PAYMENT_THRESHOLD, smallCount, smallTotal);
        if (!categoryTotals.isEmpty()) {
            System.out.println("  매칭된 소액 품목 카테고리별 합계:");
            for (Map.Entry<String, Long> entry : categoryTotals.entrySet()) {
                System.out.printf("    · %s: %,d원%n", entry.getKey(), entry.getValue());
            }
        }
    }

    private static void printSubscriptionSummary(List<Payment> payments) {
        Map<String, List<Payment>> byMerchant = new LinkedHashMap<>();
        for (Payment p : payments) {
            byMerchant.computeIfAbsent(p.getMerchant(), k -> new ArrayList<>()).add(p);
        }

        long monthlyFixedTotal = 0;
        System.out.println("\n[3-2단계] 정기 구독 패턴 분석 (3개월 내 2회 이상 동일 금액 반복 결제):");
        for (Map.Entry<String, List<Payment>> entry : byMerchant.entrySet()) {
            List<Payment> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }
            long firstAmount = list.get(0).getTotalAmount();
            boolean sameAmount = list.stream().allMatch(p -> p.getTotalAmount() == firstAmount);
            if (sameAmount) {
                monthlyFixedTotal += firstAmount;
                System.out.printf("  - %s: %d회 결제 / 회당 %,d원%n",
                        entry.getKey(), list.size(), firstAmount);
            }
        }
        System.out.printf("  예상 월 고정 구독 지출: %,d원%n", monthlyFixedTotal);
    }
}
