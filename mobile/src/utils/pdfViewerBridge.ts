export type OpenPdfMessage = {
  type: 'OPEN_PDF';
  title?: string;
  base64: string;
};

export function isOpenPdfMessage(raw: unknown): raw is OpenPdfMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as OpenPdfMessage;
  return msg.type === 'OPEN_PDF' && typeof msg.base64 === 'string' && msg.base64.length > 0;
}

export function buildPdfViewerHtml(base64: string): string {
  const safe = base64.replace(/"/g, '');
  return `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0" />
    <style>
      html, body { margin: 0; padding: 0; height: 100%; background: #525659; }
      embed { width: 100%; height: 100%; border: 0; }
    </style>
  </head>
  <body>
    <embed type="application/pdf" src="data:application/pdf;base64,${safe}" />
  </body>
</html>`;
}
