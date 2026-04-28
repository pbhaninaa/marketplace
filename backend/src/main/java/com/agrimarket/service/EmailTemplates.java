package com.agrimarket.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class EmailTemplates {

    private EmailTemplates() {}

    public static String layout(String title, String heading, String intro, List<String> rows, String footer) {
        StringBuilder items = new StringBuilder();
        if (rows != null) {
            for (String r : rows) {
                if (r == null || r.isBlank()) continue;
                items.append("<tr>")
                        .append("<td style=\"padding:10px 12px;border-top:1px solid #E6E8EE;color:#111827;font-size:14px;\">")
                        .append(escapeHtml(r))
                        .append("</td></tr>");
            }
        }

        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                </head>
                <body style="margin:0;background:#F3F4F6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#111827;">
                  <div style="max-width:640px;margin:0 auto;padding:24px;">
                    <div style="padding:18px 20px;background:#111827;border-radius:14px;color:#fff;">
                      <div style="font-size:12px;letter-spacing:1.2px;opacity:.85;">AGRI MARKETPLACE</div>
                      <div style="font-size:22px;font-weight:800;margin-top:6px;">%s</div>
                    </div>

                    <div style="background:#ffffff;border-radius:14px;margin-top:14px;overflow:hidden;border:1px solid #E5E7EB;">
                      <div style="padding:18px 20px;">
                        <div style="font-size:16px;font-weight:700;margin-bottom:6px;">%s</div>
                        <div style="font-size:14px;line-height:1.55;color:#374151;">%s</div>
                      </div>

                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">
                        %s
                      </table>

                      <div style="padding:16px 20px;background:#F9FAFB;border-top:1px solid #E5E7EB;color:#6B7280;font-size:12px;line-height:1.5;">
                        %s
                      </div>
                    </div>

                    <div style="text-align:center;color:#9CA3AF;font-size:12px;margin-top:14px;">
                      Sent %s
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(heading == null ? "" : heading),
                escapeHtml(intro == null ? "" : intro),
                items.toString(),
                escapeHtml(footer == null ? "" : footer),
                escapeHtml(formatInstant(Instant.now())));
    }

    public static String simpleText(String heading, List<String> rows, String footer) {
        StringBuilder sb = new StringBuilder();
        if (heading != null && !heading.isBlank()) {
            sb.append(heading).append("\n\n");
        }
        if (rows != null) {
            for (String r : rows) {
                if (r == null || r.isBlank()) continue;
                sb.append("- ").append(r).append("\n");
            }
        }
        if (footer != null && !footer.isBlank()) {
            sb.append("\n").append(footer).append("\n");
        }
        return sb.toString();
    }

    private static String formatInstant(Instant i) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
        return f.format(i);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

