# Session log — studio/v0 (2026-05-16, session 2)

## Branch state
- Branch: `studio/v0`
- Last committed: **9b537c1** — `M3.5: mouse-driven default scene + live preview fixes`
- Uncommitted on top of that: the smoke/fluid dissipation fix + smoke tuning + velocity
  blend/display knobs on `FluidNode` + particle trail-decay lifespan on
  `FlowFieldParticlesNode` + retuning of `DefaultScene`. The previous session's
  in-shader backdrop composite in `GLPreviewPanel` is still uncommitted too.

## The big find from this session
Last session's "lines never dissipate" theory blamed the un-premultiply step in
`renderFluid.frag` and added an in-shader backdrop composite in `GLPreviewPanel`.
That helped, but it wasn't the root cause. After instrumenting the actual
`tex_density` alpha across the whole canvas, we proved the fluid sim IS
dissipating correctly (alpha goes to 0 within ~1.5s after release across every
pixel).

The real bug: **`DwFluid2D.renderFluidTextures` uses `GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA`
blending and never clears `target` itself.** Once a pixel was drawn bright, later
frames with `src.a = 0` left dst RGB untouched, so the visible smoke was a stale
phosphor trail of historical pixel brightness, not the live texture.

**Fix:** clear `target` to transparent each frame in `FluidNode.evaluate()`
immediately before calling `fluid.renderFluidTextures`. See `FluidNode.java`
around the `f.pixelFlow().begin()` … `glClear(GL_COLOR_BUFFER_BIT)` block.

This is the single most important change in the uncommitted diff.

## Secondary bug — velocity blend mode "stuck paths"
`DwFluid2D.addVelocity` defaults to **MAX_MAGNITUDE** blend (mode 2). Logic in
`addVelocityBlob.frag`:
```glsl
if (length(vOld) > length(vNew)) glFragColor = vOld;
```
So once a drag wrote high-magnitude velocity to a cell, subsequent slower drags
were silently rejected — new strokes followed the original drag's path because
old velocities stayed "occupied" until natural decay (slow at v_diss=0.92).

**Fix:** `FluidNode` now exposes `pVelocityBlendMode` (default **1 = ADD**), so
new strokes always contribute. `MAX_MAGNITUDE` is still available (mode 2) as
the library's original behavior.

## New params on FluidNode (uncommitted)
- `pVelocityBlendMode` — int 0..2, default 1 (ADD). See above.
- `pDisplayMode` — int 0..3, default 0. Passed through to `renderFluidTextures`.
  Lets you wire `density` output to screen and switch the visualization between
  density (0), temperature (1), pressure (2), or velocity-as-color (3).
- `pShowVelocityVectors` — bool, default false. When true, overlays
  `renderFluidVectors(target, spacing)` arrows on top of whatever `display_mode`
  is showing. Debug aid for confirming the field is actually updating.
- `pVectorSpacing` — int 4..128, default 16. Pixel spacing between vectors.

## New on FlowFieldParticlesNode (uncommitted)
- `pTrailDecay` — float 0..1, default 0.03. Per-frame multiplicative decay of
  a new ping-pong trail buffer. Replaces the per-frame canvas clear: previous
  trail is faded by `(1 - decay)` into the back buffer, particles render
  additively on top, output canvas = `bg + trail`. Net effect: particles have
  visual lifespan + smooth fade without needing per-particle age tracking in
  `DwFlowFieldParticles`. 0 = trail forever (old pile-up behavior). 1 = no
  trail (old discrete-particle behavior).
- Composite uses `DwFilter.merge` (additive) — see `Merge.TexMad` usage in
  `FlowFieldParticlesNode.evaluate()`.

## Current tuned DefaultScene values
```
fluid.pDissipationVelocity    = 0.92    (slow decay — keeps smoke MOVING for a long time)
fluid.pDissipationDensity     = 0.999   (very slow density decay — smoke visible for many seconds)
fluid.pDissipationTemperature = 0.85
fluid.pVorticity              = 0.30
fluid.pApplyBuoyancy          = true
fluid.pInjectTemperature      = 3.0
fluid.pInjectRadius           = 30
fluid.pInjectColor            = (1.0, 0.7, 0.25, 1.0)   (max alpha + brighter RGB after we fixed the clear bug)
fluid.pVelocityBlendMode      = 1       (ADD, default — new strokes always reshape the field)
fluid.pDisplayMode            = 0       (density)
fluid.pShowVelocityVectors    = false

particles.pSpawnPerFrame      = 200
particles.pSpawnRadius        = 22
particles.pPointSize          = 4
particles.pColorA             = (1, 0.85, 0.4, 1.8)
particles.pAccelerationMul    = 6.0
particles.pVelocityDamping    = 0.92
particles.pTrailDecay         = 0.03    (default — ~1s particle trail lifespan)
```

