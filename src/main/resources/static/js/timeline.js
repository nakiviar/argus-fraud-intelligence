import {el, node, svg, instant, agentNames, color} from './view.js';

export function timeline(snapshot, telemetry) {
    const box = el('waterfall');
    if (!snapshot) {
        box.replaceChildren(node('p', 'Open an investigation to view measured operations.'));
        return;
    }
    const markers = snapshot.events.filter(event =>
        ['INVESTIGATION_STARTED','EVIDENCE_AGGREGATED','INVESTIGATION_COMPLETED'].includes(event.type))
        .map(event => ({name:event.type.replaceAll('_',' '), at:event.occurredAt}));
    const rows = snapshot.agentResults.map(result => ({name:agentNames[result.agent], start:result.startedAt,
        end:result.finishedAt, state:result.status}));
    const failures = telemetry.filter(event =>
        /TIMEOUT|REJECTED|FAILURE|FALLBACK|LATE|DEADLINE|TRANSITION/.test(event.eventType));
    const times = [snapshot.createdAt, ...markers.map(marker => marker.at),
        ...rows.flatMap(row => [row.start,row.end]), ...failures.map(event => event.occurredAt)].map(instant).filter(value => value !== null);
    const first = times.reduce((a,b) => a < b ? a : b);
    const last = times.reduce((a,b) => a > b ? a : b);
    const duration = Number(last - first) / 1e6;
    const offset = value => Number(instant(value) - first)/1e6;
    const x = value => 228 + (duration ? offset(value) / duration : 0)*745;
    const height = 53 + (rows.length + markers.length + (failures.length ? 1 : 0))*30;
    const graph = svg('svg',{viewBox:'0 0 1010 '+height,role:'img','aria-label':'Measured investigation waterfall'});
    for (let i=0;i<=4;i++) {
        const left = 228+i*745/4;
        graph.append(svg('line',{x1:left,x2:left,y1:30,y2:height-10,class:'timeline-grid'}),
            svg('text',{x:left,y:17,class:'timeline-tick','text-anchor':i===4?'end':'start'},(duration*i/4).toFixed(3)+' ms'));
    }
    let y=47;
    for (const row of rows) {
        const start=x(row.start), end=x(row.end);
        const mark = end-start >= 1 ? svg('rect',{x:start,y:y-7,width:end-start,height:11,rx:3,class:'timeline-mark '+color(row.state)}) :
            svg('circle',{cx:start,cy:y-2,r:4,class:'timeline-mark '+color(row.state)});
        mark.append(svg('title',{},row.start+' → '+row.end));
        graph.append(svg('text',{x:5,y,class:'timeline-label'},row.name),mark);
        y+=30;
    }
    for (const marker of markers) {
        graph.append(svg('text',{x:5,y,class:'timeline-label'},marker.name),
            svg('circle',{cx:x(marker.at),cy:y-4,r:4,fill:'#38bdf8'}));
        y+=30;
    }
    if (failures.length) {
        graph.append(svg('text',{x:5,y,class:'timeline-label'},'Resilience / validation'));
        for (const failure of failures) {
            const mark=svg('circle',{cx:x(failure.occurredAt),cy:y-4,r:4,fill:'#fbbf24'});
            mark.append(svg('title',{},failure.eventType+' · '+failure.occurredAt));
            graph.append(mark);
        }
    }
    box.replaceChildren(graph);
}
