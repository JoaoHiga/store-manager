#!/usr/bin/env node
// Regenerates the factual sections of docs/architecture.md from the code itself: technologies in use,
// backend domains, ports and their adapters, REST endpoints, Flyway migrations and frontend structure.
// Only the content between `<!-- BEGIN GENERATED: <name> -->` / `<!-- END GENERATED: <name> -->` markers is
// rewritten; everything else in the document is hand-written and left untouched.
//
// Usage:
//   node scripts/architecture-doc.mjs          rewrite docs/architecture.md in place
//   node scripts/architecture-doc.mjs --check  exit 1 if the document is out of date (used by CI)

import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DOC_PATH = join(ROOT, 'docs', 'architecture.md');
const APP_JAVA = join(ROOT, 'application', 'src', 'main', 'java', 'com', 'storemanager');
const INFRA_JAVA = join(ROOT, 'infrastructure', 'src', 'main', 'java', 'com', 'storemanager');
const MIGRATIONS = join(ROOT, 'infrastructure', 'src', 'main', 'resources', 'db', 'migration');
const FRONTEND_SRC = join(ROOT, 'frontend', 'src');

// ---------------------------------------------------------------------------------------------------------
// Pure helpers (unit-tested in architecture-doc.test.mjs)
// ---------------------------------------------------------------------------------------------------------

export function replaceSections(doc, sections) {
  let result = doc;
  for (const [name, content] of Object.entries(sections)) {
    const begin = `<!-- BEGIN GENERATED: ${name} -->`;
    const end = `<!-- END GENERATED: ${name} -->`;
    const start = result.indexOf(begin);
    const stop = result.indexOf(end);
    if (start === -1 || stop === -1 || stop < start) {
      throw new Error(`docs/architecture.md has no "${name}" markers (${begin} ... ${end})`);
    }
    result = `${result.slice(0, start + begin.length)}\n${content}\n${result.slice(stop)}`;
  }
  return result;
}

