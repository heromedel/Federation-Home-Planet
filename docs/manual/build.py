"""Builds both user manuals from one source, so they never disagree.

    python3 docs/manual/build.py

Reads docs/manual/manual.src and the pictures in docs/manual/images/, and writes User-Manual.txt (plain text,
CRLF, wrapped at 78) and User-Manual.html (one file, the pictures inside it) in the repo root.

The source's marks:
    # [1] TITLE        a section              ## Heading        a heading
    1. text            a numbered step         > text            a paragraph that belongs to the step above
    !text              a line kept as written  |text             a line break inside a paragraph
    @image f.png | alt | caption | fair    a picture (the HTML only). Add '| fair' for a screenshot that shows FTL's art:
                    its caption then carries the fair use line
Anything else is a paragraph. Version: the first '!User Manual, version N' line.
"""
import base64, html, os, re, sys, textwrap

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
W = 78


def isstep(l):
    return l[:3].rstrip('. ').isdigit() and '. ' in l[:4]


# ---------------------------------------------------------------- text

def build_text(lines):
    out, para, kind = [], [], [None]

    def blank():
        if out and out[-1] != '': out.append('')

    def flush():
        if para:
            segs = [[]]
            for x in para:
                if x.startswith('|'): segs.append([x[1:].strip()])
                else: segs[-1].append(x)
            blank()
            first = ' '.join(segs[0])
            n = first.index('. ') + 2 if kind[0] == 'step' else 3 if kind[0] == 'cont' else 0
            wrapped = []
            for i, sg in enumerate(segs):
                wrapped.append(textwrap.fill(' '.join(sg), W, initial_indent=' ' * (n if (i > 0 or kind[0] == 'cont') else 0), subsequent_indent=' ' * n))
            out.append('\n'.join(wrapped))
            out.append('')
        del para[:]
        kind[0] = None

    for raw in lines:
        line = raw.rstrip()
        if line.startswith('@image'):
            continue
        if line.startswith('# '):
            flush(); blank(); out.extend(['', '=' * W, line[2:], '=' * W, ''])
        elif line.startswith('## '):
            flush(); blank(); out.extend([line[3:], '-' * len(line[3:]), ''])
        elif line.startswith('!'):
            if para: flush()
            out.append(line[1:])
        elif line == '':
            flush()
        else:
            if out and out[-1].strip() and not para: out.append('')
            if line.startswith('>'):
                if para and kind[0] != 'cont': flush()
                kind[0] = 'cont'; para.append(line[1:].strip()); continue
            if para and isstep(line): flush()
            if not para: kind[0] = 'step' if isstep(line) else 'p'
            para.append(line if line.startswith('|') else line.strip())
    flush()
    text = '\n'.join(out).strip('\n') + '\n'
    while '\n\n\n\n' in text: text = text.replace('\n\n\n\n', '\n\n\n')
    return text


# ---------------------------------------------------------------- html

NOT_UI = {'FTL', 'GOG', 'AE', 'AEs', 'USB', 'DX', 'URL', 'II'}
UI = re.compile(r'\b[A-Z]{2,}(?:[ ][A-Z]{2,})*(?:\.\.\.)?')


def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r'\[(\d+)\]', lambda m: '<a href="#s%s">[%s]</a>' % (m.group(1), m.group(1)), s)

    def ui(m):
        t = m.group(0)
        words = t.rstrip('.').split(' ')
        if all(w in NOT_UI for w in words): return t
        return '<span class="ui">%s</span>' % t
    # leave the section links alone
    parts = re.split(r'(<a [^>]*>.*?</a>)', s)
    for i in range(0, len(parts), 2): parts[i] = UI.sub(ui, parts[i])
    return ''.join(parts)


def paragraph(segs):
    return '<p>' + '<br>\n'.join(inline(' '.join(s)) for s in segs if s) + '</p>'


def image_tag(spec):
    f, alt, cap, fair = [x.strip() for x in (spec.split('|') + ['', '', ''])[:4]]
    path = os.path.join(HERE, 'images', f)
    data = base64.b64encode(open(path, 'rb').read()).decode('ascii')
    return ('<figure><img src="data:image/png;base64,%s" alt="%s"><figcaption>%s%s</figcaption></figure>'
            % (data, html.escape(alt, quote=True), inline(cap), '<span class="fair">Fair use screenshot, for instructional purposes only.</span>' if fair == 'fair' else ''))


