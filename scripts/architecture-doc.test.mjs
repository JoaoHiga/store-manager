import assert from 'node:assert/strict';
import { test } from 'node:test';

import { parseEndpoints, parseImplementedInterfaces, replaceSections } from './architecture-doc.mjs';

test('replaceSections rewrites only the content between matching markers', () => {
  const doc = [
    'Hand-written intro.',
    '<!-- BEGIN GENERATED: tech -->',
    'stale',
    '<!-- END GENERATED: tech -->',
    'Hand-written outro.',
  ].join('\n');

  const result = replaceSections(doc, { tech: 'fresh line 1\nfresh line 2' });

  assert.equal(
    result,
    [
      'Hand-written intro.',
      '<!-- BEGIN GENERATED: tech -->',
      'fresh line 1',
      'fresh line 2',
      '<!-- END GENERATED: tech -->',
      'Hand-written outro.',
    ].join('\n'),
  );
});

test('replaceSections is idempotent', () => {
  const doc = '<!-- BEGIN GENERATED: a -->\nold\n<!-- END GENERATED: a -->';
  const once = replaceSections(doc, { a: 'new' });

  assert.equal(replaceSections(once, { a: 'new' }), once);
});

test('replaceSections fails when a generated section has no markers in the document', () => {
  assert.throws(() => replaceSections('no markers here', { missing: 'x' }), /missing/);
});

test('parseEndpoints combines the class-level base path with each method mapping', () => {
  const source = `
    @RestController
    @RequestMapping("/api/sales")
    public class SaleController {
        @GetMapping
        public List<SaleResponse> list() {}

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public SaleResponse record(@Valid @RequestBody RecordSaleRequest request) {}

        @PostMapping("/{id}/void")
        public SaleResponse voidSale(@PathVariable Long id) {}
    }
  `;

  assert.deepEqual(parseEndpoints(source), [
    { method: 'GET', path: '/api/sales', handler: 'list' },
    { method: 'POST', path: '/api/sales', handler: 'record' },
    { method: 'POST', path: '/api/sales/{id}/void', handler: 'voidSale' },
  ]);
});

test('parseImplementedInterfaces returns the interfaces a class implements', () => {
  const source = 'public class SaleRepositoryAdapter implements SaleRepositoryPort, Closeable {';

  assert.deepEqual(parseImplementedInterfaces(source), ['SaleRepositoryPort', 'Closeable']);
});
