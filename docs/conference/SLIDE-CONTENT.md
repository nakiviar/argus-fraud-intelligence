# Contenido de las 16 diapositivas

## 1. Apertura

**En pantalla.** Agents investigate. Deterministic code decides.

**Diagrama.** Dos carriles

**Notas.** Explica alcance sintético.

**Transición.** Continúa con «Evidencia distribuida».

**Evidencia.** Arquitectura AS-BUILT. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 2. Evidencia distribuida

**En pantalla.** Reglas · grafo · predictor · fuentes ausentes

**Diagrama.** Fuentes hacia política

**Notas.** Grafo y predictor usan fixtures.

**Transición.** Continúa con «Servicio vs agente».

**Evidencia.** RiskAssessmentTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 3. Servicio vs agente

**En pantalla.** Servicio determinista · agente con objetivo y capacidades

**Diagrama.** Capacidad y límite

**Notas.** El LLM es opcional en el prototipo.

**Transición.** Continúa con «Concurrencia».

**Evidencia.** AgentEngineeringTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 4. Concurrencia

**En pantalla.** Fan-out / Fan-in · dos especialistas

**Diagrama.** Workers y join

```java
private boolean fanIn(InvestigationContext context, AgentExecutionStatus missingStatus)
```

**Notas.** Una operación parcial conserva valor.

**Transición.** Continúa con «Dos caminos».

**Evidencia.** InvestigationOrchestratorTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

## 5. Dos caminos

**En pantalla.** Hot Path decide · Investigation Path documenta

**Diagrama.** Secuencia de dos etapas

**Notas.** REVIEW es la única puerta.

**Transición.** Continúa con «Resiliencia».

**Evidencia.** InvestigationApiTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 6. Resiliencia

**En pantalla.** 35 s · 25 s · 10 s

**Diagrama.** Capas de presupuesto

**Notas.** Distingue espera y cancelación.

**Transición.** Continúa con «Spring AI 2.0».

**Evidencia.** ResilienceTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://resilience4j.readme.io/docs/getting-started).

## 7. Spring AI 2.0

**En pantalla.** ChatClient → advisors → OpenAiChatModel

**Diagrama.** Ronda con callback

```java
.call().entity(converter)
```

**Notas.** Un advisor coordina herramientas y otro acota la ronda.

**Transición.** Continúa con «Structured Output».

**Evidencia.** AgentEngineeringTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 8. Structured Output

**En pantalla.** ModelFinding → EvidenceValidator

**Diagrama.** Contrato y comparación

```java
var observation = tool.observationFor(citations.getFirst()).orElseThrow(InvalidFinding::new);
```

**Notas.** JSON correcto aún puede ser inválido.

**Transición.** Continúa con «Política».

**Evidencia.** AgentEngineeringTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 9. Política

**En pantalla.** BLOCK obligatorio · REVIEW por señales o ausencia

**Diagrama.** Árbol de decisión

```java
return new RiskDecision(transaction.transactionId(), decision, POLICY_VERSION, decidedAt, signals, unavailable);
```

**Notas.** La política conserva su versión.

**Transición.** Continúa con «Microsoft Foundry».

**Evidencia.** RiskAssessmentTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 10. Microsoft Foundry

**En pantalla.** Recurso · despliegue · Entra ID

**Diagrama.** Java frente a hosting del modelo

**Notas.** Solicitud probada localmente. Inferencia real pendiente.

**Transición.** Continúa con «Seguridad».

**Evidencia.** FoundryConnectivityTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://learn.microsoft.com/en-us/azure/foundry/).

## 11. Seguridad

**En pantalla.** Herramienta de solo lectura · ámbito · procedencia

**Diagrama.** Frontera Java/modelo

```java
budget.requireTime();
```

**Notas.** La autorización no depende del prompt.

**Transición.** Continúa con «Observabilidad».

**Evidencia.** AgentEngineeringTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 12. Observabilidad

**En pantalla.** Observaciones · spans hermanos · OTLP

**Diagrama.** Árbol de spans

```java
Span.current().addLink(assessment);
```

**Notas.** Receptor local probado. Grafana pendiente.

**Transición.** Continúa con «Demo».

**Evidencia.** OtlpExportTests. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://opentelemetry.io/docs/specs/otlp/).

## 13. Demo

**En pantalla.** REVIEW → dos agentes → evidencia → timeout

**Diagrama.** Dashboard y waterfall

**Notas.** Usa Plan B por defecto.

**Transición.** Continúa con «Evaluación».

**Evidencia.** Capturas reales. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 14. Evaluación

**En pantalla.** 64 pruebas · latencias locales medidas

**Diagrama.** Tabla p50/p95

**Notas.** El benchmark describe fixtures y este equipo.

**Transición.** Continúa con «Conclusiones».

**Evidencia.** BENCHMARK-REPORT. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 15. Conclusiones

**En pantalla.** Decisión reproducible · investigación explicable

**Diagrama.** Regreso al flujo

**Notas.** Expón qué falta para producción.

**Transición.** Continúa con «Referencias».

**Evidencia.** Original RiskDecision en API. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).

## 16. Referencias

**En pantalla.** Spring AI · Azure · Resilience4j · OpenTelemetry

**Diagrama.** Lista breve

**Notas.** Los enlaces oficiales acompañan al código.

**Transición.** Abre preguntas.

**Evidencia.** Guía de estudio. Consulta el [recorrido](CODE-WALKTHROUGH.md).

**Referencia oficial.** [Documentación](https://github.com/spring-projects/spring-ai/tree/v2.0.1).


