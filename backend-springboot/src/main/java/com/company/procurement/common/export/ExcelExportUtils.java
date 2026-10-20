package com.company.procurement.common.export;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExcelExportUtils {

    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelExportUtils() {
    }

    public static byte[] toXlsxBytes(String sheetName, List<List<String>> rows) {
        return toXlsxBytes(sheetName, rows, WorkbookOptions.empty());
    }

    public static byte[] toXlsxBytes(String sheetName, List<List<String>> rows, WorkbookOptions options) {
        WorkbookOptions safeOptions = options == null ? WorkbookOptions.empty() : options;
        List<List<String>> safeRows = rows == null ? List.of() : rows;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle requiredHeaderStyle = workbook.createCellStyle();
            requiredHeaderStyle.cloneStyleFrom(headerStyle);
            Font requiredHeaderFont = workbook.createFont();
            requiredHeaderFont.setBold(true);
            requiredHeaderFont.setColor(IndexedColors.RED.getIndex());
            requiredHeaderStyle.setFont(requiredHeaderFont);
            CellStyle hyperlinkStyle = workbook.createCellStyle();
            Font hyperlinkFont = workbook.createFont();
            hyperlinkFont.setColor(IndexedColors.BLUE.getIndex());
            hyperlinkFont.setUnderline(Font.U_SINGLE);
            hyperlinkStyle.setFont(hyperlinkFont);
            CreationHelper creationHelper = workbook.getCreationHelper();
            Set<Integer> requiredColumns = new HashSet<>(safeOptions.requiredColumns());
            Set<Integer> hyperlinkColumns = new HashSet<>(safeOptions.hyperlinkColumns());

            for (int rowIndex = 0; rowIndex < safeRows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                List<String> values = safeRows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    Cell cell = row.createCell(columnIndex);
                    String cellValue = values.get(columnIndex) == null ? "" : values.get(columnIndex);
                    cell.setCellValue(cellValue);
                    if (rowIndex == 0) {
                        cell.setCellStyle(requiredColumns.contains(columnIndex) ? requiredHeaderStyle : headerStyle);
                    } else if (hyperlinkColumns.contains(columnIndex) && isHttpUrl(cellValue)) {
                        Hyperlink hyperlink = creationHelper.createHyperlink(HyperlinkType.URL);
                        hyperlink.setAddress(cellValue);
                        cell.setHyperlink(hyperlink);
                        cell.setCellStyle(hyperlinkStyle);
                    }
                }
            }
            int columnCount = safeRows.isEmpty() ? 0 : safeRows.get(0).size();
            if (safeOptions.freezeHeader() && !safeRows.isEmpty()) {
                sheet.createFreezePane(0, 1);
            }
            if (safeOptions.autoFilter() && columnCount > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columnCount - 1));
            }
            addDropdowns(workbook, sheet, safeOptions.dropdowns());
            addNotes(workbook, sheet, safeOptions.notes());
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                sheet.autoSizeColumn(columnIndex);
                int width = Math.min(Math.max(sheet.getColumnWidth(columnIndex), 12 * 256), 42 * 256);
                sheet.setColumnWidth(columnIndex, width);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("导出 Excel 失败", ex);
        }
    }

    private static void addDropdowns(Workbook workbook, Sheet sheet, List<DropdownRule> dropdowns) {
        if (dropdowns == null || dropdowns.isEmpty()) {
            return;
        }
        Sheet optionSheet = workbook.createSheet("__下拉选项");
        DataValidationHelper helper = sheet.getDataValidationHelper();
        int ruleIndex = 0;
        for (DropdownRule rule : dropdowns) {
            if (rule == null || rule.options().isEmpty()) {
                continue;
            }
            int optionColumn = ruleIndex++;
            for (int rowIndex = 0; rowIndex < rule.options().size(); rowIndex++) {
                Row row = optionSheet.getRow(rowIndex);
                if (row == null) {
                    row = optionSheet.createRow(rowIndex);
                }
                row.createCell(optionColumn).setCellValue(rule.options().get(rowIndex));
            }
            String columnName = CellReference.convertNumToColString(optionColumn);
            Name namedRange = workbook.createName();
            namedRange.setNameName("dropdown_options_" + optionColumn);
            namedRange.setRefersToFormula("'" + optionSheet.getSheetName() + "'!$" + columnName + "$1:$" + columnName + "$" + rule.options().size());
            DataValidationConstraint constraint = helper.createFormulaListConstraint(namedRange.getNameName());
            CellRangeAddressList addresses = new CellRangeAddressList(
                rule.firstRowIndex(),
                rule.lastRowIndex(),
                rule.columnIndex(),
                rule.columnIndex()
            );
            DataValidation validation = helper.createValidation(constraint, addresses);
            validation.setShowErrorBox(true);
            validation.setEmptyCellAllowed(true);
            validation.createErrorBox(
                defaultText(rule.errorTitle(), "请选择下拉值"),
                defaultText(rule.errorMessage(), "该列只能选择模板提供的值")
            );
            sheet.addValidationData(validation);
        }
        workbook.setSheetHidden(workbook.getSheetIndex(optionSheet), true);
    }

    private static void addNotes(Workbook workbook, Sheet sheet, List<CellNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        CreationHelper helper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        for (CellNote note : notes) {
            if (note == null || note.rowIndex() < 0 || note.columnIndex() < 0 || note.text() == null || note.text().isBlank()) {
                continue;
            }
            Row row = sheet.getRow(note.rowIndex());
            if (row == null) {
                row = sheet.createRow(note.rowIndex());
            }
            Cell cell = row.getCell(note.columnIndex());
            if (cell == null) {
                cell = row.createCell(note.columnIndex());
            }
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(note.columnIndex());
            anchor.setCol2(note.columnIndex() + 5);
            anchor.setRow1(note.rowIndex());
            anchor.setRow2(note.rowIndex() + 4);
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(helper.createRichTextString(note.text()));
            comment.setAuthor("system");
            cell.setCellComment(comment);
        }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record WorkbookOptions(
        List<DropdownRule> dropdowns,
        List<CellNote> notes,
        List<Integer> requiredColumns,
        List<Integer> hyperlinkColumns,
        boolean freezeHeader,
        boolean autoFilter
    ) {

        public WorkbookOptions {
            dropdowns = dropdowns == null ? List.of() : List.copyOf(dropdowns);
            notes = notes == null ? List.of() : List.copyOf(notes);
            requiredColumns = requiredColumns == null ? List.of() : List.copyOf(requiredColumns);
            hyperlinkColumns = hyperlinkColumns == null ? List.of() : List.copyOf(hyperlinkColumns);
        }

        public static WorkbookOptions empty() {
            return new WorkbookOptions(List.of(), List.of(), List.of(), List.of(), false, false);
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    public record DropdownRule(
        int columnIndex,
        int firstRowIndex,
        int lastRowIndex,
        List<String> options,
        String errorTitle,
        String errorMessage
    ) {

        public DropdownRule {
            options = options == null ? List.of() : List.copyOf(options);
        }

        public static DropdownRule of(int columnIndex, List<String> options) {
            return new DropdownRule(columnIndex, 1, 5000, options, "请选择下拉值", "该列只能选择模板提供的值");
        }
    }

    public record CellNote(int rowIndex, int columnIndex, String text) {
    }

    public static final class Builder {
        private final List<DropdownRule> dropdowns = new ArrayList<>();
        private final List<CellNote> notes = new ArrayList<>();
        private final List<Integer> requiredColumns = new ArrayList<>();
        private final List<Integer> hyperlinkColumns = new ArrayList<>();
        private boolean freezeHeader;
        private boolean autoFilter;

        private Builder() {
        }

        public Builder dropdown(int columnIndex, List<String> options) {
            dropdowns.add(DropdownRule.of(columnIndex, options));
            return this;
        }

        public Builder dropdown(DropdownRule rule) {
            dropdowns.add(rule);
            return this;
        }

        public Builder note(int rowIndex, int columnIndex, String text) {
            notes.add(new CellNote(rowIndex, columnIndex, text));
            return this;
        }

        public Builder requiredColumns(Integer... columnIndexes) {
            if (columnIndexes != null) {
                requiredColumns.addAll(List.of(columnIndexes));
            }
            return this;
        }

        public Builder hyperlinkColumns(Integer... columnIndexes) {
            if (columnIndexes != null) {
                hyperlinkColumns.addAll(List.of(columnIndexes));
            }
            return this;
        }

        public Builder freezeHeader(boolean freezeHeader) {
            this.freezeHeader = freezeHeader;
            return this;
        }

        public Builder autoFilter(boolean autoFilter) {
            this.autoFilter = autoFilter;
            return this;
        }

        public WorkbookOptions build() {
            return new WorkbookOptions(dropdowns, notes, requiredColumns, hyperlinkColumns, freezeHeader, autoFilter);
        }
    }
}
