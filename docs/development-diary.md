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

## Future entry checklist

For each session, record the date, objective, completed changes, reasons for decisions, checks and results, unresolved issues, and next steps. Focus on rendering development; omit repository housekeeping and editorial changes. Include measured performance only after actual runs, with settings and machine context.
