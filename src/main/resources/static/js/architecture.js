import {el, label, color, agentState, svg, terminal} from './view.js';

export function architecture(assessment, snapshot, runtime) {
    const diagram = svg('svg', {viewBox:'0 0 1120 390', role:'img', 'aria-label':'Deterministic Hot Path and concurrent Investigation Path'});
    diagram.append(svg('rect',{x:5,y:5,width:1110,height:155,rx:9,class:'zone'}),
        svg('rect',{x:5,y:175,width:1110,height:210,rx:9,class:'zone'}),
        svg('text',{x:23,y:30,class:'zone-title'},'DETERMINISTIC HOT PATH'),
        svg('text',{x:23,y:200,class:'zone-title'},'INVESTIGATION PATH · REVIEW ONLY'));
    const marker = svg('marker',{id:'arrow',viewBox:'0 0 10 10',refX:9,refY:5,markerWidth:6,markerHeight:6,orient:'auto'});
    marker.append(svg('path',{d:'M0 0 10 5 0 10Z',fill:'#67819d'}));
    const defs = svg('defs',{});
    defs.append(marker);
    diagram.append(defs);
    for (const d of ['M212 88H235V62H254','M212 88H254','M212 88H235V114H254','M442 62H468V82H490','M442 90H490','M442 114H468V97H490','M682 87H730','M825 117V146H120V225','M208 252H226V242H244','M208 252H226V307H244','M420 242H442','M420 307H442','M615 242H637V264H658','M615 307H637V277H658','M810 270H832','M952 270H972']) {
        diagram.append(svg('path',{d,class:'edge','marker-end':'url(#arrow)'}));
    }
    const decision = assessment?.decision ?? snapshot?.originalRiskDecision.decision;
    const available = source => assessment?.evidenceAvailability[source] === 'AVAILABLE' ? 'SUCCESS' :
        assessment?.evidenceAvailability[source] ?? 'WAITING';
    const aggregated = snapshot?.events.some(event => event.type === 'EVIDENCE_AGGREGATED');
    const started = snapshot?.events.some(event => event.type === 'INVESTIGATION_STARTED');
    const entries = [
        [24,61,188,54,'Synthetic transaction', decision ? 'SUCCESS' : 'WAITING'],
        [254,48,188,27,'Rules Engine',available('RULES')],
        [254,75,188,27,'Graph Data Provider',available('GRAPH_DATA')],
        [254,102,188,27,'Simulated ML Predictor',available('SYNTHETIC_ML')],
        [490,61,192,54,'Risk Decision Engine',decision ? 'SUCCESS' : 'WAITING'],
        [730,61,190,56,'Risk recommendation',decision ?? 'WAITING'],
        [24,225,184,54,'Investigation Orchestrator',terminal(snapshot?.status) ? snapshot.status : started ? 'RUNNING' : 'WAITING'],
        [244,215,176,54,'Graph Agent',agentState(snapshot,'GRAPH')],
        [244,280,176,54,'Behavioral Agent',agentState(snapshot,'BEHAVIORAL')],
        [442,215,173,54,'Scoped graph tools',null],
        [442,280,173,54,'Scoped behavioral tools',null],
        [658,242,152,56,'Evidence Validator',null],
        [832,242,120,56,'Evidence Aggregator',aggregated ? 'SUCCESS' : 'WAITING'],
        [972,242,126,56,'Investigation Report',snapshot?.report ? snapshot.status : 'WAITING'],
        [658,323,294,43,({AZURE:'Microsoft Foundry / shared deployment', OLLAMA:'Ollama / shared local model',
            SIMULATED:'Local simulation / model-free'})[snapshot?.mode ?? runtime?.mode] ?? 'Provider unavailable',null]
    ];
    for (const [x,y,w,h,name,state] of entries) {
        const group = svg('g',{class:'node '+color(state)});
        group.append(svg('rect',{x,y,width:w,height:h,rx:5}),
            svg('text',{x:x+8,y:y+(h<30?18:20),class:'name','font-size':w<153?11:12},name));
        if (state) group.append(svg('text',{x:h<30?x+w-7:x+8,y:y+(h<30?18:39),
            'text-anchor':h<30?'end':'start',class:'node-state','font-size':h<30?8:11},label(state)));
        diagram.append(group);
    }
    el('architecture').replaceChildren(diagram);
}
