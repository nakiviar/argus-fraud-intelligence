# Guía de estudio de ARGUS

Empieza con el [recorrido del código](CODE-WALKTHROUGH.md) y reproduce un caso en demo-local. Repite el recorrido siguiendo cada método enlazado. La ruta de inferencia de Azure se estudia junto con las pruebas locales del adaptador.

## LLM e inferencia

**Fundamento.** Un modelo predice tokens; inferencia es usarlo para obtener una respuesta, con o sin herramientas.

**Uso en ARGUS.** ARGUS limita el uso remoto a investigación. [`generate`](../../src/main/java/pe/jconf/argus/investigation/SpringAiAgentModel.java)

```java
.call().entity(converter)
```

**Lectura del fragmento.** La llamada bloquea y convierte la respuesta al tipo esperado.

**Confusión habitual.** «Generar texto equivale a emitir una decisión.»

**Pregunta de sala.** ¿Dónde entra el modelo?

**Explicación oral.** «En una ronda de investigación, después de REVIEW.»

## Agente, workflow, autonomía y runtime

**Fundamento.** Un agente combina objetivo, modelo y capacidades; un workflow fija las etapas; el runtime ejecuta y limita.

**Uso en ARGUS.** Dos especialistas comparten Java y el modelo; cada uno recibe instrucciones y herramienta propias. [`investigate`](../../src/main/java/pe/jconf/argus/investigation/GraphInvestigationAgent.java)

```java
public AgentDiagnosticResult investigate(InvestigationContext context, ExecutionBudget budget)
```

**Lectura del fragmento.** El presupuesto y el contexto llegan de Java.

**Confusión habitual.** «Cada agente requiere servidor o modelo propio.»

**Pregunta de sala.** ¿Dónde se hospeda el agente?

**Explicación oral.** «En JVM; Foundry hospeda la inferencia.»

## Spring AI, ChatModel, ChatClient y SDK

**Fundamento.** ChatModel es la interfaz de inferencia, ChatClient prepara la solicitud y OpenAiChatModel traduce al SDK Java.

**Uso en ARGUS.** La dimensión de perfil se resuelve al ensamblar el adaptador. [`chatClient`](../../src/main/java/pe/jconf/argus/ai/FoundryConnectivityConfiguration.java)

```java
static ChatClient chatClient(FoundrySettings settings, SpringAiOpenAiHttpClient transport, String accessToken)
```

**Lectura del fragmento.** La firma muestra configuración explícita de transporte y credencial.

**Confusión habitual.** «ARGUS usa un starter Azure heredado de 1.x.»

**Pregunta de sala.** ¿Qué se revisó para 2.0?

**Explicación oral.** «El adaptador OpenAI del SDK y la configuración de Azure v1; verificar APIs al migrar desde 1.x.»

## Prompts y advisors

**Fundamento.** El mensaje system configura el rol; user enuncia la tarea. Un advisor intercepta las solicitudes.

**Uso en ARGUS.** Se aplican instrucciones por agente, presupuesto y política por ronda. [`generate`](../../src/main/java/pe/jconf/argus/investigation/SpringAiAgentModel.java)

```java
.advisors(toolAdvisor, new ModelRoundAdvisor(protection, tool.budget(), httpTimeout))
```

**Lectura del fragmento.** ToolCallingAdvisor mantiene las rondas; ModelRoundAdvisor protege cada ronda.

**Confusión habitual.** «El prompt impone la autorización.»

**Pregunta de sala.** ¿Por qué dos advisors?

**Explicación oral.** «Uno ejecuta el bucle; el otro controla cada llamada remota.»

## Herramientas y ToolContext

**Fundamento.** Tool Calling delega una acción declarada a Java. @Tool es una alternativa declarativa; un ToolCallback permite control explícito.

**Uso en ARGUS.** El callback de solo lectura queda ligado a una investigación. [`call`](../../src/main/java/pe/jconf/argus/investigation/ScopedTool.java)

