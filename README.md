# Starloader

## Starloader vs Starloader-API

The starloader project on its own doesn't do much other than providing a way
to loader secondary jars (extensions) as well as providing mixins, a bytecode
manipulation framework. As such most extensions should make use of Starloader-API
to avoid conflicts, simpler extensions however don't need Starloader-API built.

## Building

The project can be built via gradle and a JDK 11 or higher.
This can be easily done via `./gradlew shadowJar` on most systems,
although the JDK needs to be installed manually and the galimulator jar needs 
to be present at the project root under `galimulator-desktop.jar`.

To compile most other extensions (inclding the API) you additionally need to use

    ./gradlew publicToMavenLocal

There is a workaround by linking against minestom instead, but that is discouraged

## Running

The built jar is located in the build/libs folder, and should have a "-all"
suffix, if that isn't the case you might have not built that shadowjar, which
will not run as it is missing vital dependencies. The jar can be moved to replace
the original galimulator jar in your runtime folder 
(home/.steam/steam/steamapps/common/Galimulator/jar in my case)
The jar can then be executed via

    java -jar jar/Galimulator-Starloader-0.0.1-SNAPSHOT-all.jar

or similar. However Java 11 (higher might also work) needs to be used
but OpenJDK 11 as offered by some major linux distro vendors is known to NOT work,
so if there is an obscure error with ld.so you should be using AdoptOpenJDK or similar

## Licensing and redistributing

All code is licensed under the Apache 2.0 license, allowing you to redistribute
the source. You MAY NOT redistribute the built shadowjar as it contains
code written by people that have not opted into a license that allows that.

Additionally the net.minestom.server package was 
written by contributors of the Minestom project, licensed under the Apache 2.0 project.
