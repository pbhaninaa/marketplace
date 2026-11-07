package com.agrimarket.service.history;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal CSV and Excel (.xlsx, Office Open XML) writers for history reports.
 *
 * <p>The .xlsx is a single-sheet workbook with inline strings and numeric cells — enough for Excel,
 * LibreOffice and Google Sheets — so no spreadsheet library is needed.
 */
public final class SpreadsheetExport {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private SpreadsheetExport() {}

    public enum Format {
        CSV("text/csv; charset=UTF-8", "csv"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

        public final String contentType;
        public final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public static Format parse(String raw) {
            if (raw == null || raw.isBlank()) return CSV;
            String v = raw.trim().toLowerCase();
            return v.equals("xlsx") || v.equals("excel") ? XLSX : CSV;
        }
    }

    public static byte[] write(Format format, String sheetName, List<String> headers, List<List<Object>> rows) {
        return format == Format.XLSX ? xlsx(sheetName, headers, rows) : csv(headers, rows);
    }

    // ---------------------------------------------------------------- CSV

    public static byte[] csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel opens UTF-8 correctly
        appendCsvLine(sb, headers.stream().map(h -> (Object) h).toList());
        for (List<Object> row : rows) {
            appendCsvLine(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvLine(StringBuilder sb, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String csvCell(Object value) {
        if (value == null) return "";
        String s = value instanceof Instant t ? TS.format(t) : value.toString();
        if (!(value instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s; // neutralise spreadsheet formula injection
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ---------------------------------------------------------------- XLSX

    public static byte[] xlsx(String sheetName, List<String> headers, List<List<Object>> rows) {
        String safeSheet = sheetName == null || sheetName.isBlank()
                ? "Report"
                : sheetName.replaceAll("[\\\\/?*\\[\\]:]", " ").substring(0, Math.min(31, sheetName.length()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""");
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""".formatted(xml(safeSheet)));
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            // Style 1 = bold header.
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                    <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
                    </styleSheet>""");

            StringBuilder sheet = new StringBuilder(64 * (rows.size() + 1));
            sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                    .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                    .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
                    .append("<sheetData>");
            appendXlsxRow(sheet, 1, headers.stream().map(h -> (Object) h).toList(), true);
            int r = 2;
            for (List<Object> row : rows) {
                appendXlsxRow(sheet, r++, row, false);
            }
            sheet.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void appendXlsxRow(StringBuilder sb, int rowNum, List<Object> cells, boolean header) {
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            Object v = cells.get(c);
            if (v == null) continue;
            String ref = columnName(c) + rowNum;
            String style = header ? " s=\"1\"" : "";
            if (v instanceof Number n && !header) {
                String num = n instanceof BigDecimal bd ? bd.toPlainString() : n.toString();
                sb.append("<c r=\"").append(ref).append("\"").append(style).append("><v>").append(num).append("</v></c>");
            } else {
                String s = v instanceof Instant t ? TS.format(t) : v.toString();
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(style)
                        .append("><is><t xml:space=\"preserve\">").append(xml(s)).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String xml(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    // Drop control characters that are illegal in XML 1.0.
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') out.append(ch);
                }
            }
        }
        return out.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
