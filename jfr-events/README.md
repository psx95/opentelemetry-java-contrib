# OpenTelemetry Java Flight Recorder (JFR) Events

Create JFR events that can be recorded and viewed in Java Mission Control (JMC).

* Creates Open Telemetry Tracing/Span events for spans
  * The thread and stacktrace will be of the thead ending the span which might be different from the thread creating the span.
  * Has the fields
    * Operation Name
    * Trace ID
    * Parent Span ID
    * Span ID
* Creates Open Telemetry Tracing/Scope events for scopes
  * Thread will match the thread the scope was active in and the stacktrace will be when scope was closed
  * Multiple scopes might be collected for a single span
  * Has the fields
    * Trace ID
    * Span ID
* Supports the Open Source version of JFR in Java 11.
  * Might support back port to OpenJDK 8, but not tested and classes are built with JDK 11 bytecode.

## Component owners

- [Staffan Friberg](https://github.com/sfriberg)

Learn more about component owners in [component_owners.yml](../.github/component_owners.yml).
