package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 카드사 결제 내역 1건과 매칭된 세부 품목 목록을 보관하는 도메인 클래스. */
public class Payment {
    private final String transactionId;
    private final LocalDateTime dateTime;
    private final String merchant;
    private final long totalAmount;
    private boolean matched;
    private final List<ReceiptItem> items;

    public Payment(String transactionId, LocalDateTime dateTime, String merchant, long totalAmount) {
        this.transactionId = requireText(transactionId, "결제 고유번호");
        if (dateTime == null) {
            throw new IllegalArgumentException("결제 일시는 null일 수 없습니다.");
        }
        this.dateTime = dateTime;
        this.merchant = requireText(merchant, "가맹점명");
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("총 결제 금액은 0보다 커야 합니다.");
        }
        this.totalAmount = totalAmount;
        this.matched = false;
        this.items = new ArrayList<>();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
        return value.trim();
    }

    /** 영수증에서 추출한 세부 품목 목록을 연결하고 매칭 상태를 완료로 전환합니다. */
    public void attachItems(List<ReceiptItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("매칭할 세부 품목이 1개 이상 필요합니다.");
        }
        for (ReceiptItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("세부 품목 목록에 null이 포함될 수 없습니다.");
            }
        }
        this.items.clear();
        this.items.addAll(items);
        this.matched = true;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getMerchant() {
        return merchant;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public boolean isMatched() {
        return matched;
    }

    public List<ReceiptItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | %,d원 | 매칭: %s (%d개 품목)",
                transactionId, dateTime, merchant, totalAmount,
                matched ? "완료" : "미완료", items.size());
    }
}
