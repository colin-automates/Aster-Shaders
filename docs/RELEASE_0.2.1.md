# Aster 0.2.1

This snapshot contains the current Sodium/Iris shader pack, its unchanged 0.2.0 material companion, source, packaging tools and earlier native Vulkan renderer reference code.

## Corrective changes

- Opaque hand rendering writes material attachments without alpha blending. Translucent hand and entity passes also replace material data directly while retaining colour blending.
- Deferred lighting and world fog preserve completed forward hand lighting. Hands use an unjittered projection and bypass temporal history.
- Temporal reconstruction uses cubic history sampling, variance clipping, point-sampled depth and view-space depth rejection. Moving foliage uses less history weight.
- Accepted opaque cutout texels preserve vertex ambient occlusion separately from mipmapped texture alpha, keeping distant foliage lighting from becoming faint.
- Iris recognizes the NORMAL_MAPS option. Existing authored lighting, water, clouds, material relief, bloom and exposure settings are retained.

## Validation scope

The private automated checks reviewed 247 sampled frames, including bare hands above ground and water, night scenes, held items, foliage and a copy of the user's saved world in still and moving views. Active shader rendering, absence of fallback and zero recorded GPU/OpenGL errors were checked. These are automated observations, not ordinary long-session gameplay acceptance.

Fresh matched 0.2.0 and 0.2.1 tests ran on an AMD Radeon RX 9070 XT with Minecraft 26.3, Sodium 0.9.2 and Iris 1.11.7. Each workload used three 60-second repeats per build after at least 30 seconds of warmup and settled terrain, at 1920x1080, 16 chunks, simulation distance 8, FOV 70, VSync off and uncapped rendering. Background applications, including CS2, remained open and their sampled load passed the comparability limits. There were no screenshot readbacks during timing.

| Workload | Aster 0.2.0 FPS | Aster 0.2.1 FPS | Mean frame-cost change |
| --- | ---: | ---: | ---: |
| Above-water pool | 71.70 | 71.63 | +0.08% |
| Underwater | 80.74 | 79.87 | +1.06% |

FPS is 1000 divided by equal-repeat mean frame cost. The twelve measured repeats retained 54,704 raw frame intervals, with no recorded GPU/OpenGL errors or frames over 100 ms. These hidden-window measurements describe the matched test workload and desktop load; they are not a universal gameplay FPS claim. Raw private test data and saved worlds are not published here.

## Packages and limitations

The shader archive SHA-256 is `9714ba3e00ef19022197531bf7af559cded9d27fa55fc548cef029b18b591fa9`. The material archive SHA-256 is `97ac049521639e3cacbaa8e9a50c06496d416bc3ae8f4b680f1eec5749b00537`.

The material companion and original shading modules were preserved. The shader archive's internal README retains its older 0.2.0 label; it is kept byte-for-byte to preserve the qualified artifact.

Offscreen reflection coverage, underwater shafts, shadows and shoreline behaviour still differ from Complementary. Feature coverage does not establish visual parity. The current qualified stack uses OpenGL. Vulkan shader-loader compatibility, performance on Vulkan and ordinary long-session acceptance remain unverified for this release.
