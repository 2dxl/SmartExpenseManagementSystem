package parser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Payment;

/**
 * 카드사 결제 내역 CSV 파일을 읽어 Payment 목록으로 변환하는 파서.
 *
 * <p>지원 형식:
 * <ul>
 *   <li>NH농협카드 국내승인내역 CSV (상단 요약, 멀티라인 헤더, 페이지 구분 문구, 하단 소계, 취소 건 포함)</li>
 *   <li>간소화 카드사 CSV ({@code 승인번호,이용일시,가맹점명,이용금액,결제구분,카드명,승인상태})</li>
 *   <li>기본 4컬럼 CSV ({@code transactionId,dateTime,merchant,totalAmount})</li>
 * </ul>
 */
public final class CsvPaymentParser {
    public static final String CARD_HEADER = "승인번호,이용일시,가맹점명,이용금액,결제구분,카드명,승인상태";
    public static final String HEADER = "transactionId,dateTime,merchant,totalAmount";

    private static final DateTimeFormatter SLASH_DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter DASH_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DOT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    private enum Format {
        NH_DOMESTIC,
        CARD_7COL,
        LEGACY_4COL
    }

    private static final class CsvRecord {
        final int startLine;
        final List<String> fields;

        CsvRecord(int startLine, List<String> fields) {
            this.startLine = startLine;
            this.fields = fields;
        }
    }

    private static final class HeaderLayout {
        final Format format;
        final int headerRecordIndex;
        final int expectedColumns;
        final int idCol;
        final int dateTimeCol;
        final int merchantCol;
        final int amountCol;
        final int cancelCol;

        HeaderLayout(Format format, int headerRecordIndex, int expectedColumns,
                int idCol, int dateTimeCol, int merchantCol, int amountCol, int cancelCol) {
            this.format = format;
            this.headerRecordIndex = headerRecordIndex;
            this.expectedColumns = expectedColumns;
            this.idCol = idCol;
            this.dateTimeCol = dateTimeCol;
            this.merchantCol = merchantCol;
            this.amountCol = amountCol;
            this.cancelCol = cancelCol;
        }
    }

