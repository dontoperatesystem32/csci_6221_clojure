# Ray Marcher Development Diary

Shared development record for Rufat Guliyev and William Switzer. Dates use America/New_York time. Record completed work and evidence here after each meaningful session; keep proposals and future work explicitly labeled. This is also source material for the final university report.

## 2026-10-23 — Milestone 1: application and dummy image

### Objective and starting point

Established a simple graphical application before implementing the ray marcher, with organized source files and an ongoing development record. The repository previously contained language-evaluation materials and rough ray-marcher requirements, but no renderer.

### Implemented

- Added a Clojure CLI project with a pinned Clojure 1.12.6 dependency and a `:run` alias.
- Added a Swing window that displays a completed Java `BufferedImage`, with a label identifying it as a dummy image.
- Created a gradient background and an orange checkerboard circle directly from pixel coordinates. This is a 2D placeholder; it uses no rays or 3D scene calculations.
- Added configurable image width and height (default 800 × 450), scrolling for large images, help, and PNG export without a window.
- Separated startup/options, image generation/output, and window display into three namespaces.

### Decisions and reasoning

- Use Java Swing for the display and Java image facilities for storage/output, called from Clojure. No separate Java source files or external graphics framework are needed for this milestone.
- Keep the window independent of image generation so a future ray marcher can supply the image without moving rendering logic into UI code.
- Build the completed image before scheduling the window. Swing creation happens on its event dispatch thread. The UI receives an image that is no longer being modified.
- Planned an independently callable ray marcher receiving an origin and a normalized direction. This remains the intended next building block; it is not implemented yet. One primary ray per pixel is the initial rendering plan, with additional rays possible later for sampling, shadows, and reflections.
- Scene data, lighting, parallel rendering, and a full camera model remain future work. The current separation leaves space for them without introducing empty framework components.

### Verification

- Generated and visually inspected an 800 × 450 PNG: gradient and checkerboard circle displayed correctly.
- Exercised image generation at 1 × 1, 320 × 180, and 1920 × 1080. Confirmed the dimensions and checked option parsing, including invalid/missing dimensions and unknown options.
- Verified the Clojure CLI run alias and PNG export with the declared dependencies. Dependency downloads required network access outside the execution sandbox; a project-local temporary dependency cache was used for these checks.
- Opened the actual Swing window on the desktop, confirmed it was visible at 900 × 600 and that UI inspection ran on Swing's event dispatch thread, captured and visually inspected its painted contents, then closed it. Native window creation required execution outside the sandbox; the sandboxed attempt terminated before displaying a window.
- Verified the CLI help output. Temporary preview images, dependency files, and verification helpers are kept under the existing ignored `.codex-build/` directory.

### Limitations and next steps

- No ray marching, camera, lighting, animation, parallel rendering, or live image replacement exists yet.
- Image dimensions and window dimensions are independent; the picture is not resampled when the window is resized.
- Extremely large image dimensions can exhaust memory. PNG export requires an existing destination directory.
- Next: vector operations, a sphere distance function, and a single-ray marcher. Verify a known hit and a miss before generating a full image. Proposed marcher results should distinguish a hit, travel-limit miss, and step-limit termination.

## 2026-10-25 — Milestone 2: fundamental sphere ray marcher

### Objective and starting point

Built a developable MVP with the camera at `(0, 0, 0)` and a sphere at `(10, 0, 0)` with radius `2`, extending the previous dummy-window application.

### Implemented and decisions

- Replaced the dummy picture in the application with actual sequential CPU sphere tracing, one primary ray through each pixel center. Removed the obsolete dummy generator.
- Added small namespaces for vector math, signed-distance scene functions, the single-ray marcher, perspective camera, and image rendering. PNG output and Swing display remain separate.
- The camera looks along +X with +Y as up and a 60-degree vertical field of view. Image aspect ratio is accounted for so the sphere remains circular in rectangular images.
- `raymarch` accepts the scene function, origin, normalized direction, and optional settings. This keeps single-ray traversal independently callable. No global object collection is required.
- Distance is evaluated and checked before moving. Default limits are 128 distance evaluations and 100 world units, with a surface tolerance of 0.001 world units.
- Results distinguish hits, travel-limit misses, and step-limit termination. Hits include world-space position; results include distance and step count. Starting inside a solid is explicitly treated as an immediate hit.
- Added a sphere-distance constructor and a union combinator for future scenes. Renderer inputs allow replacement of the scene, camera, marching settings, and color function.
- Used flat orange for hits and dark blue for misses. Magenta identifies unresolved step-limit rays. Lighting is deliberately a later step, so the sphere currently looks like a flat circle despite being calculated from 3D geometry.
- Updated the window title/status, README, and added a runnable test alias.

