package com.company.procurement.common.export;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvExportUtils {

    private CsvExportUtils() {
    }

    public static byte[] toCsvBytes(List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(escape(row.get(i)));
            }
            builder.append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }
}