```java
suppliedContext.getContext().get(SCOPE) != this
```

**Lectura del fragmento.** Se comprueba identidad del callback antes de consultar la fixture.

**Confusión habitual.** «El modelo elige la cuenta que se consulta.»

**Pregunta de sala.** ¿Puede pasar otro identificador?

**Explicación oral.** «Solo se acepta el objeto vacío y el ámbito viene de Java.»

## Structured Output y records

**Fundamento.** La conversión mapea la salida a una estructura Java; la validación semántica comprueba su significado.

**Uso en ARGUS.** La forma de ModelFinding permite separar salida candidata de procedencia. [`generate`](../../src/main/java/pe/jconf/argus/investigation/SpringAiAgentModel.java)

```java
.call().entity(converter)
```

**Lectura del fragmento.** El conversor produce ModelFinding, que se valida después.

**Confusión habitual.** «JSON válido garantiza verdad.»

**Pregunta de sala.** ¿Qué pasa si inventa una cita?

**Explicación oral.** «El validador busca la referencia en las respuestas reales de la herramienta.»

## Transacción, señales y decisión

**Fundamento.** Transaction representa un hecho sintético; RiskSignal es una señal de la política; RiskDecision conserva su resultado y fuentes ausentes.

**Uso en ARGUS.** Las entradas del camino rápido no dependen de texto generado. [`decide`](../../src/main/java/pe/jconf/argus/assessment/RiskDecisionEngine.java)

```java
return new RiskDecision(transaction.transactionId(), decision, POLICY_VERSION, decidedAt, signals, unavailable);
```

**Lectura del fragmento.** Expone versión de política, señales y ausencia en un contrato.

**Confusión habitual.** «No encontrar evidencia implica APPROVE.»

**Pregunta de sala.** ¿Qué resultado domina?

**Explicación oral.** «Una violación obligatoria produce BLOCK; ausencia puede llevar a REVIEW.»

## Contexto, evidencia y reporte

**Fundamento.** InvestigationContext fija el caso, AgentEvidence contiene la observación, AgentExecutionResult conserva estado y metadatos y el informe agrega resultados.

**Uso en ARGUS.** La procedencia y la recomendación original se conservan para revisión. [`run`](../../src/main/java/pe/jconf/argus/investigation/AgentExecution.java)

```java
var executionId = UUID.randomUUID();
```

**Lectura del fragmento.** El runtime controla el ID; la salida candidata entra al validador y el informe conserva el contexto.

**Confusión habitual.** «El ID emitido por el LLM es autoritativo.»

**Pregunta de sala.** ¿Puede un agente cambiar REVIEW?

**Explicación oral.** «El agregador recibe la decisión original como parte del contexto.»

## Procedencia

**Fundamento.** Es la relación entre una cita y el resultado del callback que la emitió.

**Uso en ARGUS.** Se valida dentro de la ejecución que obtuvo la evidencia. [`observationFor`](../../src/main/java/pe/jconf/argus/investigation/ScopedTool.java)

```java
return returned.stream().filter(reply -> reply.reference().equals(reference))
```

**Lectura del fragmento.** La comparación usa la referencia completa; EvidenceValidator.validate decide si es aceptable.

**Confusión habitual.** «Conocer el recordId basta para citar.»

**Pregunta de sala.** ¿Qué ocurre si el proveedor no devuelve dato?

**Explicación oral.** «Se representa ausencia; Java evita otorgar una referencia ficticia.»

## Monolito modular, inversión e inyección

**Fundamento.** Una JVM aloja módulos separados; el consumidor depende de una interfaz y recibe implementación por constructor o bean.

**Uso en ARGUS.** El proveedor puede cambiar sin cambiar la política. [`findEvidence`](../../src/main/java/pe/jconf/argus/assessment/GraphDataProvider.java)

```java
Optional<ProviderEvidence> findEvidence(Transaction transaction);
```

