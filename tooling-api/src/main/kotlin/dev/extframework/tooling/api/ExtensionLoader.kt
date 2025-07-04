package dev.extframework.tooling.api

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.monad.Tree
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings

public interface ExtensionLoader : ExtensionEnvironment.Attribute {
    public val extensionResolver: ExtensionResolver
    public val graph: ArchiveGraph
    public val environment: ExtensionEnvironment

    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = ExtensionLoader

    public companion object : ExtensionEnvironment.Attribute.Key<ExtensionLoader>

    // Environment INDEPENDENT operations
    public suspend fun cache(
        requests: Map<ExtensionDescriptor, ExtensionRepositorySettings>
    ): List<Tree<ExtensionData>>

    public suspend fun load(
        extensions: List<ExtensionDescriptor>
    ): List<ExtensionNode>

    // Environment DEPENDENT operations
    public suspend fun tweak(
        extensions: List<ExtensionNode>,
    )

//    // TODO serious thought
    public suspend fun unload(
        descriptor: ExtensionDescriptor,
    )

    public data class ExtensionData(
        val descriptor: ExtensionDescriptor,
        val model: ExtensionRuntimeModel
    )
}

/*
?/?/25
What I want:

Should tweakers be environment dependent? Yes 100% they are in charge of modifying the environment,
and since can have dependencies on other extensions should be able to consume those changes.

Debate: Should the tweaker tree be environment dependent?

No outside information actually needs to be passed into the tweaker mechanism - in the specific
case of application targets these are only required during initialization

Should the tweaker tree be environment dependent? The alternative is that no data can be passed into
the extension loading mechanism, so yes. The point of a tweaker is that it is stateless, at runtime
n number of environments may be present that are all perfectly valid and require tweaking. But these operations
MUST be environment independent.

Proposed mechanism:
 - The client: The client is the piece of software responsible for holding the `main` method of the program
 and beginning the extension initialization process. The `tweaker` partition is an intrinsically defined
 partition because its use in defining other partitions and dependency types. Clients may define an arbitrary
 number of partitions that they also load, eg. gradle with its 'gradle' partition.
 - Environment tweaking: The process in which a stateless tweaker modifies a given environment.
 - Runtime: What happens after initialization is completely up to the client.
    - Gradle plugin: Runtime initialization might include partition loading
    - Minecraft running initialization and starting Minecraft
    - etc.
 - Rerunning of tweakers: While operations on a given environment must not change based on any changes that occur
 to the environment before the tweaker tree was built, it is still valid for tweakers to be reran it is still valid
 to rerun tweakers as individual attributes may still contain state.
 - While the tweaker partition is stateless, not all partitions must be so: this cannot be enforced.
 - As it is defined here, environment tweaking is a compositional process. Both the client and tweakers have control
 over it and as extension loading progresses the composition of the environment grows. The only place where the environment
 should be mutated is in a tweaker. Currently, there is no plans to enforce compositional compilation of the environment:
 theoretically an extension lower in the tree (that is being a dependency of other extensions) can access the changes that
 other extensions make to the environment. As well as the environment is not locked after the tweaking process (TBD).
 - Efficient extension loading: While all partitions may be stateless

4/2/25
What I am deciding:

There are 2 extension loading operations that truly happen independent of an extension environment: Extension tree +
tweaker caching and loading. There are two options im juggling right now:
 1) Having all operations be environment specific isn't representative of what is truly happening as environments
    can really only be modified by tweakers. Initial states set by the client don't need to be represented by the
    environment object. For this reason, there could be a super type extension loader that handles these specific
    actions and then specialized types that handle it for specific environments.
 2) While it isn't valid to modify the top level parents extension environment, it is simpler to implement the
    extension loading mechanism as a tree (and thus environments also as a tree) that first delegate to parents
    and then perform the requested action. This also allows for the capability to create multiple levels of
    environment specialization at the cost of a dangling illegal operation at the top of the tree.
    Use cases for more than 1 level of tree depth:
     - Extensions themselves may wish to perform loading operations independently of other extensions
     - This allows for greater convergence of environment variability: ie specifically for Minecraft, one environment
       variable that requires specialization is minecraft version, however another might be mapping namespace. Yes, we
       could simply have n^n different unrelated environments that are unrelated but if performing a common environment
       tweak that is independent of the given variables n, the tweak would have to happen n^n times as well which is
       simply poor design.
     -

4/16/25

Taking a step back I dont like the complexity of what I can up with before. I dont think environments or the extension
loader need to have a parent / child mechanic.

The main issue:
 - Loading partitions independent of the environment is easy, however when we start mixin environments and partitions
   that may get loaded multiple times (such as loading a main partition for each different environment) we run
   into limitations in the boot system: when you look deeper they are less limitations and more just artifacts
   of what we are really dealing with: if partitions and environments are linked then it goes that when describing
   the partition you must also describe the environment thus is makes sense to include the environment in the
   PartitionDescriptor. This is limiting when there are partitions without linked descriptors, and when we think
   about what actual type should be passed into the descriptor. Given that a descriptor must be serializable the
   type must be a string, however then how do we convert a descriptor back into a valid environment? A not-elegant
   option is to have yet another global registry (no). However, without some sort of lookup table this no longer becomes
   possible. Another option is to link partition resolver and this new type of partition descriptor. Partition resolvers
   may have environments and also have a unique id associated with their name and the environment.

   **The problem: what do we do about environment-independent partitions?
    Option 1) Nullability: Environments + id can be nullable -> the way this would work is self-explanatory
    Option 2) Subtypes: PartitionDescriptor + EnvironmentalPartitionDescriptor (better name pls)
                        PartitionResolver + Env...PartitionResolver
                        **problem**: A non-environmental partition resolver may satisfy the request for an environmental
                                     partition because it does not know about the subtype unless we hard code this (valid
                                     but a tad ugly)
    Option 3) Full subtypes: PartitionDescriptor -> Environment aware + unaware : etc etc
    Option 4) All types are loaded in an environment aware container: there is the default environment: 'default'
              that all tweakers require.
              Issue 1: **What environment do parents use?**
              Solutions:
                Option 1) Environment dependent partitions use the same environment, environment independent ones
                          must use the default.
                Option 2) ExtensionPartitionLoaders provide define it, if they define a dependency on a tweaker
                          they must also define the environment it is loaded in.

              Issue 2: Typing of environments
                 Requirements:
                    - Should reasonably a lightweight type as generally descriptors are lightweight
                    - Must be fully serializable / deserializable
                 Options:
                    - Full Extension environment: Fails condition 1, is serializable by name, but not deserializable
                      without a registry
                    - Environment name -> string: Makes transforming name to full environment more difficult +
                      requirement of a registry.



*/