CSS = """
:root{--bg:#f6f5f1;--surface:#fff;--fg:#1d2430;--muted:#5b6474;--line:#d9dbe0;--accent:#9a6a12;--soft:#f3e6c8;--code:#eef0f3}
@media (prefers-color-scheme:dark){:root{--bg:#10141b;--surface:#171d26;--fg:#dce4eb;--muted:#94a0ae;--line:#2a3340;--accent:#e2b44f;--soft:#33291a;--code:#1d2530}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:17px/1.65 Georgia,'Times New Roman',serif;padding:0 16px 64px}
main{max-width:46rem;margin:0 auto}
header{padding:48px 0 8px;border-bottom:1px solid var(--line)}
header .eyebrow{font:600 13px/1 system-ui,sans-serif;letter-spacing:.12em;text-transform:uppercase;color:var(--accent)}
h1{font:700 34px/1.15 system-ui,sans-serif;margin:10px 0 6px}
header p{margin:4px 0;color:var(--muted)}
nav.toc{margin:28px 0 8px;padding:18px 22px;background:var(--surface);border:1px solid var(--line);border-radius:10px}
nav.toc h2{font:600 14px system-ui,sans-serif;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:0 0 8px;border:0;padding:0}
nav.toc ol{list-style:none;margin:0;padding:0;columns:2;column-gap:28px}
nav.toc li{break-inside:avoid;padding:2px 0;font-family:system-ui,sans-serif;font-size:15px}
nav.toc a{color:var(--fg);text-decoration:none}nav.toc a:hover{color:var(--accent)}
nav.toc .n{color:var(--accent);display:inline-block;min-width:2.4em;font-variant-numeric:tabular-nums}
h2{font:700 25px/1.2 system-ui,sans-serif;margin:52px 0 12px;padding-top:20px;border-top:1px solid var(--line)}
h2 .n{color:var(--accent);margin-right:.35em}
h3{font:650 18px/1.3 system-ui,sans-serif;margin:30px 0 4px;color:var(--accent)}
p{margin:12px 0}
p.top{text-align:right;margin:6px 0 0;font:600 13px system-ui,sans-serif}
p.top a{text-decoration:none}
a{color:var(--accent)}
.ui{font:600 .86em system-ui,sans-serif;letter-spacing:.02em;background:var(--code);border:1px solid var(--line);border-radius:4px;padding:0 5px;white-space:nowrap}
.step{display:grid;grid-template-columns:2rem 1fr;gap:6px 10px;margin:14px 0}
.step>.k{font:700 15px system-ui,sans-serif;color:var(--accent);background:var(--soft);border-radius:50%;width:1.8rem;height:1.8rem;display:flex;align-items:center;justify-content:center;margin-top:3px}
.step>div{min-width:0}
.step>div>p:first-child{margin-top:0}
pre{font:14px/1.5 ui-monospace,Consolas,monospace;background:var(--code);border:1px solid var(--line);border-radius:8px;padding:10px 14px;overflow-x:auto;margin:10px 0}
figure{margin:22px 0;text-align:center}
figure img{max-width:100%;height:auto;border:1px solid var(--line);border-radius:8px}
figcaption{font:14px/1.4 system-ui,sans-serif;color:var(--muted);margin-top:8px}
figcaption .fair{display:block;font:italic 11px/1.4 system-ui,sans-serif;margin-top:3px}
footer{margin-top:56px;padding-top:18px;border-top:1px solid var(--line);text-align:center;font-style:italic;color:var(--muted)}
@media (max-width:560px){body{font-size:16px}nav.toc ol{columns:1}h1{font-size:28px}}
@media print{body{background:#fff;color:#000}nav.toc,figure img{border-color:#999}}
"""