**Lectura del fragmento.** El puerto comunica evidencia disponible o ausente; AssessmentConfiguration conecta la fixture.

**Confusión habitual.** «Separar módulos obliga a microservicios.»

**Pregunta de sala.** ¿Cómo se añade almacenamiento?

**Explicación oral.** «Mediante un adaptador al puerto con controles nuevos.»

## Fan-out/Fan-in, lifecycle y REST asíncrono

**Fundamento.** Un coordinador reparte trabajo y reúne resultados; HTTP responde primero con la ubicación del resultado.

**Uso en ARGUS.** El caso reúne dos agentes independientes, con concurrencia y admisión acotadas. [`fanOut / fanIn`](../../src/main/java/pe/jconf/argus/investigation/InvestigationOrchestrator.java)

```java
private boolean fanIn(InvestigationContext context, AgentExecutionStatus missingStatus)
```

**Lectura del fragmento.** La coordinación combina resultados disponibles y representa faltantes. CompletableFuture opera con workers propios y el repositorio coordina cambios.

**Confusión habitual.** «202 significa informe listo.»

**Pregunta de sala.** ¿Cómo se observa consistencia?

**Explicación oral.** «GET devuelve snapshots con contratos inmutables.»

## Circuit Breaker y Bulkhead

**Fundamento.** El circuito reacciona a fallos; el bulkhead reserva capacidad.

**Uso en ARGUS.** Ambos agentes comparten un dominio de fallo remoto y tienen límites por tipo. [`execute`](../../src/main/java/pe/jconf/argus/investigation/RemoteModelProtection.java)

```java
return breaker.executeSupplier(() -> {
```

**Lectura del fragmento.** El breaker envuelve el transporte dentro de los permisos; clasificación evita contar errores de aplicación.

**Confusión habitual.** «Abrir el circuito cancela solicitudes en curso.»

**Pregunta de sala.** ¿Qué pasa al saturarse?

**Explicación oral.** «Se registra UNAVAILABLE y se preservan resultados válidos.»

## Deadlines, TimeLimiter, HTTP y cancelación

**Fundamento.** El deadline limita el caso; TimeLimiter limita esperar un resultado; el timeout de HTTP limita el transporte.

**Uso en ARGUS.** Los límites de 35/25/10 s reducen cuánto espera la investigación; el worker puede finalizar después. [`call`](../../src/main/java/pe/jconf/argus/investigation/ScopedTool.java)

```java
budget.requireTime();
```

**Lectura del fragmento.** La herramienta verifica el presupuesto antes y después de consultar; el coordinador finaliza la vista y pide interrupción.

**Confusión habitual.** «Timeout demuestra cancelación en Azure.»

**Pregunta de sala.** ¿Cuándo libera capacidad?

**Explicación oral.** «Al salir la operación protegida.»

## Retries, fallback y contención tardía

**Fundamento.** Reintentar demanda presupuesto y puede duplicar trabajo; fallback conserva resultados útiles.

**Uso en ARGUS.** El SDK configura cero reintentos y se protege el informe publicado. [`aggregate`](../../src/main/java/pe/jconf/argus/investigation/EvidenceAggregator.java)

```java
Aggregation aggregate(InvestigationContext context, List<AgentExecutionResult> results, Instant aggregatedAt)
```

**Lectura del fragmento.** El agregador recibe resultados publicados, no futuros tardíos; publish controla aceptación.

**Confusión habitual.** «Fallback siempre usa otro proveedor.»

**Pregunta de sala.** ¿Qué pasa con un solo éxito?

**Explicación oral.** «PARTIAL con evidencia válida, preservando REVIEW.»

## Métricas, logs, trazas, Micrometer y OpenTelemetry

**Fundamento.** Métricas resumen, logs describen eventos y trazas conectan operaciones. Micrometer instrumenta; OpenTelemetry representa y exporta spans; OTLP los transporta.

