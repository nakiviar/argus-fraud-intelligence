# Guion de demo en vivo

**Duración aproximada: 13 minutos.** Preabre el dashboard, el IDE en los cinco fragmentos y una segunda instancia en el puerto 8081. Usa [DEMO-RUNBOOK](DEMO-RUNBOOK.md).

**Plan B, predeterminado.** Ejecuta demo-local. Identifica como simulada la generación, el grafo y el predictor; muestra Java, HTTP, procedencia, tiempos y trazas locales reales.

**Plan A, condicional.** Si el diagnóstico en vivo autorizado y una investigación real pasaron antes de subir al escenario, usa demo-azure para Additional review en los minutos 3–8. El estado AZURE solo identifica el camino configurado. Para el minuto 9 cambia explícitamente a la instancia demo-local de timeout. Para trazas, usa Grafana solo tras verificar la UI; si falla Foundry cambia a Plan B y repite el caso.

| Minuto | Acción y pantalla | Qué decir | Resultado observable | Método | Transición | Recuperación |
|---|---|---|---|---|---|---|
| 0–1 | Abrir Overview | Presentar la tesis y el indicador de modo | Diagrama y SIMULATED o AZURE | Architecture diagram | Ir a evaluación | Captura 1920×1080 |
| 1–2 | Mostrar caso Normal | La entrada es una fixture | Importe y origen sintéticos | RiskAssessmentController.assess | Ejecutar política | POST desde PowerShell |
| 2–3 | Ejecutar Normal y Mandatory policy | La política obligatoria domina | APPROVE y BLOCK | RiskDecisionEngine.decide | Elegir REVIEW | Mostrar el orden de ramas |
| 3–4 | Evaluar Additional review | REVIEW abre investigación separada | Motivos y política original | RiskAssessmentService.assess | Pulsar Start investigation | Crear por API |
| 4–5 | Abrir Multi-Agent Investigation | Fan-out a dos especialistas | Dos resultados y waterfall | InvestigationOrchestrator.fanOut | Pasar a herramientas | Captura de timeline |
| 5–6 | Mostrar el callback | Cada agente tiene una herramienta acotada | ToolContext y argumento vacío | ScopedTool.call | Abrir adaptador | Diagnóstico local de Graph |
| 6–7 | Mostrar la ronda Spring AI | El advisor ejecuta el bucle y Java lo limita | Registro de callback y advisor | SpringAiAgentModel.generate | Seguir conversión | Prueba AgentEngineeringTests |
| 7–8 | Mostrar Structured Output | Un contrato corto, convertido y validado | ModelFinding y salida tipada | EvidenceValidator.validate | Abrir Evidence Explorer | Mostrar la comparación en Java |
| 8–9 | Inspeccionar procedencia | La referencia completa debe coincidir | Referencia verificada y observación | ScopedTool.observationFor | Pasar a fallo | Captura del explorador |
| 9–10 | Abrir el proceso local con timeout | Timeout limita la espera; se pide interrupción | PARTIAL y evidencia del otro agente | InvestigationOrchestrator.publish | Ver eventos | Caso invalid-agent-response |
| 10–11 | Abrir Observability | Los eventos reflejan el resultado publicado | AGENT_TIMEOUT y original REVIEW | ArgusTelemetry.resilience | Buscar correlación | JSON de eventos conservado |
| 11–12 | Mostrar traza | Los workers abren scopes y sus spans son hermanos | Trace ID o archivo de spans | InvestigationOrchestrator.inScope | Cerrar con límites | Abrir span-example.json |
| 12–13 | Volver al informe | Agents investigate. Deterministic code decides. | REVIEW original y evidencia preservada | EvidenceAggregator.aggregate | Preguntas | Captura de caso parcial |

