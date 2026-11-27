import {el, facts, node, label, agentNames} from './view.js';

export function references(items) {
    const list = node('div');
    for (const ref of items ?? []) {
        const group = node('div', null, 'reference');
        group.append(facts(Object.fromEntries(Object.entries(ref).map(([key,value]) => [
            ({recordId:'Source record', type:'Evidence type', toolExecutionId:'Tool execution ID',
              source:'Provider', referenceId:'Reference ID'})[key] ?? key,
            typeof value === 'object' ? JSON.stringify(value) : value
        ]))));
        list.append(group);
    }
    if (!list.childElementCount) list.append(node('p','No verified references reported.'));
    return list;
}

export function evidence(snapshot) {
    const target = el('evidence-content');
    target.replaceChildren();
    if (!snapshot?.agentResults.length) {
        target.append(node('p','Agent evidence will appear when an execution result is available.'));
        return;
    }
    for (const result of snapshot.agentResults) {
        const card = node('article',null,'card');
        card.append(node('h2',agentNames[result.agent]),
            facts({'Execution ID':result.executionId,'Execution outcome':label(result.status),
                'Validation outcome':result.status === 'SUCCESS' ? 'Accepted' :
                    result.status === 'INVALID_RESPONSE' ? 'Rejected' : 'Unavailable',
                'Rejected candidate references':'Unavailable'}),
            node('h3',snapshot.mode === 'SIMULATED' ? 'Simulated observation' : 'Model-generated observation'),
            node('p',result.evidence?.observation ?? 'Observation unavailable.', 'finding'),
            node('h3','Verified source references'), references(result.verifiedReferences));
        target.append(card);
    }
}
