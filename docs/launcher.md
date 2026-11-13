# Graphical launcher

Start from the project root:

```sh
clojure -M:run
# Or explicitly:
clojure -M:run --launcher
```

The launcher uses the same Swing toolkit as the viewer and introduces no dependencies. It opens a settings menu first. Rendering flags continue to launch directly; use `clojure -M:run --viewer` to skip the menu with default settings. `--launcher` must be used on its own. Headless rendering remains available through `--output`.

## Choose a scene

The dropdown lists the built-in sphere/cube scene and the supplied example, Menger, Mandelbulb, blending, and AO scenes found in the project's `scenes` folder. **Browse .clj…** loads another local scene. Paths are relative to your launch directory unless absolute. Scene files execute Clojure code, so open trusted files.

Selection evaluates the scene once on a background thread and fills the menu with its actual camera, lighting, AO, blending, and marching values. A failed load shows an error and restores the previous selection/settings. Switching scenes resets image size and thread count to 800 × 450 and 1. **Reset to scene defaults** restores the loaded definition without re-evaluating the file; to pick up file edits, select another scene and return, or browse to the file again.

## Controls

| Section | Controls |
|---|---|
| Scene | Supplied scene dropdown; custom file browser |
| Rendering | Width, height, thread count |
| Effects | Lambert / Half-Lambert / Valve Half-Lambert / Flat; AO off / normal / only; hard / polynomial / exponential blending; blend width |
| Advanced → Camera | Initial position, look-at point, vertical field of view |
| Advanced → Lighting | Base-color chooser, direction toward light, ambient brightness, diffuse strength, normal sampling distance |
| Advanced → AO tuning | Sample count, radius, bias, strength |
| Advanced → Marching | Maximum ray steps, maximum travel distance, hit tolerance |

Expand **Advanced settings** and scroll to see its fields. Coordinates accept three numbers separated by commas or spaces, in X, Y, Z order. Distances use world units. Exact numeric values are editable; the first version uses numeric fields rather than sliders with arbitrary ranges.

AO controls are disabled under Flat shading, and AO tuning is disabled while AO is off. Blend width is disabled for hard union. Both blending controls are disabled for scenes defined with an opaque `:scene-distance`, including the current fractal presets: they do not expose an object group for reconfiguration. Disabled modes do not erase the previous values.

Geometry editing, fractal parameters, saved launcher presets, and a copy-command action are not included. Those remain scene-file edits or possible future features. Settings are not persisted between launches.

## Open a viewer

**Open viewer** validates and snapshots the current settings, then opens the existing fly-camera viewer. Its camera reset returns to the position/orientation set in the menu. Menu edits affect the next viewer/export, not windows already open. Closing a viewer leaves the launcher available; closing the launcher leaves other viewers open.

## Export a PNG

**Render PNG…** opens a save dialog, adds `.png` if necessary, and asks before replacing an existing file. The destination directory must exist. The menu remains responsive while rendering and writing run in the background. Progress counts completed rows across all rendering tasks, including when more than one thread is selected.

**Cancel export** stops at row boundaries and prevents saving an incomplete image. Once PNG encoding starts, cancellation is disabled; wait for the saved/error message. A cancelled render does not replace an existing output file. Closing the launcher also requests cancellation of its export; if PNG writing has already begun it may finish. One export or scene load runs at a time. Settings are captured at the start of an export, so edits made while it runs affect later jobs.

The percentage describes completed rows, not remaining time. Thread count selects parallel row tasks; no more tasks are created than there are rows. Larger images, fractals, AO, and high thread counts can increase memory/CPU use. There is no automatic render-quality or memory-budget selector.

## CLI compatibility

These still bypass the launcher:

```sh
clojure -M:run --viewer
clojure -M:run --scene scenes/ao-demo.clj --ao only --threads 3
clojure -M:run --scene scenes/blend-demo.clj --union polynomial --blend 1 --width 1280 --height 720 --threads 3 --output blend.png
clojure -M:run --help
```

## Code boundaries

- `launcher_form.clj`: field definitions, layout, value parsing, and dependent-control states.
- `launcher.clj`: scene selection, dialogs, menu actions, and background-job coordination; Swing changes run on the EDT.
- `configuration.clj`: effective scene defaults, validation, and shared CLI/menu preparation. It never opens windows or exits the JVM.
- `application.clj`: reusable viewer/export actions. Export returns a file or cancellation result, keeping the launcher alive.
- `render.clj`: rendering, integer row partitioning, serialized completed-row reporting, and cancellation. No Swing dependency.
- `core.clj`: CLI parsing and routing to menu, viewer, or export; process exit is limited to CLI handling.

Tests verify scene defaults and edits, invalid input, field dependencies, pixel equality across uneven row splits and thread counts greater than image height, ordered row progress, cancellation, CLI options, and reusable file export. Native desktop checks also cover real scene selection, reset, errors, export/progress/cancellation, and opening/closing the viewer.
