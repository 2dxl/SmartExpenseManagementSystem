package parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Payment;

/** 카드사 결제 내역 CSV 파일을 읽어 Payment 목록으로 변환하는 파서. */
public final class CsvPaymentParser {
    public static final String HEADER = "transactionId,dateTime,merchant,totalAmount";

    public List<Payment> parse(Path csvFile) throws IOException {
        List<Payment> payments = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("CSV 파일이 비어 있습니다: " + csvFile);
            }
            if (header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (!HEADER.equals(header.trim())) {
                throw new IOException("헤더 형식이 올바르지 않습니다. 기대값: " + HEADER);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length != 4) {
                    throw new IOException(lineNumber + "행: 열 개수는 4개여야 합니다.");
                }
                try {
                    String id = cols[0].trim();
                    LocalDateTime dateTime = LocalDateTime.parse(cols[1].trim());
                    String merchant = cols[2].trim();
                    long totalAmount = Long.parseLong(cols[3].trim());
                    payments.add(new Payment(id, dateTime, merchant, totalAmount));
                } catch (RuntimeException e) {
                    throw new IOException(lineNumber + "행 파싱 오류: " + e.getMessage(), e);
                }
            }
        }
        return payments;
    }
}
