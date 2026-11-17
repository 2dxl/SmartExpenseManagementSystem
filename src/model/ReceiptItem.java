package model;

/** 영수증에서 추출된 개별 구매 품목. */
public class ReceiptItem {
    private final String itemName;
    private final long unitPrice;
    private final int quantity;
    private final String category;

    public ReceiptItem(String itemName, long unitPrice, int quantity, String category) {
        this.itemName = requireText(itemName, "품목명");
        if (unitPrice <= 0) {
            throw new IllegalArgumentException("단가는 0보다 커야 합니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
        }
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.category = requireText(category, "카테고리");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
        return value.trim();
    }

    public String getItemName() {
        return itemName;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getCategory() {
        return category;
    }

    public long getAmount() {
        return Math.multiplyExact(unitPrice, (long) quantity);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) | %,d원 × %d = %,d원",
                itemName, category, unitPrice, quantity, getAmount());
    }
}
