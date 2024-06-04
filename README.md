**Note: This is the branch for the variant of SLL that uses sponge's mixin implementation
to process mixins.** Compared to the launcher-micromixin variant of SLL, the launcher-sponge
variant has a slower release cycle, meaning that not all features will be present.
This variant mostly exists in order to be able to test the compliance of micromixin.
Developers should use the micromixin variant whereever possible, unless a mod demands more
powerful features only present in the spongeian variant.

# Starloader

## Starloader vs Starloader-API

The starloader project on its own doesn't do much other than providing a way
to loader secondary jars (extensions) as well as providing mixins and a bytecode
manipulation framework. As such most extensions should make use of Starloader-API
to avoid conflicts.

## Maven

Nightly builds of this application are provided on our maven repository,
https://stianloader.org/maven/ .

## IRC Chat

We do have an IRC channel for anyone that is interested in modding the game in
general, although the main purpose of the channel will be to discuss Starloader.
Feel free to take a look at it at #galimulator-modding @ irc.esper.net

## Building

<b>Due to how the the people over at Sponge compile their module-infos,
  building SLL requires Java 16 or higher. Running SLL works with Java 1.8
  and beyond regardless.</b>

The project can be built via maven and a JDK 16 or higher (JDK 17 is recommended).
This can be easily done via `mvn install` on most systems.
The built jar is located in the target folder. (Note: you probably
don't want the jar prefixed with "original-")

## Running

On MacOS it has to be run manually via a Java 1.8 JRE (17 recommended) with a command such
as `java -cp starloader-launcher-XYZ-all.jar de.geolykt.starloader.launcher.CLILauncher`

Place the built `starloader-launcher-XYZ-all.jar` in the galimulator folder.
Then edit the `config.json` file in the galimulator folder to be

```json
{
  "classPath": [
    "jar/galimulator-desktop.jar",
    "starloader-launcher-XYZ-all.jar"
  ],
  "mainClass": "de.geolykt.starloader.launcher.CLILauncher",
  "vmArgs": [
    "-Dsun.java2d.dpiaware=true"
  ]
}
```
Make sure to modify the `mainClass` entry to be equal to the one shown above,
The `starloader-launcher-XYZ-all.jar` entry in the `classPath` entry should
correspond to the name of the starloader-launcher file you copied into the
galimulator folder.

Mods need to be added in a "mods" folder located in the galimulator directory.

## Licensing and redistributing

All code is licensed under the Apache 2.0 license, allowing you to redistribute
the source.

Additionally a few portions of our code (i. e. large parts of the selfmodification and extension system)
was largely written by Minestom contributors, who have contributed their code under
the Apache 2.0 license
