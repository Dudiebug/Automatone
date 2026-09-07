# Automatone
[![Release](https://img.shields.io/github/release/Dudiebug/Automatone.svg)](https://github.com/Dudiebug/Automatone/releases/latest)
[![License](https://img.shields.io/badge/license-LGPL--3.0%20with%20anime%20exception-green.svg)](LICENSE)
[![Code of Conduct](https://img.shields.io/badge/%E2%9D%A4-code%20of%20conduct-blue.svg?style=flat)](https://github.com/cabaletta/baritone/blob/master/CODE_OF_CONDUCT.md)

A serverside Minecraft pathfinder bot, based on Baritone.

**Warning: this project is experimental. Although it is already performing well in vanilla, strange bugs may arise in heavily modded environments.
Backwards compatibility is also not being considered at the current time, so avoid depending on this for stable projects.**

There's a [showcase video](https://youtu.be/CZkLXWo4Fg4) made by @Adovin#0730 on Baritone. [Here's](https://www.youtube.com/watch?v=StquF69-_wI) a (very old!) video leijurv made showing off what it can do. [Tutorial playlist](https://www.youtube.com/playlist?list=PLnwnJ1qsS7CoQl9Si-RTluuzCo_4Oulpa)

## Automatone 1.0 for NeoForge 1.21.1

Install **`automatone-bundled-1.0.jar`** from [Releases](https://github.com/Dudiebug/Automatone/releases/latest) on your server and participating clients, or in singleplayer. Remove both older Automatone JARs first. Requires Minecraft 1.21.1, Java 21 and NeoForge 21.1.249 or a compatible newer 21.1 build. The one JAR contains both internal mods and automatically uses the appropriate client and server behavior.

Workers support finite/unlimited mining, inventories, fleet controls, collection, archives, relocation and notifications. See the [installation and testing guide](docs/M6_RELEASE_GUIDE.md) and [controller guide](docs/M5_GLOBAL_MANAGEMENT_USER_GUIDE.md). Build/unit/architecture checks are recorded separately from in-game acceptance, which remains **PENDING — HUMAN TESTING**.

The older installation, API, Fabric and Cardinal Components material below is historical; use the NeoForge guide above for this release.

This project is based on Baritone, which is itself an updated version of MineBot,
the original version of the bot for Minecraft 1.8.9, rebuilt for 1.12.2 through 1.16.5.
Automatone/Baritone focuses on reliability and particularly performance (it's over [30x faster](https://github.com/cabaletta/baritone/pull/180#issuecomment-423822928) than MineBot at calculating paths).

## Getting Started

Here are some links to help to get started (sending you to Baritone documentation for now):

- [Features](FEATURES.md)

- [Installation & setup](SETUP.md)

- [API Javadocs](https://baritone.leijurv.com/)

- [Settings](https://baritone.leijurv.com/baritone/api/Settings.html#field.detail)

- [Usage (chat control)](USAGE.md)

## API

The API is heavily documented. The Javadocs for Automatone itself are not currently hosted anywhere, but you can find
the (possibly inadequate) Javadocs for the latest release of Baritone [here](https://baritone.leijurv.com/).
Please note that usage of anything located outside of the ``baritone.api`` package is not supported by the API release
jar.

Below is an example of basic usage for changing some settings, and then pathing to an X/Z goal.

```java
IBaritone baritone = BaritoneAPI.getProvider().getBaritone(entity);
baritone.settings().allowSprint.set(true);
baritone.settings().primaryTimeoutMS.set(2000L);

baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(10000, 20000));
```

### Making your own non-player entity use Automatone
You need to register the relevant component for your entity, using the component factory provided
by Automatone:

```java
public final class MyComponents implements EntityComponentInitializer {
    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(MyEntity.class, IBaritone.KEY, BaritoneAPI.getProvider().componentFactory());
    }
}
```
> Do not forget to add that class as an entrypoint with the `cardinal-components-entity` key !
```json
{
  "entrypoints": {
    "cardinal-components-entity": [
      "my.package.MyComponents"
    ]
  }
}
```
For more information on how components work, refer to [Cardinal Components API's documentation](https://github.com/OnyxStudios/Cardinal-Components-API/wiki).

## FAQ

### Can I use Automatone as a library in my mob mod?

That's what it's for, sure! (As long as usage complies with the LGPL 3.0 License)

### Can I use Automatone to cheat?
Good luck, as of now it is completely unable to control real players.

### How is it so fast?

Magic. (Hours of [leijurv](https://github.com/leijurv/) enduring excruciating pain)
