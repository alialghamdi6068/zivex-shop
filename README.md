# ZivexShop

VoidFlameMC shop plugin for Paper 26.2 / Java 25.

## Scope

- 27-slot main shop with fixed category slots 11-15.
- End, Nether, Gear and Food shops with the requested prices.
- Separate purchase GUI with quantity controls.
- No shard economy/database: Shard Shop is a placeholder for the separate shard plugin.
- Money is delegated to VoidFlame-Core through its EconomyService.
- PhoenixCrateLite and SmartSpawner delivery are configurable command bridges.
- Fixed item slots; no auto-placement or auto-sorting.
- Admin commands for reload, inspection, price/slot changes and delivery.
- GitHub Actions build on main.

## Commands

- /shop
- /shop reload
- /shop list
- /shop debug
- /shop setprice <category> <item> <price>
- /shop setslot <category> <item> <slot>
- /shop give <player> <category> <item> [amount]

/shopadmin and /zshop are admin aliases.

## Integrations

SmartSpawner delivery defaults to its current documented "ss give <player> smart_spawner <name> <amount>" form. Adjust the command in config.yml if the installed release uses a different configured type name.

PhoenixCrateLite is deliberately command-configurable because its exact key command depends on the installed release/configuration.

## Build

Requires Java 25 and Maven. Paper 26.2 is provided at compile time.