export function parseEndpoints(source) {
  const base = source.match(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"/)?.[1] ?? '';
  const mapping = /@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?/g;
  const handler = /public\s+[\w<>,.?\s[\]]+?\s+(\w+)\s*\(/g;
  const endpoints = [];
  for (const match of source.matchAll(mapping)) {
    handler.lastIndex = match.index + match[0].length;
    endpoints.push({
      method: match[1].toUpperCase(),
      path: `${base}${match[2] ?? ''}`,
      handler: handler.exec(source)?.[1] ?? '?',
    });
  }
  return endpoints;
}

export function parseImplementedInterfaces(source) {
  const list = source.match(/\bimplements\s+([\w\s,<>]+?)\s*\{/)?.[1];
  return list ? list.split(',').map((name) => name.replace(/<.*$/, '').trim()).filter(Boolean) : [];
}

// Dependency versions are deliberately left out: Dependabot bumps them, and each bump would otherwise make
// the document stale. Only the Java version (not Dependabot-managed) is shown.
export function renderBackendTechnologies(java, modules) {
  const rows = [`| Java ${java} | root \`pom.xml\` | — |`, '| Spring Boot (BOM) | root `pom.xml` | — |'];
  for (const [module, deps] of Object.entries(modules)) {
    for (const dep of deps) {
      if (dep.groupId === 'com.storemanager') continue;
      rows.push(`| ${dep.artifactId} | \`${module}\` | ${dep.scope} |`);
    }
  }
  return ['| Technology | Module | Scope |', '|---|---|---|', ...rows].join('\n');
}

export function renderFrontendPackages(pkg) {
  const rows = [
    ...Object.keys(pkg.dependencies ?? {}).map((name) => `| ${name} | runtime |`),
    ...Object.keys(pkg.devDependencies ?? {}).map((name) => `| ${name} | dev/test |`),
  ];
  return ['| Package | Kind |', '|---|---|', ...rows].join('\n');
}

// ---------------------------------------------------------------------------------------------------------
// Filesystem readers
// ---------------------------------------------------------------------------------------------------------

function listFiles(dir, predicate = () => true) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .sort()
    .flatMap((entry) => {
      const path = join(dir, entry);
      return statSync(path).isDirectory() ? listFiles(path, predicate) : predicate(path) ? [path] : [];
    });
}

function listDirs(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((entry) => statSync(join(dir, entry)).isDirectory())
    .sort();
}

const javaName = (path) => basename(path, '.java');
const isJava = (path) => path.endsWith('.java');
const rel = (path) => relative(ROOT, path).split('\\').join('/');
// `application/src/main/java/com/storemanager/sales/...` -> `application/…/sales/...`, to keep tables readable.
const short = (path) => rel(path).replace('/src/main/java/com/storemanager/', '/…/');
const code = (items) => (items.length ? items.map((item) => `\`${item}\``).join(', ') : '—');

function javaFilesIn(dir) {
  return existsSync(dir)
    ? readdirSync(dir)
        .filter((entry) => entry.endsWith('.java'))
        .sort()
        .map((entry) => join(dir, entry))
    : [];
}

function isInterface(path) {
  return /\bpublic\s+interface\s+\w+/.test(readFileSync(path, 'utf8'));
}

function pomDependencies(pomPath) {
  const pom = readFileSync(pomPath, 'utf8');
  const block = pom.match(/<dependencies>([\s\S]*?)<\/dependencies>/)?.[1] ?? '';
  return [...block.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map(([, dep]) => ({
    groupId: dep.match(/<groupId>(.*?)<\/groupId>/)?.[1],
    artifactId: dep.match(/<artifactId>(.*?)<\/artifactId>/)?.[1],
    scope: dep.match(/<scope>(.*?)<\/scope>/)?.[1] ?? 'compile',
  }));
}

// ---------------------------------------------------------------------------------------------------------
// Section builders
// ---------------------------------------------------------------------------------------------------------

function techBackend() {
  const rootPom = readFileSync(join(ROOT, 'pom.xml'), 'utf8');
  const java = rootPom.match(/<java\.version>(.*?)<\/java\.version>/)?.[1] ?? '?';
  const modules = Object.fromEntries(
    ['application', 'infrastructure'].map((module) => [module, pomDependencies(join(ROOT, module, 'pom.xml'))]),
  );
  return renderBackendTechnologies(java, modules);
}

function techFrontend() {
  return renderFrontendPackages(JSON.parse(readFileSync(join(ROOT, 'frontend', 'package.json'), 'utf8')));
}

function backendDomains() {
  const rows = listDirs(APP_JAVA).map((domain) => {
    const entities = javaFilesIn(join(APP_JAVA, domain, 'domain')).map(javaName);
    const ports = javaFilesIn(join(APP_JAVA, domain, 'domain', 'port')).filter(isInterface).map(javaName);
    const useCases = javaFilesIn(join(APP_JAVA, domain, 'usecase'))
      .map(javaName)
      .filter((name) => name.endsWith('UseCase'));
    const adapters = listDirs(join(INFRA_JAVA, domain, 'infrastructure')).map(
      (sub) => `${sub}/ (${javaFilesIn(join(INFRA_JAVA, domain, 'infrastructure', sub)).length})`,
    );
    return `| \`${domain}\` | ${code(entities)} | ${code(ports)} | ${code(useCases)} | ${code(adapters)} |`;
  });
  return [
    '| Domain | Domain model (`domain/`) | Ports (`domain/port/`) | Use cases (`usecase/`) | Infrastructure (files) |',
    '|---|---|---|---|---|',
    ...rows,
  ].join('\n');
}

function portsAndAdapters() {
  const ports = listFiles(APP_JAVA, isJava).filter((path) => path.includes('/port/') && isInterface(path));
  const implementations = new Map();
  for (const path of listFiles(INFRA_JAVA, isJava)) {
    for (const iface of parseImplementedInterfaces(readFileSync(path, 'utf8'))) {
      implementations.set(iface, [...(implementations.get(iface) ?? []), path]);
    }
  }
  const rows = ports.map((port) => {
    const adapters = implementations.get(javaName(port)) ?? [];
    const impl = adapters.length ? adapters.map((path) => `\`${short(path)}\``).join('<br>') : '**none**';
    return `| \`${javaName(port)}\` | \`${short(port)}\` | ${impl} |`;
  });
  return ['| Port (interface) | Declared in | Implemented by (adapter) |', '|---|---|---|', ...rows].join('\n');
}

function restEndpoints() {
  const rows = listFiles(INFRA_JAVA, (path) => path.endsWith('Controller.java')).flatMap((path) =>
    parseEndpoints(readFileSync(path, 'utf8')).map(
      (endpoint) => `| ${endpoint.method} | \`${endpoint.path}\` | \`${javaName(path)}.${endpoint.handler}\` |`,
    ),
  );
  return ['| Method | Path | Handler |', '|---|---|---|', ...rows].join('\n');
}

function migrations() {
  const rows = listFiles(MIGRATIONS, (path) => path.endsWith('.sql')).map((path) => {
    const [, version, description] = basename(path, '.sql').match(/^V(\d+)__(.*)$/) ?? [];
    return `| V${version} | ${description?.replaceAll('_', ' ') ?? basename(path)} |`;
  });
  return ['| Version | Description |', '|---|---|', ...rows].join('\n');
}

function frontendStructure() {
  const isSource = (path) => /\.(jsx?|tsx?)$/.test(path) && !/\.test\./.test(path);
  const names = (dir) => listFiles(join(FRONTEND_SRC, dir), isSource).map((path) => basename(path));
  return [
    '| Layer | Folder | Files |',
    '|---|---|---|',
    `| Pages | \`frontend/src/pages/\` | ${code(names('pages'))} |`,
    `| Organisms | \`frontend/src/components/organisms/\` | ${code(names('components/organisms'))} |`,
    `| Molecules | \`frontend/src/components/molecules/\` | ${code(names('components/molecules'))} |`,
    `| Atoms | \`frontend/src/components/atoms/\` | ${code(names('components/atoms'))} |`,
    `| API clients | \`frontend/src/api/\` | ${code(names('api'))} |`,
  ].join('\n');
}

export function buildSections() {
  return {
    'tech-backend': techBackend(),
    'tech-frontend': techFrontend(),
    'backend-domains': backendDomains(),
    'ports-adapters': portsAndAdapters(),
    'rest-endpoints': restEndpoints(),
    migrations: migrations(),
    'frontend-structure': frontendStructure(),
  };
}

// ---------------------------------------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------------------------------------

function main() {
  const current = readFileSync(DOC_PATH, 'utf8');
  const updated = replaceSections(current, buildSections());

  if (process.argv.includes('--check')) {
    if (updated !== current) {
      console.error('docs/architecture.md is out of date. Run: node scripts/architecture-doc.mjs');
      process.exit(1);
    }
    console.log('docs/architecture.md is up to date.');
    return;
  }

  writeFileSync(DOC_PATH, updated);
  console.log(updated === current ? 'docs/architecture.md already up to date.' : 'docs/architecture.md updated.');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
