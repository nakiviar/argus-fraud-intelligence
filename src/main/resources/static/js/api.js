async function request(path, {signal, body} = {}) {
    const response = await fetch('/api/v1' + path, {
        signal, cache: 'no-store', headers: body ? {'Content-Type': 'application/json'} : {},
        ...(body && {method: 'POST', body: JSON.stringify(body)})
    });
    if (!response.ok) {
        const reason = {400: 'Invalid request', 404: 'Record not found', 409: 'Assessment is ineligible',
            429: 'Investigation capacity reached', 503: 'Service unavailable'}[response.status] || 'Request failed';
        throw new Error(reason + ' (HTTP ' + response.status + ').');
    }
    if (!response.headers.get('content-type')?.includes('application/json')) {
        throw new Error('The backend returned an unexpected response.');
    }
    return response.json();
}

export const api = {
    runtime: () => request('/dashboard/runtime'),
    scenarios: () => request('/scenarios'),
    scenario: (id, signal) => request('/scenarios/' + encodeURIComponent(id), {signal}),
    assess: scenarioId => request('/assessments', {body: {scenarioId}}),
    create: assessmentId => request('/investigations', {body: {assessmentId}}),
    investigation: (id, signal) => request('/investigations/' + encodeURIComponent(id), {signal}),
    events: (id, signal) => request('/investigations/' + encodeURIComponent(id) + '/events', {signal})
};
