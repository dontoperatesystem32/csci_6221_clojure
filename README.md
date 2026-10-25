# CSCI 6221 — Clojure Ray Marcher

This repository contains the CPU ray-marcher final project. Language-evaluation slides, scripts, assignment materials, and examples are collected in the separate [Clojure folder](../clojure/README.md). The current MVP renders a sphere using CPU sphere tracing and displays the result in a desktop window or saves it as a PNG.

## Run the application

Install a JDK (17 or newer) and the Clojure CLI. From this repository's root:

```sh
clojure -M:run
```

The first run downloads Clojure and its dependencies. A window displays an orange sphere silhouette on a dark background. The camera is at `(0, 0, 0)`, looks along **+X**, and sees a sphere centered at `(10, 0, 0)` with radius `2`. The camera uses +Y as up and a 60° vertical field of view. This is a real 3D distance-field calculation with flat colors; lighting has not been added yet.

Image dimensions are configurable independently of the window size. Larger images can be scrolled; resizing the window does not change the image resolution.

```sh
clojure -M:run --width 1920 --height 1080
```

To save the same image without opening a window:

```sh
clojure -M:run --width 800 --height 450 --output preview.png
```

The destination directory must already exist. Use `clojure -M:run --help` for options. This milestone does not impose a practical image-size cap; very large dimensions can exhaust memory.

## Project organization

- `src/ray_marcher/core.clj`: startup and command-line options.
- `src/ray_marcher/vector.clj`: basic 3D vector operations.
- `src/ray_marcher/scene.clj`: sphere distance function and scene union.
- `src/ray_marcher/march.clj`: independent single-ray marcher.
- `src/ray_marcher/camera.clj`: perspective camera and pixel ray directions.
- `src/ray_marcher/render.clj`: sequential image rendering and flat colors.
- `src/ray_marcher/image.clj`: PNG output.
- `src/ray_marcher/ui.clj`: Swing window displaying a completed image.
- `docs/ray-marcher-requirements.md`: rough project requirements.
- `docs/development-diary.md`: dated progress, decisions, checks, and next steps for Rufat and William.
- `../clojure/`: language-evaluation materials and the final presentation.

The renderer creates a completed image that the existing window displays. The window does not contain scene, camera, ray, or lighting logic. Image generation currently runs before the window opens; future interactive rendering will require background work and publishing completed images to the UI.

## Marcher behavior and extension points

`ray-marcher.march/raymarch` accepts a scene distance function, ray origin `ro`, normalized ray direction `rd`, and optional settings. It checks the distance at the current point before advancing. Defaults are a `0.001` surface tolerance, `100` world-unit travel limit, and `128` distance evaluations per ray. Distances and tolerance are in world units.

Results distinguish `:hit`, `:miss` (travel limit), and `:step-limit` (unresolved). Hits include the position; all results include distance traveled/bounded and number of evaluations. Starting inside an object counts as an immediate hit. The marcher expects valid settings and a unit direction. Scene functions must return exact signed distances or safe distance bounds; arbitrary implicit equations are not automatically valid inputs.

The renderer casts one ray through each pixel center and accounts for image aspect ratio. Orange means a hit, dark blue means a miss, and magenta means the step budget was exhausted. Near a silhouette, the finite surface tolerance can slightly expand the apparent shape. Rendering is sequential, with no antialiasing, shadows, or lighting yet. A sphere with flat color naturally appears as a circle.

`render-image` accepts an optional map with `:scene-distance`, `:camera`, `:settings`, and `:color-fn`. Scene construction and camera setup happen independently of the marcher. A future lighting function can use the hit position and scene distance function to estimate a normal and cast shadow rays. Threading and interactive controls are future work, not requirements of this MVP.

## Verification

```sh
clojure -M:test
```

Tests cover known sphere distances, a center hit at `(8, 0, 0)`, misses, travel and step limits, starting inside a sphere, camera directions, and image pixels checked against an independent analytic ray–sphere intersection calculation. Both portrait and landscape images and an off-center sphere are checked. Tests require no graphical desktop.
