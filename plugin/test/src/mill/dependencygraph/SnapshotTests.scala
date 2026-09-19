package mill.dependencygraph

import utest.*

object SnapshotTests extends TestSuite {
  val tests: Tests = Tests {
    test("Maven PURLs use resolved artifact names and percent-encode versions") {
      assert(Snapshot.mavenPurl("org.chipsalliance", "chisel_3", "7.4.0") ==
        "pkg:maven/org.chipsalliance/chisel_3@7.4.0")
      assert(Snapshot.mavenPurl("example.org", "api", "1.0+build") ==
        "pkg:maven/example.org/api@1.0%2Bbuild")
    }
  }
}
