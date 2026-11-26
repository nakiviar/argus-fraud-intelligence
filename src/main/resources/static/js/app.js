import {api} from './api.js';
import {el, node, facts, label, color, agentNames, agentState, elapsed, terminal} from './view.js';
import {architecture} from './architecture.js';
import {timeline} from './timeline.js';
import {evidence, references} from './evidence.js';

let runtime, assessment, snapshot, watcher, preview;
let events = [], scenarioList = [], traceId, busy = false, creating = false, eventsUnavailable = false;
let noticeTimer;
const names = {'normal':'Normal transaction','additional-review':'Additional review',
    'mandatory-policy':'Mandatory policy violation','unavailable-evidence':'Unavailable evidence',
    'invalid-agent-response':'Invalid agent response'};
const stateLabel = status => node('strong',label(status),'state '+color(status));

function notice(message) {
    const box = el('notice');
    clearTimeout(noticeTimer);
    box.textContent = message;
    box.hidden = !message;
    if (message) noticeTimer = setTimeout(() => { box.hidden = true; }, 9000);
}

function route() {
    const page = location.hash.slice(1);
    for (const panel of document.querySelectorAll('.panel')) panel.hidden = panel.id !==
        (document.getElementById(page)?.classList.contains('panel') ? page : 'overview');
    for (const link of document.querySelectorAll('nav a')) {
        if (document.querySelector(link.hash).hidden) link.removeAttribute('aria-current');
        else link.setAttribute('aria-current','page');
    }
    window.scrollTo(0, 0);
}

function renderAssessment() {
    const results = el('decision');
    results.replaceChildren();
    if (assessment) {
        results.append(stateLabel(assessment.decision), facts({
            'Assessment ID':assessment.assessmentId,'Transaction ID':assessment.transactionId,
            'Policy':assessment.policyId + ' · ' + assessment.policyVersion,
            'Reason codes':assessment.reasonCodes.join(', '),'Assessed at':assessment.assessedAt,
            'Investigation required':assessment.investigationRequired ? 'Yes' : 'No',
            ...Object.fromEntries(Object.entries(assessment.evidenceAvailability).map(([key,value]) => [label(key),value]))
        }), node('h3','Verified assessment references'), references(assessment.verifiedEvidenceReferences));
    } else results.append(node('p', 'Run a synthetic assessment to view its recommendation and evidence availability.'));
    el('create').disabled = !assessment?.investigationRequired || busy || creating ||
        snapshot?.assessmentId === assessment.assessmentId;
}

function renderAgents() {
    const root = el('agents');
    root.replaceChildren();
    if (!snapshot) return;
    for (const [type,name] of Object.entries(agentNames)) {
        const result = snapshot.agentResults.find(value => value.agent === type);
        const start = result?.startedAt ?? snapshot.events.find(event => event.type === 'AGENT_STARTED' && event.agent === type)?.occurredAt;
        const status = agentState(snapshot,type);
        const card = node('article',null,'card');
        card.append(node('h2',name),stateLabel(status),facts({
            'Mode':snapshot.mode,'Started':start,'Finished':result?.finishedAt,
            'Elapsed':elapsed(start,result?.finishedAt),'Tool calls':'Unavailable',
            'Validation outcome':status === 'SUCCESS' ? 'Accepted' : status === 'INVALID_RESPONSE' ? 'Rejected' : 'Unavailable',
            'Verified references':result?.verifiedReferences.length
        }));
        root.append(card);
    }
}

function renderEvents() {
    traceId = events.find(event => event.traceId)?.traceId;
    el('trace').replaceChildren(facts({'Investigation ID':snapshot?.investigationId,'Trace ID':traceId,
        'OTLP export':runtime?.traceExportEnabled ? 'Configured' : 'Disabled or unavailable',
        'Token usage':'Unavailable'}));
    el('copy').disabled = !traceId;
    el('retry-events').disabled = !snapshot;
    const grafana = el('grafana');
    grafana.hidden = !runtime?.grafanaUrl;
    if (runtime?.grafanaUrl) grafana.href = runtime.grafanaUrl;
    const target = el('events');
    if (!events.length) {
        target.replaceChildren(node('p', eventsUnavailable ? 'Event retrieval failed. Use Refresh events to retry.' :
            'No investigation events are currently available.'));
        return;
    }
    const table = node('table',null,'event-table');
    const header = table.createTHead().insertRow();
    for (const title of ['Timestamp','Event','Agent','Status','Outcome','Span ID']) header.append(node('th',title));
    const body = table.createTBody();
    for (const event of events) {
        const row = body.insertRow();
        for (const value of [event.occurredAt,event.eventType,agentNames[event.agent],
            event.executionStatus ?? event.investigationStatus,event.outcome,event.spanId]) row.append(node('td',value ?? '—'));
    }
    const wrapper = node('div',null,'table-wrap');
    wrapper.append(table);
    target.replaceChildren(wrapper);
    if (eventsUnavailable) target.prepend(node('p','Showing the last retrieved events. Use Refresh events to retry.'));
}

