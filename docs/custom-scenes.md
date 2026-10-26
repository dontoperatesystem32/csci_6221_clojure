# Create a custom scene

Scenes are ordinary Clojure files. Their last expression returns a map describing the geometry and optional camera, lighting, and marching settings. Load your file with `--scene`; there is no need to edit the renderer or the default scene.

## 1. Understand the axes

Every coordinate uses `[X Y Z]`:

- **X:** forward/backward for the starting camera.
- **Y:** height; positive is up.
- **Z:** sideways; positive is right for the starting camera looking along +X.

The horizontal plane is XZ. Thus the requested sphere and cube, both at height zero, are represented as `[10 0 -2]` and `[12 0 2]`.

## 2. Create a file

Create `scenes/my-scene.clj` with this content, or copy the provided `scenes/example.clj` and edit it. Use a distinct namespace name for each scene.

```clojure
(ns scenes.my-scene
  (:require [ray-marcher.scene :as scene]))

;; The final expression is the scene description.
{:scene-distance
 (scene/union
   (scene/sphere [10 0 -2] 2)
   (scene/cube [12 0 2] 3))

 :camera {:position [0 0 0]
          :look-at [11 0 0]
          :fov 60}

 :lighting {:toward-light [-0.6 0.8 -0.5]
            :ambient 0.08
            :diffuse-strength 0.92
            :diffuse-model :lambert
            :base-color 0xF5AC48}

 :settings {:epsilon 0.001
            :max-distance 100.0
            :max-steps 128}}
```

Only `:scene-distance` is required. Omitted camera, lighting, and marching values use the application's normal defaults. Unknown configuration keys are rejected to help catch spelling mistakes.

Scene files execute as Clojure code when loaded, so use your own files or ones you trust. Keep distance functions pure: the same point should return the same distance without changing shared state.

## 3. Run it

From the project root:

```sh
clojure -M:run --scene scenes/my-scene.clj
```

Try the supplied example, which uses a different starting camera and a blue base color:

```sh
clojure -M:run --scene scenes/example.clj
```

Paths are relative to the directory from which you launch the application; absolute paths also work. Restart the viewer to load file edits; hot reloading is not implemented.

To export the scene as an image:

```sh
clojure -M:run --scene scenes/my-scene.clj --width 1280 --height 720 --output my-scene.png
```

The output directory must already exist. Without `--scene`, the application uses the built-in sphere-and-cube scene.

## 4. Add and resize objects

Each constructor returns a signed-distance function:

```clojure
(scene/sphere [10 0 -2] 2)       ; center, radius
(scene/cube [12 0 2] 3)          ; center, FULL side length
(scene/box [15 1 0] [1 2 0.5])   ; center, HALF-size on each axis
```

The last box is 2 units wide along X, 4 units tall along Y, and 1 unit wide along Z. Sizes must be positive. Cubes and boxes are axis-aligned; object rotation is not a built-in setting yet.

Add objects by passing more functions to `scene/union`. It evaluates all objects and returns the smallest signed distance. Overlapping objects become one solid region; this is not a list of separately colored materials.

```clojure
:scene-distance
(scene/union
  (scene/sphere [10 0 -2] 2)
  (scene/cube [12 0 2] 3)
  (scene/sphere [16 3 0] 1))
```

The sphere at `[16 3 0]` is 3 units above the horizontal plane.

## 5. Change the camera

- `:position`: where the camera starts.
- `:look-at`: a world-space point that sets its initial viewing direction.
- `:fov`: vertical field of view in degrees; smaller values give a narrower, more zoomed-in view.

The camera always uses world +Y as up. Its initial tilt must stay within ±89 degrees, and `:position` and `:look-at` must differ. The target sets orientation; it does not lock the camera into an orbit.

The viewer starts with these settings. WASD and mouse controls work as usual, and **R resets to this scene's starting camera**. PNG export uses the same starting view. Image dimensions remain command-line options.

## 6. Change lighting and marching settings

`:toward-light` points from the surface toward a directional light and is normalized automatically. `:base-color` is a hexadecimal RGB color, applied to all objects in this version. `:diffuse-model` accepts `:lambert`, `:half-lambert`, or `:valve-half-lambert`.

A command-line lighting choice overrides only the model, preserving the scene's other lighting values:

```sh
clojure -M:run --scene scenes/my-scene.clj --lighting valve-half-lambert
clojure -M:run --scene scenes/my-scene.clj --lighting flat
```

Flat mode uses the fixed debug silhouette colors. Without `--lighting`, the scene's model is respected. See [lighting.md](lighting.md) for normal sampling, light strength, ambient fill, and color handling.

Increase `:max-steps` if magenta pixels indicate unresolved rays. Increase `:max-distance` when moving geometry farther away. `:epsilon` controls how close a ray must get to count as a hit; it is a positive distance in world units. More steps and tighter tolerances can cost more CPU time.

## 7. Define a new shape with code

You can provide your own signed-distance functions rather than being limited to the constructors. For example, a horizontal floor at Y = -2 has signed distance `y + 2`:

```clojure
(defn floor-distance [[_ y _]]
  (+ y 2.0))

;; Use in the scene map:
:scene-distance
(scene/union
  (scene/sphere [10 0 -2] 2)
  (scene/cube [12 0 2] 3)
  floor-distance)
```

Place the `defn` before the final scene map. The floor extends infinitely in X and Z; points below it are inside its solid half-space. There are still no cast shadows or ambient occlusion, so adding a floor alone will not create contact shadows.

The marcher needs signed distances or safe distance estimates: positive outside, zero on the surface, negative inside. An arbitrary formula whose zero describes a surface is not automatically safe for marching. Fractals and other advanced geometry will need appropriate distance estimates; curved light paths and non-Euclidean spaces require additional traversal work beyond defining a new scene file.
