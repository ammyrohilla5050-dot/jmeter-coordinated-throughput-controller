# Publishing Checklist

This package is ready to publish as an external JMeter plugin named
`Coordinated Throughput Controller`.

The fastest path follows the JMeter Plugins community guidance:

1. Create the plugin.
2. Publish the plugin JAR from our GitHub release. Maven Central is useful, but
   not mandatory for the first release.
3. Add an entry to `undera/jmeter-plugins/site/dat/repo/various.json` and open
   a PR.
4. Optionally add a wiki page under `undera/jmeter-plugins/site/dat/wiki/` so it
   appears on the JMeter Plugins website.

## 1. Create the public plugin repository

Suggested repository name:

```text
jmeter-coordinated-throughput-controller
```

Commit these source files:

```text
.gitignore
LICENSE
NOTICE
README.md
PUBLISHING.md
build.gradle.kts
settings.gradle.kts
docs/
repo/
src/
```

Do not commit:

```text
.gradle/
build/
dist/
```

## 2. Build the release

```bash
../../gradlew -p external/coordinated-throughput-controller clean build releaseZip
```

Release artifacts:

```text
dist/jmeter-coordinated-throughput-controller-0.1.0.jar
dist/jmeter-coordinated-throughput-controller-0.1.0.zip
```

## 3. Create a GitHub release

Create tag:

```text
v0.1.0
```

Upload:

```text
jmeter-coordinated-throughput-controller-0.1.0.jar
jmeter-coordinated-throughput-controller-0.1.0.zip
```

## 4. Prepare the `undera/jmeter-plugins` PR

Fork and clone:

```bash
git clone https://github.com/<your-github-user>/jmeter-plugins.git
```

Add our entry from:

```text
repo/various-entry.template.json
```

into:

```text
site/dat/repo/various.json
```

Before opening the PR, replace every placeholder:

```text
ammyrohilla5050-dot
Amit Kumar
```

with real values.

The release `downloadUrl` should point to the GitHub release JAR:

```text
https://github.com/<owner>/jmeter-coordinated-throughput-controller/releases/download/v0.1.0/jmeter-coordinated-throughput-controller-0.1.0.jar
```

Since this plugin has no third-party runtime libraries beyond JMeter itself,
`libs` remains `{}` and `depends` lists `jmeter-core` and `jmeter-components`.

## 5. Optional website page

If we want a page on jmeter-plugins.org, copy:

```text
repo/wiki/CoordinatedThroughputController.md
```

to the `jmeter-plugins` fork at:

```text
site/dat/wiki/CoordinatedThroughputController.md
```

Then include that file in the same PR.

## 6. Local install test

Copy:

```text
dist/jmeter-coordinated-throughput-controller-0.1.0.jar
```

to:

```text
<JMETER_HOME>/lib/ext/
```

Restart JMeter and check:

```text
Logic Controller -> Coordinated Throughput Controller
```
