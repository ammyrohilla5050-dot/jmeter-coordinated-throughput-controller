# Coordinated Throughput Controller

Coordinated Throughput Controller is an external Apache JMeter logic controller
that coordinates sibling controller branches under the same parent. During each
parent iteration, sibling Coordinated Throughput Controllers make one shared
selection decision, so only one coordinated branch (controller) runs. If the configured
weights add up to less than 100, some parent iterations intentionally run none
of the coordinated branches.

## Why This Exists

JMeter's standard Throughput Controller evaluates each sibling independently.
If two sibling controllers are both configured at 50%, both can run in the same
parent iteration. This plugin is for test plans that need weighted
selection where sibling alternatives are mutually exclusive.

## Install

1. Download `jmeter-coordinated-throughput-controller-0.1.0.jar`.
2. Copy it to:

   ```text
   <JMETER_HOME>/lib/ext/
   ```

3. Restart JMeter.
4. Add it from the Logic Controller menu as `Coordinated Throughput Controller`.

## Build

From this directory:

```bash
../../gradlew build releaseZip
```

When built inside a JMeter source checkout, the build uses the local JMeter
JARs from `src/*/build/libs`. Outside a JMeter source checkout, it resolves
Apache JMeter artifacts from Maven Central. Override the JMeter version with:

```bash
../../gradlew build -PjmeterVersion=5.6.3
```

## Release Artifacts

The build writes release files to `dist/`:

```text
dist/jmeter-coordinated-throughput-controller-0.1.0.jar
dist/jmeter-coordinated-throughput-controller-0.1.0.zip
```

The `repo/` directory contains JMeter Plugins Manager PR helpers:

```text
repo/various-entry.template.json
repo/wiki/CoordinatedThroughputController.md
```

Use `various-entry.template.json` as the object to add to
`undera/jmeter-plugins/site/dat/repo/various.json` after replacing the GitHub
owner/repository placeholders with the real release URL.

## Plugin Classes

Runtime class:

```text
org.apache.jmeter.control.CoordinatedThroughputController
```

GUI class:

```text
org.apache.jmeter.control.gui.CoordinatedThroughputControllerGui
```

## License

Apache License, Version 2.0.
