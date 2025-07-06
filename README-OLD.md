# Hello!
 Welcome to `1.0 SNAPSHOT` version of Extension Framework

# Table of contents
 - [An introduction](#an-introduction)
 - [Getting Started](#getting-started)
   - [Gradle](#gradle)
 - [First extension](#your-first-extension)
 - [Mixins](#mixins)
   - [Defining mixins](#defining-a-mixin)
   - [Source Injections](#source-injections)
   - [Method Injections](#source-injections)
   - [Field Injections](#field-injections)
 -[Partitioning](#Partitioning)
   - [Defining Partitions](#defining-partitions)
   - [Partition overloading](#partition-overloading)
# An Introduction

YakClient is an all-purpose solution to the question of how to modify existing Java applications, in this case,
targeting the Minecraft Java Edition platform. Unlike other existing solutions, YakClient is extremely lightweight
and takes a new approach to how different minecraft version are targeted and dealt with.

Our solution to versioning takes place at the `SourceSet` level. Instead of combining all version logic into every 
class or mixin that might perform version dependent actions, YakClient allows you to define `SourceSet`s that are 
reliant on specific Minecraft versions. We handle the activation and loading of these at runtime based on 
what environment your extension is loaded into. The term you will see when dealing with this feature is partitioning.

# Getting started

## Gradle
Currently, YakClient only supports gradle, so the happy-path is through a gradle java/kotlin project. 
Once you have your project setup, add the following to your `settings.gradle.kts` or `settings.gradle` file.

Kotlin: 
```kotlin
pluginManagement {
    repositories {
        maven {
            isAllowInsecureProtocol = true
            url = uri("https://maven.kaolinmc.com/snapshots")
        }
        gradlePluginPortal()
    }
}
```

Next, add the YakClient plugin and the maven-publish to your gradle file:
```kotlin
plugins {
    // ...
    
    id("maven-publish")
    id("com.kaolinmc") version "1.0.1"
    /* kotlin("kapt") version "1.8.10" - IF USING KOTLIN */ 
}
```

Add the kaolin repo to your build.gradle file:
```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.kaolinmc.com/snapshots")
    }
}
```

Make sure to reload gradle after this point (`./gradlew`, `gradle`, or if you are using an IDE like intellij you can click reload).
Next setup the rest of the gradle build file.

NOTE: If you are using MACOS add the following to your gradle file:
```kotlin
tasks.named<JavaExec>("launch") {
    jvmArgs("-XstartOnFirstThread")
}
```

Create your yakclient configuration:
```kotlin
yakclient {
    model {
        groupId = "<YOUR GROUPID>"
        name = "<YOUR ARTIFACTID>"
        version = "<YOUR VERSION>"

        packagingType = "jar"
        extensionClass = "<YOUR EXTENSION CLASS, we will set this later>"
    }

    // Your partitions, start by setting up the main one.
    partitions {
        create("main") {
            dependencies {
                minecraft("1.19.2")
                /* annotationProcessor("com.kaolinmc:yakclient-preprocessor:1.0-SNAPSHOT") - IF USING JAVA */
                
                /* "kapt"("com.kaolinmc:yakclient-preprocessor:1.0-SNAPSHOT") - IF USING KOTLIN */
                /* implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10") - IF USING KOTLIN */
            }
        }
    }
}
```

Lastly you will want to setup maven publish, without this the yakclient `launch` test will not work. 
Here is enough to get your started:
```kotlin
publishing {
    publications {
        create<MavenPublication>("<YOUR PUBLICATION NAME>") {
            artifact(tasks["jar"])
            artifact(project.buildDir.toPath().resolve("libs/erm.json")).classifier = "erm" // Keep this line

            artifactId = "<YOUR ARTIFACT_ID>"
        }
    }
}
```

For a complete example of the gradle build file with yakclient, see [example-extension](https://github.com/yakclient/example-extension).

## Your first extension

Now that you have setup gradle, you can create your extension entrypoint! This will be the class that receives update
on-start and on-end. A good name for an extension may be as follows: `com.kaolinmc.extensions.example.ExampleExtension`.
Next, implement the `com.kaolinmc.client.api.Extension` abstract class. For now, you can leave the `cleanup()` method
blank, and under the `init()` method you can add a println. 

Kotlin:
```kotlin
public class ExampleExtension : Extension() {
    override fun init() {
        println("Initing!!")
    }
    override fun cleanup() {
        // Do nothing
    }
}
```

Great! Thats your first extension complete! Now head to your command line and launch minecraft, for MacOS/Linux 
the command is `./gradlew launch -PmcVersion=<YOUR VERISON, ie, 1.20.1>` or for Windows 
`gradle launch launch -PmcVersion=<YOUR VERISON, ie, 1.20.1>`.

# Mixins

Mixins are an extremely common way of modifying Minecraft or changing how the game performs when there are no
existing APIs that implement this feature. Unlike other solutions that only let you work with the public 
precompiled already existing classes that are defined in a jar file, Mixins allow you to update the contents 
of a java program at runtime by injecting your java bytecode into another java .class file without having to 
modify any source files. Lets look at how this works in YakClient.

## Defining a Mixin

To define a mixin targeting a specific Minecraft Class, Annotate your abstract mixin class with
[`@com.kaolinmc.client.api.annotation.Mixin`](/client-api/src/main/java/net/yakclient/client/api/annotation/Mixin.java).
For example: 
```kotlin
@Mixin("a.minecraft.Class")
public abstract class MyFirstMixin {}
```

## Source Injections

Source injections are a type of mixin that allow you to inject code from one method into an existing method 
in Minecraft. The following is how you define this (API SUBJECT TO CHANGE).
```kotlin
// Your mixin class here...

@SourceInjection(
    point = BEFORE_END,
    from = "com.kaolinmc.extensions.example.MyFirstMixin",
    to = "net.minecraft.client.main.Main", // Some minecraft class
    methodFrom = "anExample()V", // Important, this must be a valid java methodName + java method Descriptor
    methodTo = "main([java/lang/String)V", // Same as above, this time targeting the minecraft method
    priority = 0 // Priority over other extensions, since we have no others, we can set this to 0. 
)
fun anExample() {
    println("A Mixin to : '${this::class.java.name}'") // This should print "A Mixin to : 'net.minecraft.client.main.Main'" if all goes well!
}
```
Lets go over each part here:
 - `point`: An injection point is the place in the method where an injection should be placed. For example `AFTER_BEGIN`
places the injection as the first line of the method we are injecting into. See [InjectionPoints](client-api/src/main/kotlin/net/yakclient/client/api/InjectionPoints.kt)
 - `from`: (**_SUBJECT TO CHANGE_**) The class that your mixin is defined is, in future updates this will not be needed.
 - `to`: (**_SUBJECT TO CHANGE_**) The class that your mixin is targeting, this will also not be necessary later on.
 - `methodFrom`: (**_SUBJECT TO CHANGE_**) The method name of your injection, This will include a valid java method
name + a valid java method descriptor. See the [java specification](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3).
 - `methodTo`: The method that your injection is targeting, also a java method name + its descriptor.
 - `priority`: The priority of your injection over those defined by other extensions and injections targeting
the same class, method and point combo.

## Method Injections
Unlike source injections, method injections simply inject an entire method into a class. They have a few benefits
and drawbacks: Unlike source injections, they cannot be performed once the game has started (jvm restrictions); they
can add in functionality and call-ability without having the worry of breaking a pre-existing code; they can only 
add functionality, not modify or remove it.

To define a method extension, do the following: 
```kotlin
// Your mixin class here
@MethodInjection(
    from = "com.kaolinmc.extensions.example.MyFirstMixin",
    to = "net.minecraft.client.main.Main",
    methodFrom = "Method to be injected(Ljava/lang/String;)V", // Yes, this works!
   // The rest will tell yakclient how to define the method once its injected. In later releases this will be optional.
    access = 1,
    name = "Method to be injected",
    description = "(Ljava/lang/String;)V",
    signature = "",
    exceptions = "",
    priority = 0
)
fun `Method to be injected`(arg1: String) {
    println("You called me with '$arg1'!")
   // Do other more complicated things
}
```

Ok, lets go over each part again.
 - `from`: See above
 - `to`: See above
 - `methodFrom`: See above
 - `access`: The java access code to be assigned to the newly created method. See [jvm specification on method accessibility](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6).
 - `name`: The name to be assigned to the newly created method.
 - `description`: The method descriptor to be assigned to the newly created method.
 - `signature`: The signature, you can almost always leave this blank. 
 - `exceptions`: The exceptions to this method, again, you can leave this blank.
 - `priority`: See above

## Field Injections

Field injections are what they sound like, they allow us to inject fields into classes.

The example:
```kotlin
@FieldInjection(
    name = "someField",
    access = 1,
    type = "Ljava/lang/String;",
    signature = "", 
    priority = 0
)
@JvmField // IMPORTANT FOR KOTLIN
public val someField : String = "" // this part will not happen. 
```
In the near future this form of annotation based declaration will change and all these fields will be filled out
by default depending on what is annotated, for now though this is necessary. 

# Partitioning

As discussed earlier, our system for dealing with varying minecraft versions is through partitions. Each partition
in your compiled extension will contain relevant code to 1 or more minecraft versions. Partitions like `main` will
be active for all minecraft versions and thus should only contain business logic that has no relevance to minecraft.
Of course for our basic example doing this is completely fine. When your extension is loaded into minecraft, partitions
that support the current minecraft version will become active. Here we will discuss several techniques that really make
this a fundamental concept of YakClient and how it allows developers to quickly iterate and develop for many
different minecraft versions without worrying about buggy hand written verison logic. 

## Defining partitions

In your gradle project, `SourceSet`s are used to separate your version logic - they will be later be compiled into your
jar file through the yakclient gradle plugin. To define a new partition do the following:
```kotlin 
// build.gradle.kts

yakclient {
    // Main source set ...
    
    partitions {
        named("1.19.2") {
            dependencies {
                // FOR KOTLIN
                //"kapt1.19.2"("com.kaolinmc:yakclient-preprocessor:1.0-SNAPSHOT")
                //implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                // FOR JAVA
                //annotationProcessor("com.kaolinmc:yakclient-preprocessor:1.0-SNAPSHOT")

                minecraft("1.19.2")
                implementation("com.kaolinmc:client-api:1.0-SNAPSHOT")
            }

            supportedVersions.addAll(listOf("1.19.2"))
        }
    }
}
```

Reload your project, and if you are using a gradle compatible IDE (eg. IntelliJ) you should be prompted to create
a new `sourceset` when creating a directory under `src/`. To define dependencies between partitions do the following:
```kotlin
// build.gradle.kts

yakclient {
    partitions {
        val other = named("1.18") {
            // Relevant code
        }
       
        named("1.19.2") {
            dependencies {
                //Relevant code from before ...

                compileOnly(other.sourceSet)
            }

            supportedVersions.addAll(listOf("1.19.2"))
        }
    }
}
```

## Partition overloading

One cool feature we get almost for free is partition overloading. If we dont want to define partitions that have 
dependencies on each other, for example the main needing version dependent info from another partition, we can 
define declarations that partitions overload. For example, if we define a class in the main partition like:
```kotlin
// com.kaolinmc.extensions.example.OverloadingExtample.kt @ main

// Class declaration ...

fun getMcVersion() : String {
    throw IllegalStateException("Illegal version of minecraft, no supporting version partition!")
}
```

Then we dont even have to implement this method, if we include a class with the exact same signature in another
partition (you should implement the overload in all other partitions) like:
```kotlin
// com.kaolinmc.extensions.example.OverloadingExtample.kt @ 1.19.2

// Class/method declaration, needs to be the same as before...

fun getMcVersion() : String {
   return "1.19.2"
}
```

Now if we launch minecraft 1.19.2, and from our main partition call the `getMcVersion()` method on init, you can see
it prints 1.19.2! There is a ton of functionality with this and allows developers to write their applications mostly 
version independently with only a few hooks into minecraft itself! This also means that YakClient APIs that other 
should follow this partitioning rule and expose their APIs through the main partition, then use version dependent
partitions to implement their APIs. Following this rule API consumer will be able to write almost completely version
independent code with extremely little overhead!



# Thanks, That's it!

Alright, thanks for reading! That's it! If you have any question at all please reach out to me on discord 
`@durganmcbroom`, submit a PR/bug here or join the [YakClient discord](https://discord.gg/dpGxnEtnw3)! 
