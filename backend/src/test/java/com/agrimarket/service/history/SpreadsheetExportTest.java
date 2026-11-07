package com.agrimarket.service.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpreadsheetExportTest {

    @Test
    void csvQuotesSpecialCharactersAndNeutralisesFormulas() {
        assertThat(SpreadsheetExport.csvCell("a,b")).isEqualTo("\"a,b\"");
        assertThat(SpreadsheetExport.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(SpreadsheetExport.csvCell("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(SpreadsheetExport.csvCell(new BigDecimal("-5.00"))).isEqualTo("-5.00");
        assertThat(SpreadsheetExport.csvCell(null)).isEmpty();

        byte[] csv = SpreadsheetExport.csv(List.of("A", "B"), List.of(Arrays.asList(1, null)));
        assertThat(new String(csv, StandardCharsets.UTF_8)).isEqualTo("﻿A,B\r\n1,\r\n");
    }

    @Test
    void columnNamesFollowExcelLettering() {
        assertThat(SpreadsheetExport.columnName(0)).isEqualTo("A");
        assertThat(SpreadsheetExport.columnName(25)).isEqualTo("Z");
        assertThat(SpreadsheetExport.columnName(26)).isEqualTo("AA");
        assertThat(SpreadsheetExport.columnName(27)).isEqualTo("AB");
    }
}
