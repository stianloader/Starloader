# Starloader

## Starloader vs Starloader-API

The starloader project on its own doesn't do much other than providing a way
to loader secondary jars (extensions) as well as providing mixins and a bytecode
manipulation framework. As such most extensions should make use of Starloader-API
to avoid conflicts.

## IRC Chat

We do have an IRC channel for anyone that is interested in modding the game in
general, although the main purpose of the channel will be to discuss Starloader.
Feel free to take a look at it at #galimulator-modding @ irc.esper.net

## Building

The project can be built via gradle and a JDK 11 or higher.
This can be easily done via `./gradlew shadowJar` on most systems.
The built jar is located in the build/libs folder, and should have a "-all"
suffix, if that isn't the case you might have not built that shadowjar, which
will not run as it is missing vital dependencies.

## Running

The jar then needs to be placed at the Galimulator folder 
(home/.steam/steam/steamapps/common/Galimulator in my case)
and can then be executed via

    java -jar Galimulator-Starloader-1.0.0-SNAPSHOT-all.jar

or similar. However Java 11+ needs to be used.
If you are a linux (or any in fact) user, then you should use AdoptopenJDK

over your vendor JDK. Many vendor JDKs are known to not work

with libGDX, one of galimulator's main libraries.

## Licensing and redistributing

All code is licensed under the Apache 2.0 license, allowing you to redistribute
the source.

Additionally a few portions of our code (i. e. the whole selfmodification and extension system)
was largely written by Minestom contributors, who have contributed their code under
the Apache 2.0 license