### Verification

- `clojure -M:test`: 4 tests, 9,986 assertions, zero failures/errors. Covered known sphere distances, center hit at `(8, 0, 0)`, rays pointing away or sideways, empty scenes, travel and step limits, inside starts, normalized camera directions, and invalid zero-vector/FOV cases.
- Independently checked image pixels against analytic ray–sphere intersections for portrait/landscape images and centered/off-center spheres. Excluded a narrow boundary band where the marching tolerance intentionally differs from an exact intersection.
- Exported actual rendered PNGs at 800 × 450 and 1920 × 1080; visually inspected the default-resolution image.
- Opened the updated Swing window, verified visibility and dimensions on the event dispatch thread, captured its painted contents, and closed the verification window. Native desktop verification used execution outside the sandbox.
- Checks used the existing project-local dependency cache. Preview images and temporary window-check helpers remain under ignored `.codex-build/`.

### Limitations and next steps

- Sequential rendering only. No lighting, shadows, antialiasing, reflections, animation, or interactive camera controls yet. No performance claims were established in this session.
- `raymarch` requires unit ray directions, valid positive settings, and appropriate distance functions. It does not turn arbitrary mathematical equations into safe distance estimates.
- Finite surface tolerance can expand silhouettes slightly; grazing rays may require more steps. Step-limit termination is not silently treated as a proven miss.
- The app renders before opening the window; future interactive rerendering should use background workers and publish completed images to Swing.
- Next suggested milestone: estimate surface normals at hit positions, add one directional light, and keep that shading independent of the ray marcher. Additional geometry and parallel rendering can follow.

## 2026-10-25 — Longer-term rendering goals

Future exploration goals include somewhat realistic black holes, fractals, wormholes, 4D objects projected into 3D space, and non-Euclidean spaces. These are not implemented features or fixed university-project deliverables. The current implementation remains the sphere-tracing MVP described above.

## 2026-10-26 — Milestone 3: interactive camera controls

### Implemented and decisions

- Added WASD movement along the horizontal XZ plane relative to camera heading, with Shift to rise and Control to descend along world Y. Looking upward does not change the horizontal movement plane.
- Added left-button mouse dragging for look direction, with pitch limited to ±89 degrees. Dragging avoids system-wide pointer capture. R restores the original camera; Escape clears movement and ends the current drag.
- Movement uses elapsed time at 3 world units per second, with combined directions normalized to prevent faster diagonal travel. Long UI pauses have their movement interval capped at 0.1 seconds to avoid large jumps.
- Held input is cleared when the window loses focus. Closing the viewer stops its timer, unregisters the keyboard handler, and shuts down the rendering worker.
- Added a pure controls namespace for pose/movement calculations and an independent worker namespace for rendering requests. The single-ray marcher and scene geometry are unchanged.
- The window now opens before rendering completes. A single background worker renders camera snapshots; Swing handles input, painting, and completed-image updates on its event dispatch thread.
- While moving, previews use a maximum dimension of 240 pixels. After 250 ms without camera changes, the viewer requests the configured full resolution. The image scales to fit the window without changing its aspect ratio.
- Kept only one pending render request so rapid input cannot build an unbounded queue. Previews can finish during movement; obsolete full-resolution renders are cancelled between rows and are not displayed. Published images are never modified afterward.
- Added status text showing preview/full resolution and the camera position associated with the displayed image. PNG export remains a single render from the original camera.

### Verification

- `clojure -M:test`: 9 tests, 10,010 assertions, zero failures/errors. Existing geometry tests still pass. New checks cover movement independent of frame subdivision and camera tilt, diagonal speed, heading, pitch limits, renderer cancellation, latest-pending-view replacement, and cancellation of obsolete full frames.
- Exercised the actual desktop window with key and mouse events: W + Shift changed both horizontal and vertical position, Control moved downward, mouse dragging changed the rendered image, R reset the view, and Escape/focus loss stopped held movement.
- Verified that desktop image calculations ran outside Swing's event dispatch thread and that rendering returned to full resolution after movement stopped. Closing the window terminated its renderer thread.
- Captured and visually inspected the viewer with its controls and status line. Native window checks used execution outside the sandbox.
- Exported an 800 × 450 PNG and confirmed its SHA-256 checksum exactly matched the previous MVP export. Interactive controls did not change the original-camera output.

### Limitations and next steps

