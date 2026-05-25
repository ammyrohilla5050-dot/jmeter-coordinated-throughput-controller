# Coordinated Throughput Controller

Coordinated Throughput Controller is an external Apache JMeter logic controller
that coordinates sibling controllers under the same parent. During each
parent iteration, sibling Coordinated Throughput Controllers make one shared
selection decision, so only one coordinated controller runs. If the configured
weights add up to less than 100, some parent iterations intentionally run none
of the coordinated controllers.


## Why This Exists

JMeter's standard Throughput Controller evaluates each sibling independently.
If two sibling controllers are both configured at 50%, both can run in the same
parent iteration. This plugin is for test plans that need weighted
controller selection where sibling controllers are mutually exclusive and only one controller run at any given iteration.


## Weight Behavior

| Use case | Example setup | What the controller does | Expected result |
| --- | --- | --- | --- |
| Total weight equals `100` | `Controller A=40`, `Controller B=60` | The controller treats the values as percentage shares. The full selection pool is consumed by real controllers, so every parent iteration selects exactly one coordinated controller. There is no empty or skipped remainder. | Over 100 parent iterations, Controller A runs about 40 times and Controller B runs about 60 times. In each parent iteration, either Controller A or Controller B runs, never both. |
| Total weight is less than `100` | `Controller A=20`, `Controller B=30`, total is `50` | The controller treats the missing portion as an intentional "no controller selected" share. Internally, the selection pool is `Controller A=20`, `Controller B=30`, and `no controller=50`. When `no controller` is selected, all sibling coordinated controllers are skipped for that parent iteration. | Over 100 parent iterations, Controller A runs about 20 times, Controller B runs about 30 times, and no coordinated controller runs about 50 times. This is useful when only part of the total traffic should enter these coordinated flows. |
| Total weight is greater than `100` | `Controller A=60`, `Controller B=90`, total is `150` | There is no remaining "no controller selected" share because the total already exceeds 100. The controller treats the values as relative weights. So `Controller A=60`, `Controller B=90` behaves like a 60:90 ratio. | Controller A receives about `60 / 150 = 40%` of selections and Controller B receives about `90 / 150 = 60%`. Over 100 parent iterations, Controller A runs about 40 times and Controller B runs about 60 times. |

In all cases, the selected controller runs its child samplers/controllers normally.
Non-selected sibling controllers return no sampler for that parent iteration.

## Requirements

| Requirement | Value |
| --- | --- |
| Requires Java Version | Java 8 or later |
| JMeter Version | Apache JMeter 5.6.3 or later |
| External Dependencies | Fully standalone plugin JAR. No additional external dependency JARs are required beyond Apache JMeter. |

## Screenshot

![Coordinated Throughput Controller GUI](docs/Coordinated_Throughput_Controller.png)

## Install
1. Search 'Coordinated Throughput Controller' in ypur plugin manager available in jmeter and click 'Apply changes and Restart JMeter' ( if plugin manager is not available under Options, first download plugin manager jar and place it in <JMETER_HOME>/lib/ext/ folder

OR

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
