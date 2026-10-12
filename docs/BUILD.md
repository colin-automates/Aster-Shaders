# Building Aster 0.2.1

Run these commands from the repository root. Python 3.12 is the tested packaging runtime; the material builder also needs Pillow and NumPy.

```sh
python -m pip install -r requirements-build.txt
python scripts/build-sodium-pack.py
```

This creates `build/shaderpacks/Aster-Sodium-0.2.1.zip` from the checked-in shader source. It does not need the Minecraft client or the former Aster Vulkan mod. The archive uses sorted entries and fixed timestamps.

To rebuild the optional material companion, supply a locally installed, legitimate Minecraft 26.3 client JAR:

```sh
python scripts/build-sodium-materials.py --minecraft-client "/path/to/26.3.jar"
```

On Windows the default client path is `%APPDATA%/.minecraft/versions/26.3/26.3.jar`. The result is `build/resourcepacks/Aster-Materials-0.2.0.zip`. The script generates normal maps from the client's block textures; it includes no diffuse textures, client JAR or game code in the output.

The Gradle wrapper builds both packs. Java 25 is the tested Java runtime. With Python and its dependencies available on PATH:

```sh
./gradlew build -PminecraftClient="/path/to/26.3.jar"
```

Use `gradlew.bat` on Windows. The wrapper downloads the checksum-pinned Gradle distribution on its first run. Build receipts are written under the ignored `tools/aster-sodium-migration/evidence` directory. Neither builder launches Minecraft or changes an installed instance.

The committed `dist/` archives are the current qualified build. Building from source writes to `build/`; it does not replace those committed archives. Compare SHA-256 checksums with `dist/SHA256SUMS.txt` when reproducing the published package. ZIP compression and PNG encoding can vary with runtime/library versions.

`scripts/write-sodium-programs.py` regenerates shader entry points and overwrites files in `shaderpacks/Aster/shaders`. It is an authoring tool, not a necessary packaging step. The previous native mod sources under `src/` are reference material and are not compiled by the current build.
