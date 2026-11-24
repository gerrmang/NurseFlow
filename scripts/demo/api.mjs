export class DemoError extends Error {}

export function localBase(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password ||
      url.search || url.hash || url.pathname !== '/') {
    throw new DemoError('시연 API는 http://127.0.0.1:<port>만 사용할 수 있습니다.');
  }
  return url.origin;
}

export function createApi(baseUrl, token, transport = fetch) {
  const base = localBase(baseUrl);
  return async (method, path, body, extraHeaders = {}) => {
    if (!path.startsWith('/api/v1/') || path.includes('..') || path.includes('\\')) {
      throw new DemoError('허용되지 않은 API 경로입니다.');
    }
    let response;
    try {
      response = await transport(base + path, {
        method, redirect: 'error', signal: AbortSignal.timeout(15_000),
        headers: { ...(token ? { authorization: `Bearer ${token}` } : {}),
          ...(body === undefined ? {} : { 'content-type': 'application/json' }), ...extraHeaders },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
    } catch {
      // Never log the request, tokens, response body or an upstream error message.
      throw new DemoError(`${method} 요청 결과를 확인하지 못했습니다. 자동 재전송하지 않습니다. 재실행하면 목록부터 확인합니다.`);
    }
    if (!response.ok) {
      throw new DemoError(`${method} API 실패 (HTTP ${response.status}). 상태를 확인한 뒤 재실행하세요.`);
    }
    let data;
    try { data = response.status === 204 ? null : await response.json(); }
    catch { throw new DemoError(`${method} API 응답 형식이 올바르지 않습니다.`); }
    return { data, etag: response.headers.get('etag') };
  };
}

export function requireDemo(condition, message) {
  if (!condition) throw new DemoError(message);
}