**Uso en ARGUS.** Se ve paralelismo y diagnósticos sin copiar prompts. Spring AI aporta observaciones de las rutas que ejecuta. [`operation`](../../src/main/java/pe/jconf/argus/observability/ArgusTelemetry.java)

```java
return Observation.createNotStarted(name, observations);
```

**Lectura del fragmento.** Se crea una observación de aplicación. Tempo recibe trazas opcionales y Grafana permite consultarlas.

**Confusión habitual.** «En local hay tokens reales.»

**Pregunta de sala.** ¿Qué ve el ponente sin Docker?

**Explicación oral.** «Eventos y los ejemplos capturados con el receptor local.»

## Trace ID, Span ID, contexto y links

**Fundamento.** El trace ID agrupa operaciones, el span ID distingue una; un link relaciona trazas sin cambiar parentesco.

**Uso en ARGUS.** Los agentes son hermanos y la evaluación puede vincularse a la investigación. [`linkAssessment`](../../src/main/java/pe/jconf/argus/observability/ArgusTelemetry.java)

```java
Span.current().addLink(assessment);
```

**Lectura del fragmento.** Añade un vínculo al contexto muestreado; el orquestador abre el scope explícitamente en workers.

**Confusión habitual.** «ThreadLocal se copia solo.»

**Pregunta de sala.** ¿Por qué un link?

**Explicación oral.** «La evaluación y la solicitud posterior pueden ser trazas separadas.»

## Privacidad y auditoría

**Fundamento.** La telemetría explica el runtime; el registro de negocio conserva el resultado evaluado.

**Uso en ARGUS.** Se limitan atributos exportados y se evita registrar prompts o credenciales. [`events`](../../src/main/java/pe/jconf/argus/observability/ArgusTelemetry.java)

```java
return history.stream().filter(event -> investigationId.equals(event.investigationId())).toList();
```

**Lectura del fragmento.** Filtra un historial acotado; PrivacySpanExporter controla la salida de spans y AuditRecorder sirve a otro propósito.

**Confusión habitual.** «Actuator es una auditoría regulatoria.»

**Pregunta de sala.** ¿Se usa PII?

**Explicación oral.** «Solo fixtures sintéticas.»

## Foundry, Azure OpenAI y Microsoft Entra ID

**Fundamento.** Foundry organiza recursos y proyectos; el despliegue elige modelo y versión; Entra emite el bearer de identidad autorizada.

**Uso en ARGUS.** Se usa endpoint del recurso y nombre de despliegue para la ruta Chat Completions v1. [`chatClient`](../../src/main/java/pe/jconf/argus/ai/FoundryConnectivityConfiguration.java)

```java
static ChatClient chatClient(FoundrySettings settings, SpringAiOpenAiHttpClient transport, String accessToken)
```

**Lectura del fragmento.** La aplicación recibe el token por entorno y construye el cliente; el proyecto no es el endpoint de inferencia usado.

**Confusión habitual.** «Configurar Azure prueba conectividad.»

**Pregunta de sala.** ¿Qué faltaría en producción?

**Explicación oral.** «Identidad administrada, entorno autorizado, almacenamiento, políticas, pruebas y aprobación operativa.»


## Lecturas para profundizar

- [Spring AI 2.0.1](https://github.com/spring-projects/spring-ai/tree/v2.0.1)
- [Tool Calling](../tool-calling.md), [Structured Output](../structured-output.md), [dominio](../domain-model.md) y [trazas](../distributed-tracing.md).
- [Azure e identidad](../azure-integration.md) y [comparación implementada y propuesta](ARCHITECTURE-AS-BUILT.md).


## Local inference and LGTM

**Adaptador Ollama.** `OllamaConfiguration.ollamaChatClient` conserva la abstracción ChatModel y apunta al origen local. Las herramientas y el validador son los mismos que en Foundry. Consulta [la guía de Ollama](../ollama-integration.md).
