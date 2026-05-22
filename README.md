# Coordinated Throughput Controller

Coordinated Throughput Controller is an external Apache JMeter logic controller
that coordinates sibling controller branches under the same parent. During each
parent iteration, sibling Coordinated Throughput Controllers make one shared
selection decision, so only one coordinated branch (controller) runs. If the configured
weights add up to less than 100, some parent iterations intentionally run none
of the coordinated branches.

## Weight Behavior

| Use case | Example setup | What the controller does | Expected result |
| --- | --- | --- | --- |
| Total weight equals `100` | `A=40`, `B=60` | The controller treats the values as percentage shares. The full selection pool is consumed by real branches, so every parent iteration selects exactly one coordinated branch. There is no empty or skipped remainder. | Over 100 parent iterations, branch A runs about 40 times and branch B runs about 60 times. In each parent iteration, either A or B runs, never both. |
| Total weight is less than `100` | `A=20`, `B=30`, total is `50` | The controller treats the missing portion as an intentional "no branch selected" share. Internally, the selection pool is `A=20`, `B=30`, and `no branch=50`. When `no branch` is selected, all sibling coordinated branches are skipped for that parent iteration. | Over 100 parent iterations, branch A runs about 20 times, branch B runs about 30 times, and no coordinated branch runs about 50 times. This is useful when only part of the total traffic should enter these coordinated flows. |
| Total weight is greater than `100` | `A=60`, `B=90`, total is `150` | There is no remaining "no branch selected" share because the total already exceeds 100. The controller treats the values as relative weights. So `A=60`, `B=90` behaves like a 60:90 ratio. | Branch A receives about `60 / 150 = 40%` of selections and branch B receives about `90 / 150 = 60%`. Over 100 parent iterations, A runs about 40 times and B runs about 60 times. |

In all cases, the selected branch runs its child samplers/controllers normally.
Non-selected sibling branches return no sampler for that parent iteration.

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