User feedback at end of session: smoke looks good, motion lingers, brightness
is fine. May want further fine-tuning of velocity/density dissipation later
purely from the param sliders — no code changes anticipated.

## Files touched but not yet committed
- `modules/studio-app/src/main/java/studio/nodes/fluid/FluidNode.java`
  (target-clear fix + pVelocityBlendMode + pDisplayMode + pShowVelocityVectors
   + pVectorSpacing; calls addVelocity with explicit blend mode now)
- `modules/studio-app/src/main/java/studio/nodes/flowfield/FlowFieldParticlesNode.java`
  (pTrailDecay + trailA/trailB ping-pong + Mad/Merge composite path)
- `modules/studio-app/src/main/java/studio/ui/DefaultScene.java`
  (tuned all fluid + particle defaults toward the new ranges that work with the
   target-clear fix; inject_color back up to (1.0, 0.7, 0.25, 1.0))
- `modules/studio-app/src/main/java/studio/ui/GLPreviewPanel.java`
  (in-shader backdrop composite from previous session — still useful for the
   blit; not the root-cause fix)

## Up next (queued, in priority order)
1. **Fine-tune from sliders.** User will likely play with v_diss, d_diss, inject
   color, trail_decay, vorticity. No code changes expected unless something
   surprising surfaces.
2. **UI cleanup pass** (still outstanding from last session), referencing
   `pixelflow_windtunnel.PNG` (in repo root). User priorities:
   - Preview pane too small vs editor. Flip the `MainFrame` vertical split
     toward preview-dominant (~60–65%).
   - `ParameterPanel` redesign — compact horizontal sliders with the value
     baked into a fill bar, label-left/value-right, near-black background,
     high-contrast pop-out. Booleans become flat pill toggles.
3. **Wind-tunnel mode** (deferred). User pointed at vimeo.com/184850259.
   When pursued, the path is:
   - Add an `ambient_wind` (vec2) param on `FluidNode` so the whole field
     gets a constant per-frame velocity push.
   - Or build a small `EmitterNode` (continuous density+velocity source on
     one edge) + `AmbientForceNode`; composes cleanly with FluidNode.
4. **Commit + cleanup.** Once tuning settles, bundle the diff into commits.
   Suggested split:
   - One commit: "fix(fluid): clear render target each frame so density fade
     is visible — DwFluid2D's renderFluidTextures never clears, alpha-blend
     was caching brightest historical pixel."
   - One commit: "feat(fluid): expose velocity blend mode, display mode,
     velocity-vector overlay on FluidNode."
   - One commit: "feat(particles): trail-decay buffer gives particles a
     visual lifespan without per-particle age tracking."
   - One commit: "tune: DefaultScene retuned for long-lived smoky look now
     that fluid renders correctly."
   - Or just bundle the whole thing under "fix smoke dissipation + tune scene
     for smoke + particle lifespan + fluid debug knobs" if you'd rather not
     split.

## How to relaunch
```powershell
$env:JAVA_HOME = "D:\jdk17\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :studio-app:run
```
Headless smoke for graph validation:
```powershell
.\gradlew.bat :studio-app:run `
  "-PmainClass=studio.headless.HeadlessSmoke" `
  "-Pproject=default" "-Pframes=120" "-Pout=smoke.png"
```

## Debugging hooks left behind (none currently in code)
The per-frame `tex_density` whole-canvas max-alpha probe in `FluidNode.evaluate()`
was used to nail the target-not-cleared bug and has been removed. If a similar
issue surfaces on velocity, you can mirror it: call `fluid.getDensity(buf)` or
add a similar `fluid.getVelocity(buf)` readback, scan for max magnitude, log
every ~30 frames. The technique is to always sample THE WHOLE TEXTURE — single-
pixel probes at the cursor position give a misleading "0" because density/velocity
get advected away from the sample point.
