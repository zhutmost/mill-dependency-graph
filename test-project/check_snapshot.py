"""Check the properties that need a real Mill/Coursier resolution to verify."""

import json
import sys

with open(sys.argv[1], encoding="utf-8") as file:
    manifests = json.load(file)["manifests"]

core = manifests["core"]["resolved"]
test = manifests["core.test"]["resolved"]


def find(dependencies, artifact):
    matches = [v for purl, v in dependencies.items() if f"/{artifact}@" in purl]
    assert len(matches) == 1, (artifact, list(dependencies))
    return matches[0]


assert find(core, "pprint_3")["relationship"] == "direct"
assert find(core, "pprint_3")["scope"] == "runtime"
assert find(core, "logback-classic")["scope"] == "runtime"
assert find(core, "annotations")["scope"] == "development"
assert find(core, "scala3-compiler_3")["scope"] == "development"
assert find(test, "utest_3")["scope"] == "development"
assert find(test, "utest_3")["relationship"] == "direct"

for manifest in manifests.values():
    resolved = manifest["resolved"]
    for package in resolved.values():
        for child in package["dependencies"]:
            assert child in resolved, (manifest["name"], child)

print(f"Checked {len(manifests)} module manifests")
