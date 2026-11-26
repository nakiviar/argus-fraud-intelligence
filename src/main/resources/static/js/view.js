export const el = (id) => document.getElementById(id);
export const label = value => value == null ? 'Unavailable' : String(value).replaceAll('_', ' ');
export function node(name, text, cls) {
    const item = document.createElement(name);
    if (text != null) item.textContent = text;
    if (cls) item.className = cls;
    return item;
}
export function facts(values) {
    const list = node('dl', null, 'facts');
    for (const [key, value] of Object.entries(values)) {
        list.append(node('dt', key), node('dd', value ?? 'Unavailable'));
    }
    return list;
}
export function color(value) {
    if (['APPROVE','SUCCESS','COMPLETED'].includes(value)) return 'good';
    if (['REVIEW','PARTIAL','TIMEOUT','UNAVAILABLE','RUNNING','AGGREGATING'].includes(value)) return 'warn';
    if (['BLOCK','FAILED','ERROR','INVALID_RESPONSE'].includes(value)) return 'bad';
    return 'dim';
}
export const agentNames = {GRAPH: 'Graph agent', BEHAVIORAL: 'Behavioral agent'};
export const terminal = status => ['COMPLETED','PARTIAL','FAILED'].includes(status);
export function agentState(snapshot, type) {
    return snapshot?.agentResults.find(result => result.agent === type)?.status ??
        (snapshot?.events.some(event => event.type === 'AGENT_STARTED' && event.agent === type)
            ? 'RUNNING' : 'WAITING');
}
export function svg(tag, attributes, text) {
    const result = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [key, value] of Object.entries(attributes)) result.setAttribute(key, value);
    if (text != null) result.textContent = text;
    return result;
}
export function instant(value) {
    const parts = /^(.*?)(?:\.(\d{1,9}))?Z$/.exec(value ?? '');
    return parts && BigInt(Date.parse(parts[1] + 'Z')) * 1000000n + BigInt((parts[2] ?? '').padEnd(9, '0'));
}
export function elapsed(start, end) {
    const first = instant(start), last = instant(end);
    return first == null || last == null ? 'Unavailable' : (Number(last - first) / 1e6).toFixed(3) + ' ms';
}
