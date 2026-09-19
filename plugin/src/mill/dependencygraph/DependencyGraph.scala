package mill.dependencygraph

import coursier.core.{Configuration, Resolution, VariantSelector}
import coursier.core.VariantSelector.VariantMatcher
import mill.api.{Discover, Evaluator, ExternalModule, Task}
import mill.javalib.{BoundDep, JavaModule, Lib, TestModule}
import mill.scalalib.ScalaModule
import mill.util.TokenReaders.given

/** Exports the resolved dependencies of all JVM modules in the current Mill build. */
object DependencyGraph extends ExternalModule {

  def generate(ev: Evaluator) = {
    val modules = ev.rootModule.moduleInternal.modules.collect { case module: JavaModule => module }
    val manifests = Task.traverse(modules)(manifest)

    Task.Command {
      ujson.Obj("manifests" -> ujson.Obj.from(manifests()))
    }
  }

  private def manifest(module: JavaModule): Task[(String, ujson.Value)] = {
    val runtime = resolve(module, Configuration.runtime)
    val provided = resolve(module, Configuration.provided)
    val plugins: Task[Option[Resolution]] = module match {
      case scala: ScalaModule => pluginResolution(scala)
      case _ => Task.Anon { Option.empty[Resolution] }
    }

    Task.Anon {
      val root = module.coursierDependencyTask()
      val name = module.moduleSegments.render
      val graph = new Snapshot

      // Match the compile and runtime configurations Mill uses for its own classpaths.
      graph.addModule(runtime(), root.withConfiguration(Configuration.runtime),
        development = module.isInstanceOf[TestModule])
      graph.addModule(provided(), root.withConfiguration(Configuration.provided),
        development = true)

      plugins().foreach { resolved =>
        graph.addRoots(resolved, resolved.rootDependencies, development = true)
      }

      name -> ujson.Obj("name" -> s"mill:$name", "resolved" -> graph.toJson)
    }
  }

  private def pluginResolution(module: ScalaModule): Task[Option[Resolution]] = {
    val pluginDeps = module.scalacPluginMvnDeps
    val resolver = module.defaultResolver
    Task.Anon {
      val deps = pluginDeps()
      if (deps.isEmpty) None else Some(resolver().resolution(deps))
    }
  }

  private def resolve(module: JavaModule, configuration: Configuration): Task[Resolution] = Task.Anon {
    val params = module.resolutionParams()
    val runtime = configuration == Configuration.runtime
    val usage = if (runtime) VariantMatcher.Runtime else VariantMatcher.Api
    val configured = params
      .withDefaultConfiguration(if (runtime) Configuration.runtime else Configuration.compile)
      .withDefaultVariantAttributes(
        VariantSelector.AttributesBased(
          params.defaultVariantAttributes.map(_.matchers).getOrElse(Map.empty) ++
            Map("org.gradle.usage" -> usage)
        )
      )

    Lib.resolveDependenciesMetadataSafe(
      repositories = module.allRepositories(),
      deps = Seq(BoundDep(
        module.coursierDependencyTask().withConfiguration(configuration),
        force = false
      )),
      mapDependencies = Some(module.mapDependencies()),
      customizer = module.resolutionCustomizer(),
      coursierCacheCustomizer = module.coursierCacheCustomizer(),
      resolutionParams = configured,
      checkGradleModules = module.checkGradleModules(),
      config = module.coursierConfigModule().coursierConfig()
    ).get
  }

  override lazy val millDiscover = Discover[this.type]
}