    public List<Payment> parse(Path csvFile) throws IOException {
        if (csvFile == null) {
            throw new IllegalArgumentException("CSV 파일 경로는 null일 수 없습니다.");
        }
        String content = readFileText(csvFile);
        if (content.trim().isEmpty()) {
            throw new IOException("CSV 파일이 비어 있습니다: " + csvFile);
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        List<CsvRecord> records = parseCsvRecords(content);
        HeaderLayout layout = detectHeader(records);
        if (layout == null) {
            throw new IOException("지원하는 CSV 헤더를 찾을 수 없습니다: " + csvFile);
        }

        List<Payment> payments = new ArrayList<>();
        for (int i = layout.headerRecordIndex + 1; i < records.size(); i++) {
            CsvRecord record = records.get(i);
            if (isBlankRecord(record.fields)) {
                continue;
            }
            if (layout.format == Format.NH_DOMESTIC && isNhNonDataRecord(record.fields, layout)) {
                continue;
            }
            if (record.fields.size() != layout.expectedColumns) {
                throw new IOException(record.startLine + "행: 열 개수는 " + layout.expectedColumns + "개여야 합니다.");
            }
            if (layout.cancelCol >= 0) {
                String status = record.fields.get(layout.cancelCol).trim();
                if (status.contains("취소")) {
                    continue;
                }
            }
            try {
                String id = record.fields.get(layout.idCol).trim();
                LocalDateTime dateTime = parseDateTime(record.fields.get(layout.dateTimeCol).trim());
                String merchant = record.fields.get(layout.merchantCol).trim();
                long totalAmount = parseAmount(record.fields.get(layout.amountCol).trim());
                payments.add(new Payment(id, dateTime, merchant, totalAmount));
            } catch (RuntimeException e) {
                throw new IOException(record.startLine + "행 파싱 오류: " + e.getMessage(), e);
            }
        }
        return payments;
    }

    private static String readFileText(Path csvFile) throws IOException {
        try {
            return Files.readString(csvFile, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return Files.readString(csvFile, Charset.forName("MS949"));
        }
    }

    private static HeaderLayout detectHeader(List<CsvRecord> records) {
        for (int i = 0; i < records.size(); i++) {
            List<String> fields = records.get(i).fields;
            if (isBlankRecord(fields)) {
                continue;
            }

            Map<String, Integer> colIndex = new HashMap<>();
            for (int col = 0; col < fields.size(); col++) {
                String normalized = normalizeHeader(fields.get(col));
                if (!normalized.isEmpty()) {
                    colIndex.putIfAbsent(normalized, col);
                }
            }

            // 1) NH농협카드 국내승인내역 형식
            if (colIndex.containsKey("거래일자")
                    && colIndex.containsKey("승인번호")
                    && colIndex.containsKey("거래금액")
                    && colIndex.containsKey("가맹점명")) {
                int cancelCol = colIndex.getOrDefault("취소여부", -1);
                return new HeaderLayout(
                        Format.NH_DOMESTIC,
                        i,
                        fields.size(),
                        colIndex.get("승인번호"),
                        colIndex.get("거래일자"),
                        colIndex.get("가맹점명"),
                        colIndex.get("거래금액"),
                        cancelCol);
            }

            // 2) 간소화 카드사 7컬럼 형식
            if (fields.size() == 7
                    && colIndex.containsKey("승인번호")
                    && colIndex.containsKey("이용일시")
                    && colIndex.containsKey("가맹점명")
                    && colIndex.containsKey("이용금액")) {
                int statusCol = colIndex.getOrDefault("승인상태", -1);
                return new HeaderLayout(
                        Format.CARD_7COL,
                        i,
                        7,
                        colIndex.get("승인번호"),
                        colIndex.get("이용일시"),
                        colIndex.get("가맹점명"),
                        colIndex.get("이용금액"),
                        statusCol);
            }

            // 3) 기본 4컬럼 형식
            if (fields.size() == 4
                    && "transactionId".equals(fields.get(0).trim())
                    && "dateTime".equals(fields.get(1).trim())
                    && "merchant".equals(fields.get(2).trim())
                    && "totalAmount".equals(fields.get(3).trim())) {
                return new HeaderLayout(Format.LEGACY_4COL, i, 4, 0, 1, 2, 3, -1);
            }
        }
        return null;
    }

    /** NH농협카드 CSV 중간/하단에 반복 삽입되는 페이지 구분선, 안내문, 제목, 소계 행을 판별합니다. */
    private static boolean isNhNonDataRecord(List<String> fields, HeaderLayout layout) {
        String first = fields.get(0).trim();
        if (first.startsWith("·")
                || "국내 승인내역".equals(first)
                || "건수".equals(first)
                || "카드번호".equals(normalizeHeader(first))) {
            return true;
        }
        return fields.size() < layout.expectedColumns;
    }

    private static boolean isBlankRecord(List<String> fields) {
        for (String field : fields) {
            if (!field.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeHeader(String raw) {
        return raw.replaceAll("\\s+", "");
    }

    private static LocalDateTime parseDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw, SLASH_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw, DASH_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw, DOT_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        return LocalDateTime.parse(raw);
    }

    private static long parseAmount(String raw) {
        String normalized = raw.replace(",", "").replace("원", "").trim();
        return Long.parseLong(normalized);
    }

    /** RFC 4180 규칙에 따라 큰따옴표 내부 줄바꿈과 쉼표를 포함한 CSV 레코드 목록을 파싱합니다. */
    private static List<CsvRecord> parseCsvRecords(String content) throws IOException {
        List<CsvRecord> records = new ArrayList<>();
        List<String> currentFields = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();

        boolean inQuotes = false;
        int currentLine = 1;
        int recordStartLine = 1;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        currentCell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\r') {
                        if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                            i++;
                        }
                        currentCell.append('\n');
                        currentLine++;
                    } else if (c == '\n') {
                        currentCell.append('\n');
                        currentLine++;
                    } else {
                        currentCell.append(c);
                    }
                }
            } else {
                if (c == '"' && currentCell.length() == 0) {
                    inQuotes = true;
                } else if (c == ',') {
                    currentFields.add(currentCell.toString());
                    currentCell.setLength(0);
                } else if (c == '\r' || c == '\n') {
                    if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentFields.add(currentCell.toString());
                    records.add(new CsvRecord(recordStartLine, currentFields));
                    currentFields = new ArrayList<>();
                    currentCell.setLength(0);
                    currentLine++;
                    recordStartLine = currentLine;
                } else {
                    currentCell.append(c);
                }
            }
        }

        if (inQuotes) {
            throw new IOException(recordStartLine + "행: 닫히지 않은 큰따옴표가 있습니다.");
        }
        if (currentCell.length() > 0 || !currentFields.isEmpty()) {
            currentFields.add(currentCell.toString());
            records.add(new CsvRecord(recordStartLine, currentFields));
        }
        return records;
    }
}