- Mouse look currently requires holding the left button; continuous captured-pointer look is not implemented.
- Pixel rendering within each frame remains sequential. Preview lag and refresh speed depend on scene complexity and hardware; no fixed frame rate is promised.
- There is no collision detection. Moving inside the sphere retains the existing immediate-hit behavior, and magenta silhouette pixels can still indicate unresolved step-limit rays.
- The scene still uses flat colors. Surface normals and basic lighting remain the suggested next rendering milestone, followed by additional geometry and parallel pixel rendering.

## 2026-10-26 — Milestone 4: surface normals and diffuse lighting

### Implemented and decisions

- Added one world-space directional light and diffuse shading to the sphere. The unit light vector points from the surface toward the light, using the normalized direction `[-0.6, 0.8, -0.5]`.
- Estimated outward normals with six central-difference samples of the scene distance function, rather than hard-coding sphere normals. The sampling step is configurable and defaults to 0.001 world units. A zero gradient receives ambient fill only.
- Used Lambert-style diffuse lighting by default: `diffuse = clamp(dot(n,l), 0, 1)`, then `brightness = 0.08 + 0.92 * diffuse`. The small constant ambient term approximates surrounding illumination; it does not simulate light bounces.
- Implemented the proposed alternative `clamp(dot(n,l)*0.5+0.5, 0, 1)` as an optional half-Lambert-style remap. Both models use the same light, base color, and ambient fill, making their angular response directly comparable. The alternative does not add the optional square used in some variants.
- Chose Lambert-style shading as the default because surfaces facing away receive no direct light. The ambient fill stays independently adjustable, and future shadow visibility can affect the direct term separately.
- Calculated lighting in linear RGB and encoded the result to sRGB for display. The base color is decoded once per image; out-of-range output channels are clamped. No HDR/tone mapping was added.
- Kept all shading in a separate lighting namespace. The marcher still returns hit information independently of lighting, camera, and display. An explicit color function still overrides default shading.
- Added `--lighting lambert`, `--lighting half-lambert`, and `--lighting flat` for both the interactive viewer and PNG exports. Flat mode preserves the previous geometry-only appearance. Lighting selection is at launch, not a live toggle.

### Verification

- `clojure -M:test`: 14 tests, 10,307 assertions, zero failures/errors. Added checks against analytic sphere normals and a plane normal, angular response at front/side/back orientations, ambient-only fill, undefined normals, light direction/normalization, sRGB round trips, lit pixels, and lighting-option validation.
- Retained the analytic ray–sphere image tests with an explicit flat-color function so geometry checks remain independent of shading.
- Exported and visually inspected the lit sphere and a side-by-side comparison of Lambert and the proposed remap. The comparison uses identical scene/light/fill settings and a narrower 35-degree camera view to show the shading clearly.
- Confirmed the flat-mode PNG checksum exactly matches the previous MVP output.
- Verified the actual desktop viewer with lighting: mouse dragging changed the image, movement and vertical controls worked, reset restored the view, full-resolution rendering resumed after movement, and closing the viewer stopped its renderer thread. Image calculations remained outside Swing's event dispatch thread.

### Limitations and next steps

- No cast shadows, specular highlights, reflections, distance falloff, multiple lights, or simulated indirect illumination yet. The current directional light has constant direction and strength everywhere.
- Normal estimation adds six distance samples per shaded hit. Small geometry, fine fractal detail, and non-smooth joins may need different sampling steps or normal methods.
- Existing step-limit markers remain magenta and can appear along grazing silhouettes. Starting inside geometry retains the marcher's existing immediate-hit policy.
- Next suggested lighting milestone: shadow rays, then a floor or second object so cast shadows can be evaluated. Material and light settings can evolve independently of the single-ray marcher.

## 2026-10-26 — Valve half-Lambert lighting option

- Added `--lighting valve-half-lambert` alongside `lambert`, `half-lambert`, and `flat` for the interactive viewer and PNG output.
- Implemented `wrapped = clamp(dot(n,l)*0.5+0.5, 0, 1)` followed by `diffuse = wrapped*wrapped`. The ambient fill, diffuse strength, light, and base color use the same settings as the other lit modes. Standard Lambert remains the default.
- Verification: 14 tests, 10,313 assertions, zero failures/errors. Checked Valve's angular response at five orientations and CLI acceptance of the new option. Successfully exported and visually inspected a PNG using the new command-line mode.
- Existing lighting limitations remain: no cast shadows or ambient occlusion yet. The new mode changes only the diffuse angular response.

