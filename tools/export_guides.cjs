#!/usr/bin/env node
'use strict';

// Usage: node tools/export_guides.cjs /path/to/runtime/node_modules/markdown-it
// Requires Chromium and markdown-it; no portal access or credentials are needed.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const MarkdownIt = require(process.argv[2] ? path.resolve(process.argv[2]) : 'markdown-it');
const root = path.resolve(__dirname, '..');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'gdansk-guide-print-'));
const css = `
  @page {
    size: A4; margin: 17mm 17mm 19mm;
    @bottom-center { content: "Gdańsk Case Monitor  ·  " counter(page) " / " counter(pages); font-family: "DejaVu Sans", sans-serif; font-size: 8pt; color: #526477; }
  }
  * { box-sizing: border-box; }
  body { font-family: "DejaVu Sans", sans-serif; font-size: 10pt; line-height: 1.48; color: #202c3b; }
  h1 { font-size: 25pt; line-height: 1.18; color: #173d68; margin: 0 0 8mm; }
  h2 { font-size: 15pt; color: #173d68; margin: 8mm 0 3mm; border-bottom: 1px solid #d9e3ef; padding-bottom: 2mm; }
  h3 { font-size: 11pt; margin: 5mm 0 2mm; }
  h1, h2, h3 { break-after: avoid; }
  p { margin: 2.5mm 0; orphans: 3; widows: 3; }
  a { color: #215d96; text-decoration: none; }
  ul, ol { padding-left: 6mm; margin: 3mm 0; }
  li { margin: 1.3mm 0; }
  code { font-family: "DejaVu Sans Mono", monospace; font-size: 8.7pt; overflow-wrap: anywhere; }
  table { width: 100%; border-collapse: collapse; margin: 4mm 0; font-size: 9pt; }
  thead { display: table-header-group; }
  th { background: #eaf0f7; text-align: left; }
  th, td { border: 1px solid #cfdae7; padding: 2.3mm; vertical-align: top; }
  tr { break-inside: avoid; }
  figure { margin: 4mm 0; text-align: center; break-inside: avoid; }
  figure img { max-width: 100%; max-height: 157mm; width: auto; height: auto; }
  figcaption { font-size: 8pt; color: #526477; margin-top: 2mm; }
`;

for (const language of ['EN', 'RU']) {
  const md = new MarkdownIt({ html: false, linkify: true, typographer: false });
  const originalLink = md.renderer.rules.link_open || ((tokens, idx, options, env, renderer) => renderer.renderToken(tokens, idx, options));
  md.renderer.rules.link_open = (tokens, idx, options, env, renderer) => {
    const href = tokens[idx].attrGet('href');
    if (/^USER_GUIDE_(EN|RU)\.md$/.test(href || '')) tokens[idx].attrSet('href', pathToFileURL(path.join(root, 'docs', href.replace('.md', '.pdf'))).href);
    return originalLink(tokens, idx, options, env, renderer);
  };
  let illustrationCount = 0;
  md.renderer.rules.image = (tokens, idx) => {
    const token = tokens[idx];
    const src = token.attrGet('src');
    const asset = path.resolve(root, 'docs', src);
    const mime = { '.svg': 'image/svg+xml', '.jpg': 'image/jpeg' }[path.extname(asset)];
    if (!asset.startsWith(path.join(root, 'docs', 'assets') + path.sep) || !mime) throw new Error('Unexpected illustration: ' + src);
    const caption = md.utils.escapeHtml(token.content);
    illustrationCount++;
    return `<figure><img src="data:${mime};base64,${fs.readFileSync(asset).toString('base64')}" alt="${caption}"><figcaption>${caption}</figcaption></figure>`;
  };
  const source = path.join(root, 'docs', `USER_GUIDE_${language}.md`);
  // Figure is a block element; remove its generated Markdown paragraph wrapper.
  const body = md.render(fs.readFileSync(source, 'utf8')).replace(/<p>(<figure>[\s\S]*?<\/figure>)<\/p>/g, '$1');
  if (illustrationCount !== 5) throw new Error('Expected three schematic illustrations and two warning screenshots in ' + source);
  const html = path.join(temp, `USER_GUIDE_${language}.html`);
  fs.writeFileSync(html, `<!doctype html><html lang="${language.toLowerCase()}"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'"><title>Gdańsk Case Monitor — ${language === 'EN' ? 'User Guide' : 'Руководство пользователя'}</title><style>${css}</style></head><body>${body}</body></html>`);
  const pdf = path.join(root, 'docs', `USER_GUIDE_${language}.pdf`);
  const profile = fs.mkdtempSync(path.join(temp, 'chrome-'));
  const args = ['--headless', '--disable-gpu', '--no-pdf-header-footer', '--no-first-run', '--disable-background-networking', `--user-data-dir=${profile}`, `--print-to-pdf=${pdf}`, pathToFileURL(html).href];
  // Some isolated Linux build hosts cannot provide Chromium's OS sandbox.
  // Opt-in only, for rendering these generated static local documents.
  if (process.argv.includes('--no-sandbox')) args.unshift('--no-sandbox');
  const result = spawnSync(process.env.CHROMIUM_BIN || 'chromium', args, { encoding: 'utf8', timeout: 60000 });
  if (result.status !== 0 || result.error) throw new Error(result.error?.message || result.stderr || 'Chromium failed');
  if (!fs.readFileSync(pdf).subarray(0, 5).equals(Buffer.from('%PDF-'))) throw new Error('Invalid PDF: ' + pdf);
  console.log(`${pdf}: ${fs.statSync(pdf).size} bytes, ${illustrationCount} embedded illustrations`);
}
console.log('Temporary print HTML and browser profiles: ' + temp);
