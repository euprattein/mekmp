# Mekanism: More Pump (`mekmp`)

A Minecraft **1.21.1 (NeoForge)** addon mod for **Mekanism** that adds **7 higher-tier Electric Pumps** (Advanced through Infinite) engineered for high-throughput industrial fluid extraction with minimal server TPS impact.

---

## Features

- **7 Progressive Tiers:**
  - **Standard Mekanism Tiers:** `Advanced`, `Elite`, `Ultimate`
  - **Mekanism: Extras Integration:** `Absolute`, `Supreme`, `Cosmic`, `Infinite` (automatically enabled when `mekanism_extras` is loaded)
- **In-World Tier Upgrading:**
  - **Sneak + Right-Click** a base Mekanism Electric Pump or any lower-tier pump with a matching **Tier Installer** (`mekanism:*_tier_installer` or `mekanism_extras:*_tier_installer`) to upgrade it in-place.
  - Preserves internal fluid tank contents, stored FE, installed upgrades, fluid container slots, and redstone control mode, while respecting Mekanism block security.
- **Full Mekanism Upgrade Support & Quick-Insert:**
  - Supports **Speed Upgrades** (up to 8), **Energy Upgrades** (up to 8), and **Filter Upgrade** (up to 1 for Heavy Water extraction).
  - **Sneak + Right-Click** a pump with upgrade items in hand to quickly insert them directly into the machine.
- **High-Throughput Pumping & Distance-Bucketed Fluid Chaining:**
  - Water and Heavy Water scale by `4x` per tier without consuming infinite water sources.
  - Non-water fluids (e.g., Lava) are extracted via a distance-prioritized frontier chain, harvesting up to `2^(tier + 1)` source blocks per operation (up to 128 blocks/op at Infinite tier) within an 80-block Manhattan range.
- **GUI & Automation:**
  - Automatically ejects fluid from the **top face** (`Direction.UP`) and accepts energy from the **back face**.
  - Built-in fluid container input/output slots for filling buckets and portable tanks, plus Redstone Mode cycling (`Ignored` / `High` / `Low`) and a Tank Dump button.
- **Server Configuration (`mekmp-server.toml`):**
  - Every tier's stats and base water/heavy water yields are configurable per world in `saves/<world>/serverconfig/mekmp-server.toml` and support live `/reload`.

---

## Default Tier Stats

| Tier | Required Installer | Multiplier | Water / Op | Heavy Water / Op | Tank Capacity | Usage (FE/t) | Energy Capacity | Max Chain / Op |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Advanced** | `mekanism:advanced_tier_installer` | 4x | 4,000 mB | 40 mB | 40,000 mB | 80 FE/t | 32,000 FE | 2 blocks |
| **Elite** | `mekanism:elite_tier_installer` | 16x | 16,000 mB | 160 mB | 160,000 mB | 160 FE/t | 64,000 FE | 4 blocks |
| **Ultimate** | `mekanism:ultimate_tier_installer` | 64x | 64,000 mB | 640 mB | 640,000 mB | 320 FE/t | 128,000 FE | 8 blocks |
| **Absolute*** | `mekanism_extras:absolute_tier_installer` | 256x | 256,000 mB | 2,560 mB | 2,560,000 mB | 640 FE/t | 256,000 FE | 16 blocks |
| **Supreme*** | `mekanism_extras:supreme_tier_installer` | 1,024x | 1,024,000 mB | 10,240 mB | 10,240,000 mB | 1,280 FE/t | 512,000 FE | 32 blocks |
| **Cosmic*** | `mekanism_extras:cosmic_tier_installer` | 4,096x | 4,096,000 mB | 40,960 mB | 40,960,000 mB | 2,560 FE/t | 1,024,000 FE | 64 blocks |
| **Infinite*** | `mekanism_extras:infinite_tier_installer` | 16,384x | 16,384,000 mB | 163,840 mB | 163,840,000 mB | 5,120 FE/t | 2,048,000 FE | 128 blocks |

*\*Available when **Mekanism: Extras** is installed.*

---

## Configuration

Upon loading a world, a server config file is generated at:
```text
saves/<world_name>/serverconfig/mekmp-server.toml

```

Configurable options include:

* `[general]`: `waterBaseAmountMb`, `heavyWaterBaseAmountMb`
* `[tiers.<tier_name>]`: `tankCapacity`, `energyCapacityFe`, `energyUsageFe`, `ticksPerOperation`, `pumpMultiplier`, `maxRange`, `maxChainBlocksPerOperation`

---

## Requirements

* **Minecraft:** 1.21.1
* **Mod Loader:** NeoForge
* **Required Dependency:** Mekanism
* **Optional Dependency:** Mekanism: Extras

---

## Building from Source

```bash
git clone [https://github.com/euprattein/mekmp.git](https://github.com/euprattein/mekmp.git)
cd mekmp
./gradlew build

```

The compiled mod `.jar` will be output to `build/libs/`.