## 2026-10-26 — Milestone 5: sphere-and-cube scene and custom scene files

### Implemented and decisions

- Updated the built-in scene to a radius-2 sphere at `[10 0 -2]` and a side-length-3 cube at `[12 0 2]`. Coordinates use `[X Y Z]` with Y as height; the requested zero-height positions were interpreted accordingly.
- Added exact signed-distance constructors for axis-aligned boxes and cubes. Boxes accept three half-sizes; cubes accept a full side length. Both integrate with scene union, normal estimation, and existing lighting.
- Added `--scene FILE.clj`. A scene file returns a map containing a distance function and optional camera, lighting, and marching settings. Ordinary Clojure functions allow new geometry without changing the renderer or building a separate expression parser.
- Added validation for configuration keys, camera points, marching limits, and lighting settings. Scene files are executable local Clojure; distance functions are expected to be pure and provide safe distance values.
- Scene cameras specify `:position`, `:look-at`, and vertical `:fov`. The viewer starts from that camera, and R returns to that scene's starting pose. PNG export uses the same initial camera. Camera coordinates written as integers are supported in the status display.
- A command-line lighting option overrides only the selected scene's diffuse model, preserving its light direction, ambient fill, and base color. Without that option, the scene's lighting model is respected.
- Added `scenes/example.clj` with the two objects, a different camera, and blue base color, plus a complete custom-scene tutorial covering axes, geometry, camera, lighting, marching limits, and an example custom floor distance function.

### Verification

- `clojure -M:test`: 19 tests, 10,353 assertions, zero failures/errors. Added known box face/edge/corner distances, default-layout ray hits, scene-file loading, custom camera conversion, configuration validation, and CLI override checks.
- Made the original sphere-only geometry and normal tests use an explicit fixture so future changes to the default demonstration scene cannot silently change those tests' meaning.
- Exported and visually inspected the default sphere/cube scene and the supplied custom scene with a CLI Valve half-Lambert override.
- Extracted the complete tutorial scene into a file and rendered it through `--scene`; its PNG checksum exactly matched the equivalent built-in scene render.
- Verified the custom scene in the desktop viewer: starting camera `(0, 1, -4)`, movement, mouse look, reset to the scene camera, focus/escape stopping, full-resolution completion, and renderer shutdown after closing. Corrected status formatting for integer camera coordinates during this check.

### Limitations and next steps

- Custom scene files are loaded at startup; editing a file requires restarting the viewer. Geometry uses a shared scene-wide base color, with no per-object materials or built-in object rotations yet.
- World +Y remains up, with initial camera tilt limited to ±89 degrees. Defining new distance functions does not yet add curved-ray or non-Euclidean traversal.
- Cast shadows and ambient occlusion remain future work. The sphere and cube provide a more useful starting scene for developing those effects.

## 2026-10-26 — Milestone 6: Menger sponge fractal scene

### Implemented and decisions

- Added `scenes/menger.clj`, a turquoise Menger sponge centered at `[10 0 0]`, with side length 6 and three subdivision levels. The camera views three faces from above and to one side; an angled directional light gives the visible faces contrasting brightness.
- Added a reusable `fractals/menger-sponge` constructor accepting center, full side length, and depth 0–6. Depth 0 is a solid cube. Higher levels repeatedly remove the central cross of each cell.
- Evaluated repeated cell coordinates and tunnel cuts inside a signed distance bound instead of generating all `20^depth` remaining cubes. World-space scaling preserves distance units, so the existing marcher and normal estimation can use the function unchanged.
- Tuned this scene's hit tolerance and normal sampling to 0.0005, with a 512-step marching budget. Other scenes and the default scene retain their settings.
- Added launch instructions and editable depth guidance to the README/custom-scene tutorial, and saved a 960 × 720 rendered preview at `docs/images/menger.png`.

### Verification

- `clojure -M:test`: 22 tests, 10,642 assertions, zero failures/errors. Compared solid membership and conservative exterior distances against an independently constructed union of recursive leaf cubes at depths 0–2. Also checked a ray passing through the central tunnel, a ray hitting retained material, world scaling/translation, validation, and scene loading.
- Rendered and visually inspected the fractal while tuning its light and framing, then exported the final 960 × 720 preview through `--scene scenes/menger.clj`.
- Verified the desktop viewer loaded the scene's starting camera, rendered outside Swing's event dispatch thread, and produced both preview and full-resolution images.

### Limitations and next steps

