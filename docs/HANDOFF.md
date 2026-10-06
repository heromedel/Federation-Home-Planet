# Handoffs

How a Claude session on Federation Home Planet hands a bug to the session that owns the code (written by
Cloud-C-BugsandFeedback, 5.49, from its docked Steam launch and crew trade handoffs).

## When

Before fixing a bug, ask heromedel exactly: "Would you like me to work on this or prepare a handoff?" A handoff sends
the bug to the session that owns that code (the one that wrote it, or is working in it now), so two sessions never
edit the same file at once.

## Before writing

- Reproduce it if you can: a test on your own bench that fails until it's fixed. If you can't (no Steam, no Windows),
  say so plainly and why: "Likely, not reproduced".
- Fetch the receiving branch and take every line number from its code, not yours.
- Check what else the bug touches: namesakes (duplicate crew names can't be avoided), Windows line endings in the log
  files, files older versions wrote.
- Decide how bad it is: does it harm a fleet (a clone, a lost ship), or only a record or a message?

## The parts, in this order

1. **Header.** Eyebrow: "Handoff to <branch>". Title: the bug in one plain sentence, as the player meets it ("A crew
   member traded away is listed as missing in action"). Three chips: the status (Reproduced, or Likely, not
   reproduced); where you found it, with the version and commit; your branch.
2. **What happens.** Paragraphs, not lists. What the player does and sees. Quote the real message, log lines or file
   contents exactly, in a block. How bad it is. How you confirmed it, and what in the same test behaved correctly
   (that tells them what not to break).
3. **Where.** One row per place: File:line, then one sentence on what that code does and how it leads to the bug.
4. **Suggested fix.** Numbered, small steps, with the exact method names and strings. Name the edge cases. Mark the
   decisions that are theirs: "your call". Player-facing wording follows CLAUDE.md's voice rules.
5. **How to test.** The harness test to extend, the steps, and what it should then show. Steps on Windows only when it
   can't be tested in a cloud session.
6. **For the merge.** Your branch's version and whether it's pushed; whether you touched these files; whether a clash
   is expected; their next version number.
7. **The plain-text copy.** The whole handoff again as one block, to paste into the other session's chat. It must
   stand alone: the receiving session can't open the page.

## How it reads

- Plain words and short sentences, the way you'd explain it across a table. Say what you checked as fact, and label
  guesses as guesses.
- Quote messages and log lines; don't paraphrase them.
- No lists of problems, and no status lists tacked on. heromedel's own words go in exactly as written.
- Never FTL's game files or a link to them.

## After

- Publish the page as a private artifact (HTML, icon "clipboard"), kept in your scratchpad's handoffs folder (never in
  the repo). Give heromedel the link and a short paragraph; the copy box is what they paste.
- Don't fix it yourself once it's handed off.
- Keep your reproduction test. When the fix comes back, merge it and run that test against it before saying it's
  fixed.

*Information that doesn't fit in the template can be appended to the template.

## The page template

Save it as an .html file in your scratchpad, fill in the square brackets, and publish it. It keeps every handoff
looking the same: one reading column, the status chips, the Where rows, the copy box. Anything that doesn't fit goes
in the [Anything else] section, before the copy box.