function renderResilience() {
    const controls = el('controls');
    controls.replaceChildren(node('p', runtime?.mode === 'SIMULATED' ?
        'Failure and delay controls are set when the local application starts. See the conference runbook for commands.' :
        'Synthetic failure controls are available with demo-local.'));
    if (runtime?.simulation) controls.append(facts({'Forced failure agent':runtime.simulation.failureAgent ?? 'Disabled',
        'Delayed agent':runtime.simulation.delayAgent ?? 'Disabled','Configured delay':runtime.simulation.delay}));
    const shortcuts = el('scenario-shortcuts');
    shortcuts.replaceChildren();
    if (runtime?.mode === 'SIMULATED') {
        for (const scenario of scenarioList.filter(item => ['invalid-agent-response','unavailable-evidence','additional-review'].includes(item.scenarioId))) {
            const button = node('button',names[scenario.scenarioId],'button secondary');
            button.addEventListener('click', () => {
                el('scenarios').value = scenario.scenarioId;
                selectScenario();
                location.hash = 'assessment';
            });
            shortcuts.append(button);
        }
    }
    const result = el('resilience-result');
    result.replaceChildren();
    if (!snapshot) {
        result.append(node('p','Open an investigation to inspect outcome and retained evidence.'));
        return;
    }
    result.append(facts({'Outcome':label(snapshot.status),'Original recommendation':snapshot.originalRiskDecision.decision,
        'Verified references preserved':snapshot.verifiedEvidenceReferences.length,
        ...Object.fromEntries(Object.keys(agentNames).map(type => [agentNames[type],label(agentState(snapshot,type))]))}),
        references(snapshot.verifiedEvidenceReferences));
    for (const event of events.filter(event => /TIMEOUT|REJECTED|FAILURE|FALLBACK|LATE|DEADLINE|TRANSITION/.test(event.eventType))) {
        result.append(node('p',[event.occurredAt,event.eventType,agentNames[event.agent],event.outcome].filter(Boolean).join(' · '),'mono'));
    }
}

function render() {
    const decision = snapshot?.originalRiskDecision.decision ?? assessment?.decision;
    el('overview-decision').textContent = decision ?? 'Awaiting assessment';
    el('overview-decision').className = color(decision);
    el('overview-case').textContent = snapshot ? label(snapshot.status) : 'Awaiting a REVIEW case';
    el('overview-case').className = color(snapshot?.status);
    el('overview-evidence').textContent = snapshot?.verifiedEvidenceReferences.length ?? '—';
    renderAssessment();
    architecture(assessment,snapshot,runtime);
    const summary = el('case-summary');
    summary.replaceChildren(node('h2','Investigation summary'));
    if (snapshot) summary.append(stateLabel(snapshot.status),facts({
        'Investigation ID':snapshot.investigationId,'Assessment ID':snapshot.assessmentId,
        'Original simulated recommendation':snapshot.originalRiskDecision.decision,
        'Policy version':snapshot.originalRiskDecision.policyVersion,
        'Mode':snapshot.mode,'Created':snapshot.createdAt,'Completed':snapshot.completedAt
    }));
    else summary.append(node('p','Start a REVIEW investigation or open an existing case.'));
    renderAgents();
    timeline(snapshot,events);
    evidence(snapshot);
    renderEvents();
    renderResilience();
}

function cancelWatcher() {
    if (watcher) {
        watcher.controller.abort();
        clearTimeout(watcher.timer);
        watcher = undefined;
    }
}

function schedule(current, delay) {
    if (watcher === current) current.timer = setTimeout(() => poll(current),delay);
}

