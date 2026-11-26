# Preguntas y respuestas

### 1. ¿Por qué Spring AI y no LangChain4j?

La charla demuestra la integración con Spring Boot, ChatClient y advisors. No hubo una comparación de rendimiento.

### 2. ¿Por qué no Foundry Agent Service?

El proceso Java conserva orquestación, estado, presupuestos y procedencia. Foundry se configura para inferencia.

### 3. ¿Por qué no MCP?

Dos callbacks de alcance fijo bastan; no hay descubrimiento ni invocación de herramientas externas.

### 4. ¿Por qué no RAG?

Se consulta evidencia estructurada de la fixture correspondiente al caso.

### 5. ¿Por qué no base vectorial?

Los registros se resuelven por contexto e identidad exacta.

### 6. ¿Por qué compartir modelo?

El rol, instrucciones y herramientas especializan a los agentes. Compartir despliegue también comparte fallos y cuota.

### 7. ¿Por qué orquestación determinista?

Java controla admisión, ejecución paralela, estados y publicación de resultados.

### 8. ¿Por qué un monolito modular?

Una JVM facilita la demo; los módulos ya separan política, investigación e infraestructura.

### 9. ¿Quién ejecuta Tool Calling?

Spring AI procesa la solicitud del modelo y despacha el ToolCallback en Java.

### 10. ¿Qué ve el modelo?

Instrucciones, esquema de herramienta y sus resultados sintéticos; la capacidad de autorización queda en ToolContext.

### 11. ¿Puede el modelo acceder a otro caso?

El callback comprueba identidad de ámbito y exige argumentos vacíos.

### 12. ¿Cómo se contiene una alucinación?

Se validan referencias y observaciones con reglas de aplicación; siguen existiendo límites de verificación semántica.

### 13. ¿Qué garantiza Structured Output?

Conversión a ModelFinding; EvidenceValidator comprueba validez después.

### 14. ¿Cómo se acredita una cita?

La referencia completa debe haber sido emitida por el callback de esa ejecución.

### 15. ¿Quién emite identificadores?

Java asigna IDs y metadatos de ejecución y de registros.

### 16. ¿Puede un agente cambiar REVIEW?

El informe conserva el RiskDecision original y el agente carece de acceso a su publicación.

### 17. ¿Por qué separar el Hot Path?

La decisión debe ser reproducible sin latencia ni disponibilidad de un LLM.

### 18. ¿Qué ocurre si faltan datos?

La disponibilidad queda explícita; ausencia puede provocar REVIEW y no cancela un BLOCK obligatorio.

### 19. ¿Qué significa APPROVE?

Es una recomendación sintética sin efecto sobre operaciones bancarias.

### 20. ¿Un timeout cancela Azure?

La app solicita interrupción; no se prueba cancelación remota.

### 21. ¿Para qué sirve TimeLimiter?

Acota la espera del coordinador sobre una vista del resultado.

### 22. ¿Y el timeout HTTP?

Limita el intento del transporte y se reduce al presupuesto restante.

### 23. ¿Qué aísla Bulkhead?

Capacidad por agente y capacidad compartida de llamadas remotas.

### 24. ¿Qué protege el circuito?

El despliegue compartido; cuenta fallos clasificados como transitorios o de transporte.

### 25. ¿Cuándo se libera un permiso?

Cuando termina la operación protegida, incluso si el informe cerró antes.

### 26. ¿Se reintenta el modelo?

El SDK se configura con cero reintentos; una ronda adicional de herramientas es parte del workflow.

### 27. ¿Qué pasa si fallan ambos agentes?

El informe queda FAILED sin éxitos verificados y conserva REVIEW.

### 28. ¿Qué sucede si Foundry no responde?

Se clasifican fallos, se limita la espera y se conserva cualquier evidencia válida. No hay cambio automático a simulación.

### 29. ¿Cómo sobreviven las trazas al executor?

El coordinador abre en cada worker y callback el scope de la observación capturada.

### 30. ¿Trace ID e investigation ID son equivalentes?

Uno identifica el flujo de telemetría; el otro, el caso de negocio.

### 31. ¿Para qué sirve un span link?

Relaciona la evaluación con una investigación posterior sin cambiar su padre HTTP.

### 32. ¿Qué exporta OTLP?

Spans filtrados; prompts, respuestas y argumentos quedan fuera de la telemetría de la demo.

### 33. ¿Se verificó Grafana?

Se probó OTLP/HTTP contra loopback y se preservaron spans; la interfaz de Grafana queda pendiente.

### 34. ¿Qué autenticación usa Azure?

Un bearer de Microsoft Entra ID suministrado por entorno para el recurso autorizado.

### 35. ¿Qué es real en demo-local?

HTTP, política, concurrencia, validación, presupuesto y telemetría; los hechos y la generación son simulados.

### 36. ¿Se miden tokens offline?

El dashboard marca no disponible cuando no hay datos de proveedor.

### 37. ¿Hay garantías regulatorias?

El prototipo usa datos sintéticos, memoria volátil y controles de demo. Producción requiere identidad, auditoría durable, privacidad y validación.

### 38. ¿Está medida la detección de fraude?

Se midieron latencias y resultados mecánicos de fixtures, sin evaluar precisión bancaria.

### 39. ¿Se puede usar Azure Monitor?

Es una opción de arquitectura que requiere validar instrumentación y evitar spans duplicados.

### 40. ¿Se verificó Azure en vivo?

El contrato se ejercitó localmente; sigue pendiente una invocación autorizada a un despliegue real.