```html
<title>[Subject] Handoff</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>
/* Layout: one reading column, a short status strip, then the handoff in order: what, where, fix, test, merge. */
pre.log { margin: 0; overflow-x: auto; font: 13px/1.5 var(--mono); background: var(--code-bg); border: 1px solid var(--line); border-radius: 6px; padding: 10px 12px; }
:root {
	--bg: #f6f5f1; --surface: #ffffff; --fg: #1d2430; --muted: #5b6474; --line: #d9dbe0;
	--accent: #9a6a12; --accent-soft: #f3e6c8; --code-bg: #eef0f3;
	--sans: "IBM Plex Sans", "Segoe UI", system-ui, sans-serif;
	--mono: "IBM Plex Mono", ui-monospace, "Cascadia Mono", Consolas, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
	--bg: #10141b; --surface: #171d26; --fg: #dce4eb; --muted: #94a0ae; --line: #2a3340;
	--accent: #e2b44f; --accent-soft: #33291a; --code-bg: #1d2530; color-scheme: dark } }
:root[data-theme="dark"] {
	--bg: #10141b; --surface: #171d26; --fg: #dce4eb; --muted: #94a0ae; --line: #2a3340;
	--accent: #e2b44f; --accent-soft: #33291a; --code-bg: #1d2530; color-scheme: dark }
body { background: var(--bg); color: var(--fg); font-family: var(--sans); font-size: 16px; line-height: 1.55; padding-inline: 16px; padding-block: 32px 48px; }
main { max-width: 68ch; margin: 0 auto; display: grid; gap: 28px; }
header { display: grid; gap: 10px; }
.eyebrow { font-family: var(--mono); font-size: 12px; letter-spacing: .06em; text-transform: uppercase; color: var(--accent); }
h1 { font-size: 28px; line-height: 1.2; margin: 0; font-weight: 600; text-wrap: balance; }
h2 { font-size: 17px; margin: 0 0 8px; font-weight: 600; }
p, ol, ul { margin: 0; }
ol, ul { padding-left: 1.3em; display: grid; gap: 6px; }
.meta { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { font-family: var(--mono); font-size: 12px; padding: 3px 9px; border: 1px solid var(--line); border-radius: 999px; color: var(--muted); background: var(--surface); }
.chip.status { color: var(--accent); border-color: var(--accent); background: var(--accent-soft); }
section { display: grid; gap: 8px; }
code { font-family: var(--mono); font-size: .9em; background: var(--code-bg); padding: 1px 5px; border-radius: 4px; overflow-wrap: anywhere; }
.where { display: grid; gap: 6px; padding: 0; list-style: none; }
.where li { display: grid; grid-template-columns: minmax(0, 13ch) minmax(0, 1fr); gap: 12px; padding: 8px 0; border-top: 1px solid var(--line); }
.where li:last-child { border-bottom: 1px solid var(--line); }
.loc { font-family: var(--mono); font-size: 13px; color: var(--accent); font-variant-numeric: tabular-nums; }
.copy { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 14px 16px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.copy p { flex: 1 1 260px; min-width: 0; color: var(--muted); font-size: 14px; }
button { font: 500 14px var(--sans); color: var(--bg); background: var(--fg); border: 0; border-radius: 6px; padding: 8px 14px; cursor: pointer; }
button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
#plain { width: 100%; min-height: 0; font: 12px/1.5 var(--mono); color: var(--fg); background: var(--code-bg); border: 1px solid var(--line); border-radius: 6px; padding: 10px; }
@media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
</style>

<main>
	<header>
		<div class="eyebrow">Handoff to [receiving branch]</div>
		<h1>[The bug in one plain sentence, as the player meets it]</h1>
		<div class="meta">
			<span class="chip status">[Reproduced | Likely, not reproduced]</span>
			<span class="chip">Found [reviewing | reading | testing] [version] ([commit])</span>
			<span class="chip">From [your branch]</span>
		</div>
	</header>

	<section>
		<h2>What happens</h2>
		<p>[What the player does and what they see. Quote the real message, log lines or file contents exactly.]</p>
		<pre class="log">[the exact log lines or message, if any]</pre>
		<p>[How bad: does it harm the fleet, or only a record? How you confirmed it, and what in the same test behaved correctly.]</p>
	</section>

	<section>
		<h2>Where</h2>
		<ul class="where">
			<li><span class="loc">[File]:[line]</span><span>[One sentence: what this code does, and how it leads to the bug.]</span></li>
			<li><span class="loc">[File]:[line]</span><span>[...]</span></li>
		</ul>
	</section>

	<section>
		<h2>Suggested fix</h2>
		<ol>
			<li>[A small, concrete step: the method, the exact strings or names.]</li>
			<li>[The edge cases: namesakes, Windows line endings, files older versions wrote.]</li>
			<li>[A decision that's theirs to make: "your call".]</li>
		</ol>
	</section>

	<section>
		<h2>How to test</h2>
		<ul>
			<li>[The harness test to extend, the steps, and what it should then show.]</li>
			<li>[Steps on Windows only when it can't be tested in a cloud session.]</li>
		</ul>
	</section>

	<section>
		<h2>For the merge</h2>
		<p>[Your branch's version, pushed or not; whether you touched these files; whether a clash is expected; their next version number.]</p>
	</section>

	<section>
		<h2>[Anything else]</h2>
		<p>[Information that doesn't fit the sections above, in a section of its own, before the copy box.]</p>
	</section>

	<div class="copy">
		<p>The same handoff as plain text, to paste into [receiving session]'s chat.</p>
		<button id="copy-btn" type="button">Copy handoff</button>
		<textarea id="plain" rows="9" readonly aria-label="Handoff as plain text">[The whole handoff as one block of plain text that stands alone: the receiving session can't open this page.]</textarea>
	</div>
</main>

<script>
(function () {
	var btn = document.getElementById('copy-btn'), box = document.getElementById('plain');
	btn.addEventListener('click', function () {
		function fallback() { box.focus(); box.select(); btn.textContent = 'Selected: press Ctrl+C'; }
		try {
			navigator.clipboard.writeText(box.value).then(function () { btn.textContent = 'Copied'; }, fallback);
		} catch (e) { fallback(); }
	});
})();
</script>
```
