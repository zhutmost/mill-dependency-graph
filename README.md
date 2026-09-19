# 📦 Mill Dependency Graph

Submit the resolved JVM dependencies of a [Mill](https://mill-build.org) 1.1+ build
to GitHub's [dependency graph](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/exploring-the-dependencies-of-a-repository).
Supports Mill **1.1+** and is currently tested with **1.1.9**. The Mill plugin
and GitHub Action live in this repository.

## 🚀 Use the Action

Enable the dependency graph in your repository, then add this workflow. Use a
commit SHA or a release tag in place of `main` for a stable Action reference.

```yaml
name: Dependency graph
on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  submit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: zhutmost/mill-dependency-graph@main
```

The Action uses `./mill`, `./millw`, or `mill` on `PATH` in that order. It builds the
bundled plugin with the project's Mill executable, publishes it to the runner's
local Ivy repository, then loads it into the project with `--import`. No changes
to the project's `build.mill` are needed. For a build in a subdirectory, set
`working-directory` on the Action; the executable must be in that directory or
on `PATH`. Reusing a Coursier cache makes repeated runs faster. The GitHub token
is used only in the upload step, not passed to Mill.

The Action submits a snapshot for the triggering commit. Run it on pushes to the
default branch; dependency review on pull requests can then compare snapshots.
GitHub requires `contents: write` for dependency submission.

## 🔎 What is reported

Each `JavaModule` or `ScalaModule`, including test modules, becomes a logical
manifest named `mill:<module path>`. The plugin reads Mill's Coursier resolution
for the production runtime, compile-only and Scala compiler plugin classpaths;
it reports resolved Maven coordinates, dependency edges, and direct/indirect
relationships. Production runtime dependencies are `runtime`; compile-only,
compiler plugin and test-module dependencies are `development`. Compiler plugin
dependencies include the transitive libraries on Mill's `scalacPluginClasspath`.
If a package is used in both, `runtime` wins. BOM entries and Mill's internal
module identities are not submitted as external packages. Logical manifests do
not claim a physical `build.mill` source file, because several modules can share
one file.

Local jars from `unmanagedClasspath`, native dependencies and dependencies
used solely by `build.mill` itself are outside this JVM dependency graph.
This integration feeds GitHub dependency visibility and vulnerability alerts;
it does not create version-update pull requests.

## 🛠️ Develop

Install Mill 1.1.9 and a JDK 17 or newer, then run:

```sh
mill plugin.test
mill plugin.publishLocal
cd test-project
COURSIER_REPOSITORIES='ivy2Local|central' mill --import \
  'mvn:io.github.zhutmost::mill-dependency-graph::0.1.0' \
  show mill.dependencygraph.DependencyGraph/generate > snapshot.json
python3 check_snapshot.py snapshot.json
```

The command emits `{"manifests": ...}` as JSON; the Action adds the commit,
workflow and detector metadata before posting it to GitHub. The plugin can be
published to Maven Central later to skip building it on every Action run.

Licensed under [MIT](LICENSE).
