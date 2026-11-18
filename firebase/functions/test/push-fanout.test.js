'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { forEachBounded, forEachBestEffort, invalidDeviceRefs } = require('../push-fanout');

test('bounded fanout sends every recipient only once', async () => {
  const ids = Array.from({ length: 50 }, (_, i) => i);
  const touched = [];
  let running = 0; let max = 0;
  await forEachBounded(ids, 8, async (id) => {
    running++;
    max = Math.max(max, running);
    await new Promise(resolve => setImmediate(resolve));
    touched.push(id);
    running--;
  });
  assert.deepEqual(touched.sort((a, b) => a - b), ids);
  assert.ok(max <= 8 && max > 1);
});

test('bounded fanout validates concurrency and skips empty batches', async () => {
  await assert.rejects(() => forEachBounded(['x'], 0, async () => {}), TypeError);
  let called = false;
  await forEachBounded([], 4, async () => { called = true; });
  assert.equal(called, false);
});


test('best-effort delivery attempts every recipient despite individual failures', async () => {
  const attempted = [];
  const result = await forEachBestEffort(Array.from({ length: 41 }, (_, i) => i), 8, async id => {
    attempted.push(id);
    if (id % 10 === 0) throw new Error('device temporarily unavailable');
    await new Promise(resolve => setImmediate(resolve));
  });
  assert.deepEqual(attempted.sort((a, b) => a - b), Array.from({ length: 41 }, (_, i) => i));
  assert.deepEqual(result, { attempted: 41, failed: 5 });
});

test('best-effort delivery skips an empty group and validates concurrency', async () => {
  const result = await forEachBestEffort([], 8, async () => { throw Error('never'); });
  assert.deepEqual(result, { attempted: 0, failed: 0 });
  await assert.rejects(() => forEachBestEffort([1], 0, async () => {}), TypeError);
});

test('FCM cleanup removes only permanently invalid token references', () => {
  const refs = ['a', 'b', 'c', 'd'].map(id => ({ ref: { id } }));
  const outcomes = [
    { success: true },
    { success: false, error: { code: 'messaging/registration-token-not-registered' } },
    { success: false, error: { code: 'messaging/unavailable' } },
    { success: false, error: { code: 'messaging/invalid-registration-token' } }
  ];
  assert.deepEqual(invalidDeviceRefs(refs, outcomes).map(r => r.id), ['b', 'd']);
});

test('FCM cleanup gracefully handles missing result entries', () => {
  assert.deepEqual(invalidDeviceRefs([{ ref: { id: 'a' } }], []), []);
  assert.throws(() => invalidDeviceRefs(null, []), TypeError);
});
