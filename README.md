# CSCI 6221 — Clojure Ray Marcher

This repository contains the CPU ray-marcher final project. Language-evaluation slides, scripts, assignment materials, and examples are collected in the separate [Clojure folder](../clojure/README.md). The current MVP renders a sphere using CPU sphere tracing and displays the result in a desktop window or saves it as a PNG.

## Run the application

Install a JDK (17 or newer) and the Clojure CLI. From this repository's root:

```sh
clojure -M:run
```

The first run downloads Clojure and its dependencies. A window displays an orange sphere silhouette on a dark background. The camera is at `(0, 0, 0)`, looks along **+X**, and sees a sphere centered at `(10, 0, 0)` with radius `2`. The camera uses +Y as up and a 60° vertical field of view. This is a real 3D distance-field calculation with flat colors; lighting has not been added yet.

Image dimensions are configurable independently of the window size. The image scales to fit the window while keeping its aspect ratio; resizing the window does not change the render resolution.

```sh
clojure -M:run --width 1920 --height 1080
```

To save the same image without opening a window:

```sh
clojure -M:run --width 800 --height 450 --output preview.png
```

The destination directory must already exist. Use `clojure -M:run --help` for options. This milestone does not impose a practical image-size cap; very large dimensions can exhaust memory.

## Camera controls

Focus the viewer window to use the keyboard. Hold the left mouse button and drag over the image to look around; release it to stop looking. No system pointer capture is required.

| Input | Action |
|---|---|
| W / S | Forward / backward along the horizontal XZ plane |
| A / D | Strafe left / right along that plane |
| Shift / Control | Move up / down along world Y |
| Left-button mouse drag | Turn and tilt the camera |
| R | Reset the original camera position and orientation |
| Escape | Clear held movement and stop the current mouse drag |

Movement follows camera heading but stays horizontal even when looking up or down. Speed is 3 world units per second; diagonal movement has the same speed. Pitch is limited to ±89° to avoid flipping. Switching to another window clears held input. There is no collision detection: flying inside the sphere produces the marcher's existing immediate-hit behavior.

During movement the viewer requests previews with a maximum dimension of 240 pixels, scaled to fit the window. After 250 ms without camera changes it requests the configured full resolution. One background worker processes the latest pending view; previews may briefly lag the current camera, while obsolete full renders are cancelled. Actual refresh speed depends on scene cost and hardware; this is not a fixed-frame-rate renderer. The status line identifies preview/full resolution and the camera position used for the displayed image.

PNG export remains a single render from the original camera, without opening the interactive window.

## Project organization

- `src/ray_marcher/core.clj`: startup and command-line options.
- `src/ray_marcher/vector.clj`: basic 3D vector operations.
- `src/ray_marcher/scene.clj`: sphere distance function and scene union.
- `src/ray_marcher/march.clj`: independent single-ray marcher.
- `src/ray_marcher/camera.clj`: perspective camera and pixel ray directions.
- `src/ray_marcher/render.clj`: sequential image rendering and flat colors.
- `src/ray_marcher/image.clj`: PNG output.
- `src/ray_marcher/controls.clj`: camera movement and mouse-look calculations.
- `src/ray_marcher/worker.clj`: background rendering with one pending view.
- `src/ray_marcher/ui.clj`: interactive Swing viewer and input handling.
- `docs/ray-marcher-requirements.md`: rough project requirements.
- `docs/development-diary.md`: dated progress, decisions, checks, and next steps for Rufat and William.
- `../clojure/`: language-evaluation materials and the final presentation.

The renderer creates a completed image that the existing window displays. The window does not contain scene, camera, ray, or lighting logic. The interactive window opens immediately and renders in the background. Completed images are published on Swing's event dispatch thread; the worker never modifies an image after publishing it.

## Marcher behavior and extension points

`ray-marcher.march/raymarch` accepts a scene distance function, ray origin `ro`, normalized ray direction `rd`, and optional settings. It checks the distance at the current point before advancing. Defaults are a `0.001` surface tolerance, `100` world-unit travel limit, and `128` distance evaluations per ray. Distances and tolerance are in world units.

Results distinguish `:hit`, `:miss` (travel limit), and `:step-limit` (unresolved). Hits include the position; all results include distance traveled/bounded and number of evaluations. Starting inside an object counts as an immediate hit. The marcher expects valid settings and a unit direction. Scene functions must return exact signed distances or safe distance bounds; arbitrary implicit equations are not automatically valid inputs.

The renderer casts one ray through each pixel center and accounts for image aspect ratio. Orange means a hit, dark blue means a miss, and magenta means the step budget was exhausted. Near a silhouette, the finite surface tolerance can slightly expand the apparent shape. Rendering is sequential, with no antialiasing, shadows, or lighting yet. A sphere with flat color naturally appears as a circle.

`render-image` accepts an optional map with `:scene-distance`, `:camera`, `:settings`, and `:color-fn`. Scene construction and camera setup happen independently of the marcher. A future lighting function can use the hit position and scene distance function to estimate a normal and cast shadow rays. An optional `:cancelled?` predicate is checked between image rows; cancellation returns `nil`. The viewer uses this to abandon obsolete full-resolution frames. Pixel calculations remain sequential within each frame; background rendering does not yet use multiple CPU cores for a single image.

## Verification

```sh
clojure -M:test
```

Tests cover known sphere distances, a center hit at `(8, 0, 0)`, misses, travel and step limits, starting inside a sphere, camera directions, and image pixels checked against an independent analytic ray–sphere intersection calculation. Both portrait and landscape images and an off-center sphere are checked. Additional tests check movement speed, horizontal travel while tilted, diagonal normalization, pitch limits, cancellation, and replacement of pending views. Tests require no graphical desktop.