def build_html(lines):
    # header block, the contents, then the sections
    version = ''
    for l in lines:
        m = re.match(r'!User Manual, version (\S+)', l)
        if m: version = m.group(1); break
    first = next(i for i, l in enumerate(lines) if l.startswith('# '))
    head, body = lines[:first], lines[first:]

    toc = []
    for l in head:
        m = re.match(r'!\s+\[(\d+)\]\s+(.*)$', l)
        if m: toc.append((m.group(1), m.group(2).strip()))
    # the plain paragraph before CONTENTS, and the note under the list
    plain = [l for l in head if l and not l.startswith('!')]

    o = ['<!doctype html>', '<html lang="en"><head><meta charset="utf-8">',
         '<meta name="viewport" content="width=device-width,initial-scale=1">',
         '<title>The Home Planet Station User Manual</title><style>%s</style></head><body><main>' % CSS,
         '<header id="top"><div class="eyebrow">Issued by The Federation Home Planet</div><h1>The Home Planet Station</h1>',
         '<p>User Manual, version %s</p>' % html.escape(version)]
    for p in plain: o.append('<p>%s</p>' % inline(p))
    o.append('</header>')
    o.append('<nav class="toc"><h2>Contents</h2><ol>')
    for n, t in toc: o.append('<li><a href="#s%s"><span class="n">[%s]</span>%s</a></li>' % (n, n, html.escape(t)))
    o.append('</ol><p style="font:14px/1.5 system-ui,sans-serif;color:var(--muted);margin:12px 0 0">Buttons on the station\'s main screens are written in capitals, such as LAUNCH FTL, just as they appear on screen. Other buttons and options are written as they are labelled, such as Patch mods.</p></nav>')

    titles = dict(toc)
    closing = []
    para, kind = [], [None]
    step = [None]  # an open step: list of html pieces

    def close_step():
        if step[0] is not None:
            num, pieces = step[0]
            o.append('<div class="step"><div class="k">%s</div><div>%s</div></div>' % (num, '\n'.join(pieces)))
            step[0] = None

    def emit(piece):
        if step[0] is not None: step[0][1].append(piece)
        else: o.append(piece)

    def flush():
        if not para: return
        segs = [[]]
        for x in para:
            if x.startswith('|'): segs.append([x[1:].strip()])
            else: segs[-1].append(x)
        if kind[0] == 'step':
            close_step()
            first = ' '.join(segs[0])
            num = first[:first.index('.')]
            segs[0] = [first[first.index('. ') + 2:]]
            step[0] = (num, [paragraph(segs)])
        elif kind[0] == 'cont':
            emit(paragraph(segs))
        else:
            close_step()
            o.append(paragraph(segs))
        del para[:]
        kind[0] = None

    pre = []
    in_section = False
    TOP = '<p class="top"><a href="#top">Top</a></p>'

    def flush_pre():
        if pre:
            emit('<pre>%s</pre>' % html.escape(textwrap.dedent('\n'.join(pre))))
            del pre[:]

    for raw in body:
        line = raw.rstrip()
        if line.startswith('!Fair flights') or (closing and line.startswith('!')):  # the closing words
            flush(); flush_pre(); close_step()
            if line[1:].strip(): closing.append(line[1:])
            continue
        if line.startswith('!'):
            flush()
            pre.append(line[1:])
            continue
        if pre and not line.startswith('!'):
            flush_pre()
        if line.startswith('# '):
            flush(); close_step()
            if in_section: o.append(TOP)
            in_section = True
            m = re.match(r'# \[(\d+)\] (.*)$', line)
            o.append('<h2 id="s%s"><span class="n">[%s]</span>%s</h2>' % (m.group(1), m.group(1), html.escape(titles.get(m.group(1), m.group(2).title()))))
        elif line.startswith('## '):
            flush(); close_step(); o.append('<h3>%s</h3>' % html.escape(line[3:]))
        elif line.startswith('@image'):
            flush(); close_step(); o.append(image_tag(line[len('@image'):]))
        elif line == '':
            flush()
        elif line.startswith('>'):
            if para and kind[0] != 'cont': flush()
            kind[0] = 'cont'; para.append(line[1:].strip())
        else:
            if para and isstep(line): flush()
            if not para: kind[0] = 'step' if isstep(line) else 'p'
            para.append(line if line.startswith('|') else line.strip())
    flush(); flush_pre(); close_step()
    if in_section: o.insert(len(o), TOP)
    if closing: o.append('<footer>%s</footer>' % '<br>'.join(html.escape(c) for c in closing))
    o.append('</main></body></html>')
    return '\n'.join(o) + '\n'


def main():
    lines = open(os.path.join(HERE, 'manual.src'), encoding='utf-8').read().split('\n')
    text = build_text(lines)
    open(os.path.join(ROOT, 'User-Manual.txt'), 'w', encoding='utf-8', newline='\r\n').write(text)
    open(os.path.join(ROOT, 'User-Manual.html'), 'w', encoding='utf-8', newline='\n').write(build_html(lines))
    bad = [(i + 1, l) for i, l in enumerate(text.split('\n')) if len(l) > W]
    nonascii = [c for c in text if ord(c) > 127]
    print('text: %d lines, %d over %d, %d non-ASCII; html: %d KB' % (text.count('\n'), len(bad), W, len(nonascii), os.path.getsize(os.path.join(ROOT, 'User-Manual.html')) // 1024))


if __name__ == '__main__':
    main()
