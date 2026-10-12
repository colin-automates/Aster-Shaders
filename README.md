# Aster Shaders

Aster 0.2.1 is an independently authored Minecraft shader pack with atmospheric clouds, water waves and caustics, material relief, foliage motion, shadows, bloom, exposure and temporal anti-aliasing.

The current verified setup is **Minecraft 26.3, Sodium 0.9.2 and Iris 1.11.7 on OpenGL**. The former Aster Vulkan client mod is not required. A native Vulkan + Sodium + Vitrail route is under investigation; this release has not been qualified on that stack.

## Download and install

1. Install the matching [Sodium](https://modrinth.com/mod/sodium) and [Iris](https://modrinth.com/mod/iris) builds for Minecraft 26.3.
2. Download [Aster-Sodium-0.2.1.zip](https://github.com/colin-automates/Aster-Shaders/raw/refs/heads/main/dist/Aster-Sodium-0.2.1.zip) into your instance's `shaderpacks` folder.
3. Download [Aster-Materials-0.2.0.zip](https://github.com/colin-automates/Aster-Shaders/raw/refs/heads/main/dist/Aster-Materials-0.2.0.zip) into `resourcepacks` and enable it in Minecraft. The companion's version remains 0.2.0 because its contents are unchanged.
4. Select Aster in Iris's shader screen and use the OpenGL graphics backend.

The checked-in archives match the current installed build. Their hashes are in [dist/SHA256SUMS.txt](dist/SHA256SUMS.txt). The shader archive retains its original internal README, which says 0.2.0; the ZIP filename, source and checksum identify the corrective 0.2.1 build.

## Source and build

- `shaderpacks/Aster/`: active shader-pack source and authored cloud-noise atlas.
- `materials/aster-materials.json`: Aster's material definitions.
- `scripts/build-sodium-pack.py`: deterministic shader-pack packaging.
- `scripts/build-sodium-materials.py`: companion normal-map generation from a locally installed Minecraft client.
- `scripts/write-sodium-programs.py` and `scripts/templates/`: shader entry-point generator and cloud template.
- `src/`: earlier native Vulkan renderer source retained for reference; the current Gradle build excludes it.

Build instructions are in [docs/BUILD.md](docs/BUILD.md). Minecraft client files, third-party mods and shader packs, worlds, launcher state, and private test captures are excluded from this repository.

## Release status

Version 0.2.1 corrects the bare hand showing terrain, distant cutout foliage and overly soft temporal detail. Matched automated rendering and performance checks passed on an RX 9070 XT. See [docs/RELEASE_0.2.1.md](docs/RELEASE_0.2.1.md) for the scope and results.

Aster's lighting, BRDF, water, sky, foliage and tone-mapping code is independently authored. No Complementary code or assets are included. Full visual parity with Complementary and ordinary long-session gameplay acceptance remain unproven.
