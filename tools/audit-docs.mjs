#!/usr/bin/env node
// Built-in Node only. Run from this Git checkout: node tools/audit-docs.mjs [--write]
// First write pins HEAD/date; later runs use that saved baseline, including after commit.
// --base <git-ref> and --date YYYY-MM-DD deliberately choose another baseline/date.
import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const manifestPath = 'docs/audit-2026-11-02/DOCS_COVERAGE.json';
const blank = text => text.replace(/[^\r\n]/g, ' ');

// Mask code without moving lines: links shown as examples are not document links.
export function maskCode(source) {
    let fence;
    let masked = source.split(/(?<=\n)/).map(line => {
        const marker = line.match(/^ {0,3}(`{3,}|~{3,})(.*)/);
        if (fence) {
            if (marker && marker[1][0] === fence.char && marker[1].length >= fence.length
                && !marker[2].trim()) fence = undefined;
            return blank(line);
        }
        if (!marker) return line;
        fence = { char: marker[1][0], length: marker[1].length };
        return blank(line);
    }).join('');
    for (let start = masked.indexOf('`'); start !== -1;) {
        let length = 1;
        while (masked[start + length] === '`') length++;
        const delimiter = '`'.repeat(length);
        let end = masked.indexOf(delimiter, start + length);
        while (end !== -1 && (masked[end - 1] === '`' || masked[end + length] === '`')) {
            end = masked.indexOf(delimiter, end + length);
        }
        if (end === -1) {
            start = masked.indexOf('`', start + length);
            continue;
        }
        const after = end + length;
        masked = masked.slice(0, start) + blank(masked.slice(start, after)) + masked.slice(after);
        start = masked.indexOf('`', after);
    }
    return masked;
}

// Inline links/images and reference definitions cover the repository's Markdown.
export function markdownTargets(source) {
    const masked = maskCode(source);
    const found = [];
    const append = (target, offset) => found.push({
        target,
        line: masked.slice(0, offset).split('\n').length,
    });
    const brackets = [];
    for (let index = 0; index < masked.length; index++) {
        if (masked[index] === '\\') { index++; continue; }
        if (masked[index] === '[') { brackets.push(index); continue; }
        if (masked[index] !== ']' || !brackets.length) continue;
        const labelStart = brackets.pop();
        if (masked[index + 1] !== '(') continue;
        let offset = index + 2;
        while (/\s/.test(masked[offset] ?? '') && offset < masked.length) offset++;
        const start = offset;
        if (masked[offset] === '<') {
            const end = masked.indexOf('>', offset + 1);
            if (end !== -1) { append(masked.slice(offset + 1, end), labelStart); index = end; }
            continue;
        }
        let depth = 0;
        for (; offset < masked.length; offset++) {
            const char = masked[offset];
            if (char === '\\' && offset + 1 < masked.length) { offset++; continue; }
            if (char === '(') depth++;
            if (char === ')') { if (depth === 0) break; depth--; }
            if (/\s/.test(char) && depth === 0) break;
        }
        if (offset > start) append(masked.slice(start, offset), labelStart);
        index = offset;
    }
    for (const match of masked.matchAll(/^ {0,3}\[[^\]\n]+\]:\s*(<[^>\n]+>|[^\s]+)/gm)) {
        append(match[1].replace(/^<|>$/g, ''), match.index);
    }
    return found;
}

function git(root, ...args) {
    return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
}

function nulSet(output) {
    return new Set(output.split('\0').filter(Boolean));
}

function localTarget(root, document, raw) {
    if (/^file:/i.test(raw)) return { absolute: 'file-uri' };
    if (/^[a-z]:[\\/]/i.test(raw) || /^\\\\/.test(raw)) return { absolute: 'filesystem-path' };
    if (!raw || raw.startsWith('#') || raw.startsWith('//') || /^[a-z][a-z\d+.-]*:/i.test(raw)) return null;
    const withoutSuffix = raw.split(/[?#]/, 1)[0];
    if (!withoutSuffix) return null;
    let target;
    try { target = decodeURIComponent(withoutSuffix).replace(/\\([()[\]<> ])/g, '$1').replaceAll('\\', '/'); }
    catch { return { target: withoutSuffix, missing: true, reason: 'invalid-url-encoding' }; }
    const resolved = target.startsWith('/')
        ? path.resolve(root, '.' + target)
        : path.resolve(root, path.dirname(document), target);
    const relative = path.relative(root, resolved);
    const outside = relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative);
    return { target, missing: outside || !fs.existsSync(resolved), ...(outside ? { reason: 'outside-repository' } : {}) };
}

export function documentSha256(bytes) {
    return createHash('sha256').update(bytes.toString('utf8').replaceAll('\r\n', '\n'), 'utf8').digest('hex');
}

function reportFor(root, baseCommit, auditDate) {
    const discovered = nulSet(git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard'));
    const docs = [...discovered].filter(file => /\.md$/i.test(file) && /README|STATE/i.test(path.posix.basename(file))).sort();
    const atBase = nulSet(git(root, 'ls-tree', '-r', '--name-only', '-z', baseCommit));
    const changed = nulSet(git(root, 'diff', '--name-only', '-z', baseCommit, '--'));
    const [year, month, day] = auditDate.split('-');
    const dateMarkers = [auditDate, `${day}.${month}.${year}`, `${day}/${month}/${year}`];
    const missingLinks = [], absoluteFileLinks = [];
    const requestedDocs = docs.map(file => {
        const present = fs.existsSync(path.join(root, file));
        const bytes = present ? fs.readFileSync(path.join(root, file)) : null;
        const source = bytes?.toString('utf8') ?? '';
        for (const link of markdownTargets(source)) {
            const checked = localTarget(root, file, link.target);
            if (checked?.absolute) absoluteFileLinks.push({ path: file, line: link.line, kind: checked.absolute });
            if (checked?.missing) missingLinks.push({ path: file, line: link.line, target: checked.target,
                ...(checked.reason ? { reason: checked.reason } : {}) });
        }
        return {
            path: file,
            present,
            sha256: bytes ? documentSha256(bytes) : null,
            existedAtBase: atBase.has(file),
            changedSinceBase: !atBase.has(file) || changed.has(file),
            dated: /20\d{2}-\d{2}-\d{2}/.test(file),
            archival: /архив|archiv|legacy|историческ[^\n]{0,70}(?:срез|снимок|пакет)|historical[^\n]{0,70}(?:snapshot|packet)/i.test(source.slice(0, 1800)),
            currentDateMarker: dateMarkers.some(marker => source.includes(marker)),
        };
    });
    const coverage = {
        unchangedExistingDocs: requestedDocs.filter(doc => doc.existedAtBase && !doc.changedSinceBase).map(doc => doc.path),
        absentDocs: requestedDocs.filter(doc => !doc.present).map(doc => doc.path),
        missingCurrentDateMarker: requestedDocs.filter(doc => !doc.currentDateMarker).map(doc => doc.path),
        unlabelledHistoricalDocs: requestedDocs.filter(doc => doc.dated && !doc.path.includes(auditDate) && !doc.archival).map(doc => doc.path),
    };
    return {
        schemaVersion: 1,
        auditDate,
        baseCommit,
        discovery: 'git tracked + non-ignored untracked Markdown; basename contains README or STATE, case-insensitive',
        hashPolicy: 'SHA-256 of UTF-8 Markdown with CRLF normalized to LF; all other text is preserved',
        summary: {
            requestedDocs: requestedDocs.length,
            existingAtBase: requestedDocs.filter(doc => doc.existedAtBase).length,
            newDocs: requestedDocs.filter(doc => !doc.existedAtBase).length,
            archivalDocs: requestedDocs.filter(doc => doc.archival).length,
            missingLinks: missingLinks.length,
            absoluteFileLinks: absoluteFileLinks.length,
        },
        requestedDocs,
        coverage,
        missingLinks,
        absoluteFileLinks,
    };
}

function main() {
    const args = process.argv.slice(2);
    let write = false, base, date;
    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--write') write = true;
        else if (args[i] === '--base' && args[i + 1]) base = args[++i];
        else if (args[i] === '--date' && args[i + 1]) date = args[++i];
        else throw new Error('Usage: node tools/audit-docs.mjs [--write] [--base <git-ref>] [--date YYYY-MM-DD]');
    }
    const root = git(process.cwd(), 'rev-parse', '--show-toplevel').trim();
    const destination = path.join(root, manifestPath);
    const saved = fs.existsSync(destination) ? JSON.parse(fs.readFileSync(destination, 'utf8')) : null;
    const auditDate = date ?? saved?.auditDate ?? new Date().toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(auditDate)
        || new Date(auditDate + 'T12:00:00Z').toISOString().slice(0, 10) !== auditDate) throw new Error('Invalid audit date');
    const baseCommit = git(root, 'rev-parse', '--verify', `${base ?? saved?.baseCommit ?? 'HEAD'}^{commit}`).trim();
    const report = reportFor(root, baseCommit, auditDate);
    if (write) {
        fs.mkdirSync(path.dirname(destination), { recursive: true });
        fs.writeFileSync(destination, JSON.stringify(report, null, 2) + '\n');
    }
    const manifestMatches = write || (saved !== null && JSON.stringify(saved) === JSON.stringify(report));
    const findings = report.missingLinks.length + report.absoluteFileLinks.length
        + Object.values(report.coverage).reduce((sum, files) => sum + files.length, 0);
    console.log(JSON.stringify({ ok: findings === 0 && manifestMatches, manifest: manifestPath, mode: write ? 'write' : 'verify',
        manifestMatches, ...report.summary, coverage: report.coverage, missingLinks: report.missingLinks,
        absoluteFileLinks: report.absoluteFileLinks }, null, 2));
    if (!manifestMatches) console.error('Manifest absent or stale. Review document changes, then run with --write.');
    if (findings || !manifestMatches) process.exitCode = 1;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try { main(); }
    catch (error) { console.error(`Documentation audit failed: ${error.message}`); process.exitCode = 1; }
}
