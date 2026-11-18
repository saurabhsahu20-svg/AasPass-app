'use strict';

/**
 * Run best-effort network delivery to many recipients with bounded concurrency.
 * Sequential sends make groups unnecessarily slow; unbounded Promise.all can
 * exhaust a function instance under burst load.
 */
async function forEachBounded(items, concurrency, send) {
  if (!Array.isArray(items) || !Number.isInteger(concurrency) || concurrency < 1 ||
      concurrency > 16 || typeof send !== 'function') {
    throw new TypeError('Invalid delivery batch');
  }
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(items.length, concurrency) }, async () => {
    while (next < items.length) {
      const index = next++;
      await send(items[index], index);
    }
  }));
}
/** Never let one offline device prevent delivery attempts to other group members.
 * Failures are counted, and caller decides how to report them without logging
 * message content, personal identifiers or device registration tokens.
 */
async function forEachBestEffort(items, concurrency, send) {
  let failures = 0;
  await forEachBounded(items, concurrency, async (item, index) => {
    try { await send(item, index); }
    catch (_error) { failures++; }
  });
  return { attempted: items.length, failed: failures };
}

/** Return only the device refs for permanently invalid FCM tokens. */
function invalidDeviceRefs(deviceDocs, responses) {
  if (!Array.isArray(deviceDocs) || !Array.isArray(responses)) {
    throw new TypeError('Invalid notification responses');
  }
  const invalidCodes = new Set([
    'messaging/registration-token-not-registered',
    'messaging/invalid-registration-token'
  ]);
  const refs = [];
  for (let i = 0; i < Math.min(deviceDocs.length, responses.length); i++) {
    if (!responses[i]?.success && invalidCodes.has(responses[i]?.error?.code) &&
        deviceDocs[i]?.ref) refs.push(deviceDocs[i].ref);
  }
  return refs;
}

module.exports = { forEachBounded, forEachBestEffort, invalidDeviceRefs };
