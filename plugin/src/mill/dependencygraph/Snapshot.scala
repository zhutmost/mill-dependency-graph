package mill.dependencygraph

import coursier.core.{Dependency, Resolution}
import coursier.graph.DependencyTree

import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** The Maven part of a GitHub dependency-submission manifest. */
private[dependencygraph] final class Snapshot {
  private final class Entry {
    var direct = false
    var runtime = false
    val children = mutable.Set.empty[String]
  }

  private val entries = mutable.Map.empty[String, Entry]

  def addModule(resolution: Resolution, root: Dependency, development: Boolean): Unit =
    addTrees(resolution, DependencyTree.one(resolution, root).children, development)

  def addRoots(resolution: Resolution, roots: Seq[Dependency], development: Boolean): Unit =
    addTrees(resolution, roots.map(DependencyTree.one(resolution, _)), development)

  private def addTrees(
      resolution: Resolution,
      roots: Seq[DependencyTree],
      development: Boolean
  ): Unit = {
    // The same Maven module can be reached with different exclusions. Merge its outgoing edges.
    val visited = mutable.Set.empty[Dependency]

    def visit(tree: DependencyTree, direct: Boolean): Set[String] = {
      if (tree.excluded || tree.endorsed) Set.empty
      else if (tree.dependency.module.organization.value == "mill-internal")
        tree.children.iterator.flatMap(child => visit(child, direct = false)).toSet
      else {
        val dep = tree.dependency
        val version = resolution.retainedVersions.getOrElse(
          dep.module,
          throw new IllegalStateException(s"No resolved version for ${dep.module.repr}")
        )
        val url = Snapshot.mavenPurl(dep.module.organization.value, dep.module.name.value,
          version.asString)
        val entry = entries.getOrElseUpdate(url, new Entry)
        entry.direct ||= direct
        entry.runtime ||= !development
        if (visited.add(dep))
          entry.children ++= tree.children.iterator.flatMap(child => visit(child, direct = false))
        Set(url)
      }
    }

    roots.foreach(root => visit(root, direct = true))
  }

  def toJson: ujson.Obj = ujson.Obj.from(entries.toSeq.sortBy(_._1).map { case (url, entry) =>
    url -> ujson.Obj(
      "package_url" -> url,
      "relationship" -> (if (entry.direct) "direct" else "indirect"),
      "scope" -> (if (entry.runtime) "runtime" else "development"),
      "dependencies" -> ujson.Arr.from(entry.children.toSeq.sorted.map(ujson.Str(_)))
    )
  })
}

private[dependencygraph] object Snapshot {
  def mavenPurl(group: String, artifact: String, version: String): String =
    s"pkg:maven/${encode(group)}/${encode(artifact)}@${encode(version)}"

  private def encode(value: String): String =
    value.getBytes(StandardCharsets.UTF_8).iterator.map { byte =>
      val unsigned = byte & 0xff
      if ((unsigned >= 'a' && unsigned <= 'z') ||
          (unsigned >= 'A' && unsigned <= 'Z') ||
          (unsigned >= '0' && unsigned <= '9') ||
          "-._~".contains(unsigned.toChar)) unsigned.toChar.toString
      else f"%%$unsigned%02X"
    }.mkString
}