- This is a finite approximation of the Menger sponge, using a conservative distance bound rather than an exact Euclidean distance everywhere. Increasing depth raises CPU cost and needs sufficient image resolution and suitably small sampling tolerances.
- The scene uses existing diffuse lighting only; no cast shadows or ambient occlusion were introduced. Those effects would improve the depth cues inside the tunnels.
- The new scene is selected with `--scene`; the sphere-and-cube scene remains the built-in default. Camera controls work through the existing viewer.

## 2026-10-26 — PNG export progress

- Added an optional renderer callback receiving completed image rows and total rows, starting at zero and reporting each finished row. Rendering remains independent of console output and Swing.
- PNG exports now show an ASCII progress bar with percentage and row counts. Terminal updates reuse one line and occur at percentage changes. Without an attached Java console, updates are limited to approximately ten-percent milestones on separate lines.
- Progress is written to stderr, preserving the final saved-file message on stdout. Once rows are complete, a `Writing PNG...` message covers PNG encoding and saving. Terminal status is cleared on completion or failure through cleanup in `finally`.
- The progress callback is supplied only for PNG exports; interactive previews and camera controls retain their existing behavior.
- Verification: 26 tests, 10,657 assertions, zero failures/errors. Added checks for accurate row counts, cancellation without a false completion event, identical image pixels with/without reporting, bounded non-interactive output, and terminal-line cleanup.
- Ran an actual terminal export and redirected 960 × 720 fractal exports. Verified zero-to-100-percent row reporting, the writing phase, and clean stdout. A temporary depth-3 scene copy produced a PNG checksum identical to the original depth-3 preview; the current depth-5 scene setting was preserved.
- Limitation: percentage describes completed rows rather than predicted time; rows crossing detailed geometry can take longer. PNG encoding has a status message rather than its own measured percentage. Progress is currently synchronous with the sequential renderer.

## 2026-10-26 — Milestone 7: reference-inspired Mandelbulb scene

### Implemented and decisions

- Identified the supplied image as appearing to show a Mandelbulb, likely spherical power 8. The exact original parameters cannot be recovered from the screenshot alone.
- Added `fractals/mandelbulb`, with configurable center, scale, power, escape iterations, bailout radius, and step factor. It uses spherical-power escape iteration and the derivative-based exterior distance estimate described in the linked references.
- Handled zero-radius orbits without division by zero. Far outside the fractal, used its radius-2 bounding sphere to avoid excessively long logarithmic steps. Near the surface the estimator remains approximate, not a proven conservative bound.
- Non-escaping points return zero rather than a negative signed interior distance. This supports the existing hit test but is not a general signed-distance primitive for subtraction/intersection operations.
- Added `scenes/mandelbulb.clj` using power 8, 14 escape iterations, scale 2.5, and a 0.7 step factor. A local Y reflection places the spire above the layered base, and the model is translated to world `[10 0 0]`.
- Used a pale-gray material and directional light. Tuned hit tolerance to 0.0015 and normal sampling to 0.008 to reduce speckled shading while retaining the larger surface ridges. The scene has a 768-step marching budget and its own camera.
- Added a rendered 800 × 800 preview and a Mandelbulb guide covering identification, tuning, formula references, and estimator limitations. The default and Menger scenes remain separate.

### Verification

- `clojure -M:test`: 29 tests, 10,743 assertions, zero failures/errors. Added closed-form checks of positive polar-axis escape orbits for powers 2, 4, 8, and 16; bounded center/negative-pole cases; finite nonnegative samples; reflection symmetry; world scaling/translation; ray hits/misses; option validation; and scene loading.
- Rendered and visually compared multiple previews while tuning orientation and normal sampling, then exported and inspected the final 800 × 800 image. The PNG progress bar reached completion and its writing phase successfully.
- Verified that the desktop viewer loaded the configured camera, rendered outside Swing's event dispatch thread, and produced preview and full-resolution frames after mouse-look movement.

### Limitations and next steps

- This reproduces the reference's apparent fractal family and overall orientation, not its exact parameters or image. The reference's gray ground, soft contact shadows, and ambient occlusion are not reproduced by the current diffuse-only scene.
- Finite iterations, hit tolerance, and normal smoothing limit visible detail. Higher resolution and tighter settings increase CPU cost; no antialiasing or exact interior distance was added.
- Shadows and ambient occlusion remain useful next improvements, particularly for the fractal's recesses and contact with future supporting geometry.

## Future entry checklist

For each session, record the date, objective, completed changes, reasons for decisions, checks and results, unresolved issues, and next steps. Focus on rendering development; omit repository housekeeping and editorial changes. Include measured performance only after actual runs, with settings and machine context.