async function poll(current) {
    try {
        const record = await api.investigation(current.id,current.controller.signal);
        if (watcher !== current) return;
        snapshot = record;
        current.statusFailures = 0;
        el('case-id').value = record.investigationId;
        try {
            const received = await api.events(current.id,current.controller.signal);
            if (watcher !== current) return;
            events = received;
            eventsUnavailable = false;
            current.eventFailures = 0;
        } catch (error) {
            if (watcher !== current) return;
            eventsUnavailable = true;
            current.eventFailures++;
        }
        render();
        if (!terminal(record.status) || (eventsUnavailable && current.eventFailures < 3)) schedule(current,700);
    } catch (error) {
        if (watcher !== current) return;
        notice(error.message);
        if (++current.statusFailures < 3 && !error.message.includes('HTTP 404') &&
            !error.message.includes('HTTP 400')) schedule(current,1200);
    }
}

function watch(id, initial) {
    cancelWatcher();
    snapshot = initial;
    events = [];
    eventsUnavailable = false;
    const controller = new AbortController();
    watcher = {id,controller,statusFailures:0,eventFailures:0};
    render();
    poll(watcher);
}

async function selectScenario() {
    preview?.abort();
    preview = new AbortController();
    const current = preview;
    cancelWatcher();
    assessment = undefined;
    snapshot = undefined;
    events = [];
    eventsUnavailable = false;
    render();
    const box = el('transaction');
    box.replaceChildren(node('p','Loading synthetic transaction…'));
    el('assess').disabled = true;
    try {
        const value = await api.scenario(el('scenarios').value,current.signal);
        if (preview !== current) return;
        box.replaceChildren(facts({'Transaction ID':value.transactionId,'Source account':value.sourceAccountId,
            'Destination account':value.destinationAccountId,'Amount':value.amount + ' ' + value.currency,
            'Transaction time':value.occurredAt}));
        el('assess').disabled = false;
    } catch (error) {
        if (preview === current) box.replaceChildren(node('p',error.message,'warn'));
    }
}

el('scenarios').addEventListener('change',selectScenario);
el('assessment-form').addEventListener('submit',async event => {
    event.preventDefault();
    if (busy || creating) return;
    const current = preview;
    busy = true;
    el('assess').disabled = true;
    try {
        const result = await api.assess(el('scenarios').value);
        if (preview === current) {
            cancelWatcher();
            assessment = result;
            snapshot = undefined;
            events = [];
            eventsUnavailable = false;
        }
    } catch (error) { notice(error.message); }
    finally {
        busy = false;
        if (preview === current) el('assess').disabled = false;
        render();
    }
});

el('create').addEventListener('click',async () => {
    if (creating || busy || !assessment?.investigationRequired || snapshot?.assessmentId === assessment.assessmentId) return;
    const current = assessment;
    creating = true;
    renderAssessment();
    try {
        const result = await api.create(current.assessmentId);
        if (assessment === current) {
            watch(result.investigationId,result);
            location.hash = 'investigation';
        }
    } catch (error) { notice(error.message); }
    finally { creating = false; renderAssessment(); }
});

el('lookup').addEventListener('submit',event => {
    event.preventDefault();
    const id = el('case-id').value.trim();
    if (!/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(id)) {
        notice('Enter a valid investigation UUID.');
        return;
    }
    if (snapshot?.investigationId !== id) assessment = undefined;
    watch(id,snapshot?.investigationId === id ? snapshot : undefined);
});
el('retry-events').addEventListener('click',() => {
    if (snapshot) watch(snapshot.investigationId,snapshot);
});
el('copy').addEventListener('click',async () => {
    if (!traceId) return;
    try {
        await navigator.clipboard.writeText(traceId);
        notice('Trace ID copied.');
    } catch { notice('Clipboard access unavailable. Select the trace ID to copy it.'); }
});

window.addEventListener('hashchange',route);
route();
render();
api.runtime().then(data => {
    runtime = data;
    el('mode').textContent = data.mode;
    el('verification').textContent = data.liveModelVerification === 'NOT_RECORDED_IN_THIS_PROCESS'
        ? 'Live Foundry verification pending' : 'Live Foundry verification unavailable';
    render();
}).catch(error => {
    el('mode').textContent = 'UNAVAILABLE';
    el('verification').textContent = 'Runtime metadata unavailable';
    notice(error.message);
});
api.scenarios().then(data => {
    scenarioList = data;
    for (const item of data) {
        const option = node('option',names[item.scenarioId] ?? item.scenarioId);
        option.value = item.scenarioId;
        el('scenarios').append(option);
    }
    if (!data.length) {
        el('transaction').replaceChildren(node('p','No synthetic scenarios available.'));
        return;
    }
    el('scenarios').disabled = false;
    el('scenarios').value = data.some(item => item.scenarioId === 'normal') ? 'normal' : data[0].scenarioId;
    selectScenario();
}).catch(error => el('transaction').replaceChildren(node('p',error.message,'warn')));
