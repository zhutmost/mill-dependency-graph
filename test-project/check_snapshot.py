"""Check the properties that need a real Mill/Coursier resolution to verify."""

import json
import sys

with open(sys.argv[1], encoding="utf-8") as file:
    manifests = json.load(file)["manifests"]

core = manifests["core"]["resolved"]
test = manifests["core.test"]["resolved"]
java = manifests["javaLib"]["resolved"]
consumer = manifests["consumer"]["resolved"]
excluded = manifests["excluded"]["resolved"]
bom = manifests["withBom"]["resolved"]

assert set(manifests) == {"core", "core.test", "javaLib", "consumer", "excluded", "withBom"}


def find(dependencies, artifact):
    matches = [v for purl, v in dependencies.items() if f"/{artifact}@" in purl]
    assert len(matches) == 1, (artifact, list(dependencies))
    return matches[0]


assert find(core, "pprint_3")["relationship"] == "direct"
assert find(core, "pprint_3")["scope"] == "runtime"
assert find(core, "logback-classic")["scope"] == "runtime"
assert find(core, "annotations")["scope"] == "development"
assert find(core, "scala3-compiler_3")["scope"] == "development"
assert find(core, "scala3-compiler_3")["relationship"] == "direct"
assert find(core, "scala3-interfaces")["scope"] == "development"
assert find(core, "scala3-interfaces")["relationship"] == "indirect"
assert find(test, "utest_3")["scope"] == "development"
assert find(test, "utest_3")["relationship"] == "direct"

assert find(java, "commons-text")["relationship"] == "direct"
assert find(java, "commons-lang3")["relationship"] == "indirect"
assert find(java, "commons-lang3")["scope"] == "runtime"
assert find(consumer, "commons-text")["relationship"] == "indirect"
assert find(consumer, "commons-lang3")["scope"] == "runtime"

assert find(excluded, "commons-text")["relationship"] == "direct"
assert not any("/commons-lang3@" in purl for purl in excluded)

assert find(bom, "jackson-databind")["package_url"].endswith("@2.17.2")
assert not any("/jackson-bom@" in purl for purl in bom)

for manifest in manifests.values():
    resolved = manifest["resolved"]
    for package in resolved.values():
        for child in package["dependencies"]:
            assert child in resolved, (manifest["name"], child)

print(f"Checked {len(manifests)} module manifests")
