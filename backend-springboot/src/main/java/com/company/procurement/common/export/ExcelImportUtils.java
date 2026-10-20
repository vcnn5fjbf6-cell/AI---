package com.company.procurement.common.export;

import com.company.procurement.common.exception.BizException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExcelImportUtils {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_ROWS = 5000;

    private ExcelImportUtils() {
    }

    public static List<List<String>> readFirstSheet(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("EXCEL_FILE_REQUIRED", "请上传 Excel 文件（.xlsx 或 .xls）");
        }
        try (InputStream inputStream = file.getInputStream()) {
            return readFirstSheet(file.getOriginalFilename(), file.getSize(), inputStream);
        } catch (IOException ex) {
            throw new BizException("EXCEL_READ_FAILED", "读取 Excel 文件失败");
        }
    }

    public static List<List<String>> readFirstSheet(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BizException("EXCEL_FILE_REQUIRED", "请上传 Excel 文件（.xlsx 或 .xls）");
        }
        return readFirstSheet(fileName, content.length, new ByteArrayInputStream(content));
    }

    private static List<List<String>> readFirstSheet(String fileName, long fileSize, InputStream inputStream) {
        validateExcelFile(fileName, fileSize);
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BizException("EXCEL_SHEET_EMPTY", "Excel 文件没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<List<String>> rows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum - sheet.getFirstRowNum() + 1 > MAX_ROWS) {
                throw new BizException("EXCEL_ROW_LIMIT_EXCEEDED", "Excel import supports at most " + MAX_ROWS + " rows");
            }
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rows.add(List.of());
                    continue;
                }
                List<String> values = new ArrayList<>();
                short lastCellNum = row.getLastCellNum();
                for (int columnIndex = 0; columnIndex < Math.max(0, lastCellNum); columnIndex++) {
                    values.add(formatter.formatCellValue(row.getCell(columnIndex)).trim());
                }
                rows.add(values);
            }
            return rows;
        } catch (IOException ex) {
            throw new BizException("EXCEL_READ_FAILED", "读取 Excel 文件失败");
        } catch (RuntimeException ex) {
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException("EXCEL_READ_FAILED", "读取 Excel 文件失败，请确认文件格式正确");
        }
    }

    private static void validateExcelFile(String fileName, long fileSize) {
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new BizException("EXCEL_FILE_TOO_LARGE", "Excel file must be less than 5MB");
        }
        if (!StringUtils.hasText(fileName)
            || !(fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx") || fileName.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new BizException("EXCEL_FILE_REQUIRED", "请上传 Excel 文件（.xlsx 或 .xls）");
        }
    }

    public static List<Map<String, String>> toHeaderMaps(List<List<String>> table) {
        if (table == null || table.isEmpty()) {
            return List.of();
        }
        List<String> headers = table.get(0).stream().map(ExcelImportUtils::normalizeHeader).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < table.size(); rowIndex++) {
            List<String> values = table.get(rowIndex);
            Map<String, String> row = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String header = headers.get(columnIndex);
                if (!StringUtils.hasText(header)) {
                    continue;
                }
                row.put(header, columnIndex < values.size() ? values.get(columnIndex).trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    public static String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String header = value.replace("\uFEFF", "").trim()
            .replaceAll("[（(]\\s*(?:可填|替换时必填|勿改|不可修改|请勿修改|系统生成|系统字段)[^）)]*[）)]", "");
        if (header.chars().allMatch(ch -> ch < 128)) {
            return header.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
        }
        return header.replace(" ", "");
    }
}
