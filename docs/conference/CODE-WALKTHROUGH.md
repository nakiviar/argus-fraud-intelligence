# Recorrido del código

Usa **Additional review** en el navegador. `demo-local` ejercita toda la ruta con simulación; la invocación Spring AI requiere `demo-azure` o el modelo controlado de pruebas.

| Paso | Fuente y método | Datos y comportamiento |
|---|---|---|
| Navegador | [resources/static/js/app.js](../../src/main/resources/static/js/app.js) · `manejador submit / api.assess` | Envía scenarioId por POST; api.js define el contrato Fetch. |
| REST | [java/pe/jconf/argus/assessment/web/RiskAssessmentController.java](../../src/main/java/pe/jconf/argus/assessment/web/RiskAssessmentController.java) · `assess` | Valida que el cuerpo contenga solo scenarioId. |
| Evaluación | [java/pe/jconf/argus/assessment/RiskAssessmentService.java](../../src/main/java/pe/jconf/argus/assessment/RiskAssessmentService.java) · `assess` | Resuelve la fixture, consulta reglas y proveedores y almacena la evaluación. |
| Política | [java/pe/jconf/argus/assessment/RiskDecisionEngine.java](../../src/main/java/pe/jconf/argus/assessment/RiskDecisionEngine.java) · `decide` | La regla obligatoria domina; señales o insumos ausentes disparan REVIEW. |
| Admisión | [java/pe/jconf/argus/investigation/InvestigationController.java](../../src/main/java/pe/jconf/argus/investigation/InvestigationController.java) · `create` | Recibe assessmentId; devuelve Location y 202 para un caso nuevo. |
| Fan-out | [java/pe/jconf/argus/investigation/InvestigationOrchestrator.java](../../src/main/java/pe/jconf/argus/investigation/InvestigationOrchestrator.java) · `start → registerAndStart → fanOut` | Admite un REVIEW almacenado, crea contexto y distribuye a los workers. |
| Agentes | [java/pe/jconf/argus/investigation/GraphInvestigationAgent.java](../../src/main/java/pe/jconf/argus/investigation/GraphInvestigationAgent.java) · `investigate` | Construye el callback de grafo; BehavioralInvestigationAgent.investigate configura el de predicción. |
| Ejecución | [java/pe/jconf/argus/investigation/AgentExecution.java](../../src/main/java/pe/jconf/argus/investigation/AgentExecution.java) · `run` | Recibe instrucciones, callback, contexto y presupuesto. En offline selecciona SimulatedAgentModel.generate. |
| Invocación Spring AI | [java/pe/jconf/argus/investigation/SpringAiAgentModel.java](../../src/main/java/pe/jconf/argus/investigation/SpringAiAgentModel.java) · `generate` | En Azure envía mensajes y registra advisors, callback y ToolContext; convierte el resultado a ModelFinding. |
| Ronda remota | [java/pe/jconf/argus/investigation/ModelRoundAdvisor.java](../../src/main/java/pe/jconf/argus/investigation/ModelRoundAdvisor.java) · `adviseCall` | Aplica presupuesto de transporte y RemoteModelProtection.execute a cada ronda; FoundryConnectivityConfiguration.chatClient configura SDK y modelo. |
| Herramienta | [java/pe/jconf/argus/investigation/ScopedTool.java](../../src/main/java/pe/jconf/argus/investigation/ScopedTool.java) · `call(String, ToolContext)` | Valida identidad del ámbito, presupuesto y argumentos vacíos. Emite referencia para evidencia disponible. |
| Validación | [java/pe/jconf/argus/investigation/EvidenceValidator.java](../../src/main/java/pe/jconf/argus/investigation/EvidenceValidator.java) · `validate` | Contrasta las citas con resultados del callback para construir AgentEvidence. |
| Fan-in | [java/pe/jconf/argus/investigation/InvestigationOrchestrator.java](../../src/main/java/pe/jconf/argus/investigation/InvestigationOrchestrator.java) · `publish → fanIn` | Entrega resultados admisibles y cierra el caso con presupuesto global. |
| Informe | [java/pe/jconf/argus/investigation/EvidenceAggregator.java](../../src/main/java/pe/jconf/argus/investigation/EvidenceAggregator.java) · `aggregate` | Genera InvestigationReport, conserva REVIEW y deduplica referencias verificadas. |
| Consulta | [java/pe/jconf/argus/investigation/InvestigationController.java](../../src/main/java/pe/jconf/argus/investigation/InvestigationController.java) · `get / events` | Devuelve snapshot o eventos acotados y correlacionables. |
| Render | [resources/static/js/app.js](../../src/main/resources/static/js/app.js) · `poll → render` | Actualiza arquitectura, resultados y timeline desde snapshot y eventos. evidence.js separa observaciones de referencias. |

## Rama de timeout

`InvestigationOrchestrator.fanOut` programa el límite global y conecta la vista de resultado a TimeLimiter. Si Graph consume su presupuesto, `publish` registra el resultado TIMEOUT y se solicita la interrupción del worker. `fanIn` conserva Behavioral si terminó a tiempo. `EvidenceAggregator.aggregate` produce PARTIAL y conserva las referencias aceptadas. Una respuesta tardía no reemplaza el resultado publicado; la contención puede emitir un evento adicional.

## Cinco fragmentos para proyectar

1. `RiskDecisionEngine.decide`, precedencia de política y evidencia ausente.
2. `InvestigationOrchestrator.fanOut` y `fanIn`, presupuesto y paralelismo.
3. `SpringAiAgentModel.generate`, registro de advisors y conversión.
4. `ScopedTool.call`, control de ámbito.
5. `EvidenceValidator.validate`, procedencia comprobada.

