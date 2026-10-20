package com.company.procurement.common.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PdfExportUtils {

    public static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final Color TITLE_COLOR = new Color(15, 32, 61);
    private static final Color TITLE_BG = new Color(20, 45, 86);
    private static final Color HEADER_BG = new Color(42, 68, 110);
    private static final Color LABEL_BG = new Color(248, 251, 255);
    private static final Color ALT_ROW_BG = new Color(250, 252, 255);
    private static final Color HIGHLIGHT_ROW_BG = new Color(241, 250, 246);
    private static final Color HIGHLIGHT_CELL_BG = new Color(224, 244, 234);
    private static final Color HIGHLIGHT_TEXT = new Color(15, 118, 83);
    private static final Color METRIC_BG = new Color(247, 250, 255);
    private static final Color MUTED_TEXT = new Color(91, 106, 128);
    private static final Color BORDER = new Color(210, 222, 238);

    private PdfExportUtils() {
    }

    public static byte[] toPdfBytes(String title, List<PdfBlock> blocks) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BaseFont baseFont = loadChineseFont();
            Font titleFont = new Font(baseFont, 17, Font.BOLD, Color.WHITE);
            Font sectionFont = new Font(baseFont, 11, Font.BOLD, TITLE_COLOR);
            Font headerFont = new Font(baseFont, 8.5f, Font.BOLD, Color.WHITE);
            Font normalFont = new Font(baseFont, 8.5f, Font.NORMAL, Color.BLACK);
            Font labelFont = new Font(baseFont, 8.5f, Font.BOLD, TITLE_COLOR);
            Font highlightFont = new Font(baseFont, 8.5f, Font.BOLD, HIGHLIGHT_TEXT);
            Font metricValueFont = new Font(baseFont, 13, Font.BOLD, TITLE_COLOR);
            Font metricLabelFont = new Font(baseFont, 8, Font.NORMAL, MUTED_TEXT);

            Document document = new Document(PageSize.A4.rotate(), 26, 26, 30, 28);
            PdfWriter.getInstance(document, outputStream);
            document.open();
            addTitle(document, title, titleFont);

            for (PdfBlock block : safeBlocks(blocks)) {
                addSectionTitle(document, block.title(), sectionFont);
                if (block.metrics() != null && !block.metrics().isEmpty()) {
                    document.add(metricTable(block.metrics(), metricValueFont, metricLabelFont));
                    if ((block.keyValues() != null && !block.keyValues().isEmpty())
                        || (block.tables() != null && !block.tables().isEmpty())) {
                        document.add(spacer(5));
                    }
                }
                if (block.keyValues() != null && !block.keyValues().isEmpty()) {
                    document.add(keyValueTable(block.keyValues(), labelFont, normalFont));
                    if (block.tables() != null && !block.tables().isEmpty()) {
                        document.add(spacer(3));
                    }
                }
                if (block.tables() != null) {
                    for (int tableIndex = 0; tableIndex < block.tables().size(); tableIndex++) {
                        PdfTable table = block.tables().get(tableIndex);
                        if (table.title() != null && !table.title().isBlank()) {
                            Paragraph tableTitle = new Paragraph(table.title(), labelFont);
                            tableTitle.setSpacingBefore(4);
                            tableTitle.setSpacingAfter(4);
                            document.add(tableTitle);
                        }
                        document.add(dataTable(table, headerFont, normalFont, highlightFont));
                        if (tableIndex + 1 < block.tables().size()) {
                            document.add(spacer(6));
                        }
                    }
                }
            }
            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("生成 PDF 失败", ex);
        }
    }

    private static void addTitle(Document document, String title, Font titleFont) throws DocumentException {
        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(new Phrase(safe(title), titleFont));
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setBackgroundColor(TITLE_BG);
        titleCell.setPaddingTop(10);
        titleCell.setPaddingBottom(10);
        titleTable.addCell(titleCell);
        titleTable.setSpacingAfter(7);
        document.add(titleTable);
    }

    private static void addSectionTitle(Document document, String title, Font sectionFont) throws DocumentException {
        Paragraph section = new Paragraph(safe(title), sectionFont);
        section.setSpacingBefore(10);
        section.setSpacingAfter(6);
        document.add(section);
    }

    private static PdfPTable metricTable(List<PdfMetric> metrics, Font valueFont, Font labelFont) {
        int columns = Math.min(4, Math.max(1, metrics.size()));
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        for (PdfMetric metric : metrics) {
            table.addCell(metricCell(metric, valueFont, labelFont));
        }
        int remainder = metrics.size() % columns;
        if (remainder > 0) {
            for (int i = remainder; i < columns; i++) {
                table.addCell(metricCell(new PdfMetric("", "", ""), valueFont, labelFont));
            }
        }
        return table;
    }

    private static PdfPCell metricCell(PdfMetric metric, Font valueFont, Font labelFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(METRIC_BG);
        Paragraph value = new Paragraph(safe(metric.value()), valueFont);
        value.setSpacingAfter(3);
        Paragraph label = new Paragraph(safe(metric.label()), labelFont);
        cell.addElement(value);
        cell.addElement(label);
        String note = safe(metric.note());
        if (!note.isBlank()) {
            Paragraph noteParagraph = new Paragraph(note, labelFont);
            noteParagraph.setSpacingBefore(2);
            cell.addElement(noteParagraph);
        }
        return cell;
    }

    private static PdfPTable keyValueTable(List<List<String>> rows, Font labelFont, Font normalFont) {
        PdfPTable table = new PdfPTable(new float[] {1.2f, 2.2f, 1.2f, 2.2f});
        table.setWidthPercentage(100);
        for (int i = 0; i < rows.size(); i += 2) {
            List<String> left = safeRow(rows.get(i), 2);
            List<String> right = i + 1 < rows.size() ? safeRow(rows.get(i + 1), 2) : List.of("", "");
            table.addCell(cell(left.get(0), labelFont, LABEL_BG, Element.ALIGN_LEFT));
            table.addCell(cell(left.get(1), normalFont, Color.WHITE, Element.ALIGN_LEFT));
            table.addCell(cell(right.get(0), labelFont, LABEL_BG, Element.ALIGN_LEFT));
            table.addCell(cell(right.get(1), normalFont, Color.WHITE, Element.ALIGN_LEFT));
        }
        return table;
    }

    private static PdfPTable dataTable(PdfTable pdfTable,
                                       Font headerFont,
                                       Font normalFont,
                                       Font highlightFont) throws DocumentException {
        List<String> headers = pdfTable.headers() == null ? List.of() : pdfTable.headers();
        int columnCount = Math.max(1, headers.size());
        PdfPTable table = pdfTable.widths() != null && pdfTable.widths().length == columnCount
            ? new PdfPTable(pdfTable.widths())
            : new PdfPTable(columnCount);
        table.setWidthPercentage(100);
        for (String header : headers) {
            table.addCell(cell(header, headerFont, HEADER_BG, Element.ALIGN_CENTER));
        }
        List<List<String>> rows = pdfTable.rows() == null || pdfTable.rows().isEmpty()
            ? List.of(List.of("暂无数据"))
            : pdfTable.rows();
        Set<Integer> highlightedRows = new HashSet<>(pdfTable.highlightedRows() == null ? List.of() : pdfTable.highlightedRows());
        Set<Integer> highlightedColumns = new HashSet<>(pdfTable.highlightedColumns() == null ? List.of() : pdfTable.highlightedColumns());
        Set<String> highlightedCells = new HashSet<>(pdfTable.highlightedCells() == null ? List.of() : pdfTable.highlightedCells());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            List<String> safeRow = safeRow(row, columnCount);
            boolean highlightedRow = highlightedRows.contains(rowIndex);
            Color rowBg = highlightedRow ? HIGHLIGHT_ROW_BG : rowIndex % 2 == 0 ? Color.WHITE : ALT_ROW_BG;
            for (int columnIndex = 0; columnIndex < safeRow.size(); columnIndex++) {
                boolean highlightedCell = highlightedCells.contains(cellKey(rowIndex, columnIndex))
                    || highlightedRow && highlightedColumns.contains(columnIndex);
                Color background = highlightedCell ? HIGHLIGHT_CELL_BG : rowBg;
                Font font = highlightedCell ? highlightFont : normalFont;
                table.addCell(cell(safeRow.get(columnIndex), font, background, alignForHeader(headers, columnIndex)));
            }
        }
        return table;
    }

    private static int alignForHeader(List<String> headers, int columnIndex) {
        String header = columnIndex < headers.size() ? headers.get(columnIndex) : "";
        if (header.contains("单价")
            || header.contains("金额")
            || header.contains("价格")
            || header.contains("数量")
            || header.contains("合计")) {
            return Element.ALIGN_RIGHT;
        }
        if (header.contains("序号")
            || header.contains("单位")
            || header.contains("交期")
            || header.contains("状态")
            || header.contains("结果")
            || header.contains("轮次")) {
            return Element.ALIGN_CENTER;
        }
        return Element.ALIGN_LEFT;
    }

    public static String cellKey(int rowIndex, int columnIndex) {
        return rowIndex + ":" + columnIndex;
    }

    private static PdfPCell cell(String text, Font font, Color background, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setPadding(4);
        cell.setLeading(0, 1.1f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(background);
        return cell;
    }

    private static Paragraph spacer(float spacing) {
        Paragraph paragraph = new Paragraph("");
        paragraph.setSpacingAfter(spacing);
        return paragraph;
    }

    private static BaseFont loadChineseFont() throws IOException, DocumentException {
        for (String candidate : fontCandidates()) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String pathText = candidate.contains(",") ? candidate.substring(0, candidate.indexOf(',')) : candidate;
            if (!Files.exists(Path.of(pathText))) {
                continue;
            }
            String actual = candidate;
            if (!candidate.contains(",") && candidate.toLowerCase().endsWith(".ttc")) {
                actual = candidate + ",0";
            }
            return BaseFont.createFont(actual, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
        throw new IllegalStateException("未找到可用于导出 PDF 的中文字体，请安装 Noto CJK 或设置 PDF_CJK_FONT_PATH");
    }

    private static List<String> fontCandidates() {
        List<String> candidates = new ArrayList<>();
        candidates.add(System.getenv("PDF_CJK_FONT_PATH"));
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0");
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf");
        candidates.add("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc,0");
        candidates.add("C:/Windows/Fonts/msyh.ttc,0");
        candidates.add("C:/Windows/Fonts/simsun.ttc,0");
        candidates.add("C:/Windows/Fonts/simhei.ttf");
        return candidates;
    }

    private static List<PdfBlock> safeBlocks(List<PdfBlock> blocks) {
        return blocks == null ? List.of() : blocks;
    }

    private static List<String> safeRow(List<String> row, int size) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(row != null && i < row.size() ? safe(row.get(i)) : "");
        }
        return values;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record PdfBlock(String title, List<PdfMetric> metrics, List<List<String>> keyValues, List<PdfTable> tables) {

        public PdfBlock(String title, List<List<String>> keyValues, List<PdfTable> tables) {
            this(title, List.of(), keyValues, tables);
        }
    }

    public record PdfMetric(String label, String value, String note) {
    }

    public record PdfTable(String title,
                           List<String> headers,
                           List<List<String>> rows,
                           float[] widths,
                           List<Integer> highlightedRows,
                           List<Integer> highlightedColumns,
                           List<String> highlightedCells) {

        public PdfTable(String title, List<String> headers, List<List<String>> rows, float[] widths) {
            this(title, headers, rows, widths, List.of(), List.of(), List.of());
        }

        public PdfTable(String title,
                        List<String> headers,
                        List<List<String>> rows,
                        float[] widths,
                        List<Integer> highlightedRows,
                        List<Integer> highlightedColumns) {
            this(title, headers, rows, widths, highlightedRows, highlightedColumns, List.of());
        }
    }
}
