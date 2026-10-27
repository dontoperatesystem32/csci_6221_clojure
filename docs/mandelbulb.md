# Mandelbulb reference scene

The supplied reference appears to be a **Mandelbulb**, likely the common spherical power-8 form. The tiered, cauliflower-like lobes and repeating ridges support that identification. The exact power, camera, iteration count, and original renderer settings cannot be established from a single image.

## Run and export

```sh
clojure -M:run --scene scenes/mandelbulb.clj --width 800 --height 800
clojure -M:run --scene scenes/mandelbulb.clj --width 1200 --height 1200 --output mandelbulb.png
```

PNG export uses the existing progress bar. The viewer supports the same mouse/WASD controls and scene-camera reset as the other scenes. The built-in default and Menger scene are unchanged.

## Shape and orientation

The model uses the recurrence `z <- spherical-power(z, power) + c`. It multiplies the polar and azimuthal angles and raises the radius to the specified power, instead of using complex-number multiplication in a plane. Points whose orbits remain bounded form the approximated fractal.

The reusable constructor is:

```clojure
(fractals/mandelbulb [0 0 0] 2.5
  {:power 8 :iterations 14 :bailout 4.0 :step-factor 0.7})
```

- The first argument is its center; `2.5` is a world-space scale, not an exact radius.
- `:power` changes the geometry. Supported integer powers are 2–16; power 8 is the reference-inspired preset.
- `:iterations` limits the escape calculation per distance query, from 1–32. It is separate from the marcher's `:max-steps` limit.
- `:bailout` is the orbit radius at which a point is considered escaped, from 2–8.
- `:step-factor` multiplies the estimated step by a value in `(0,1]`. Smaller values take shorter steps and often need more marching iterations.

The constructor uses +Y as the polar axis. The supplied scene builds the model at a local origin, reflects local Y to place its spire above its layered base, and translates it to world `[10 0 0]`. These transformations are in the scene's distance function, demonstrating that custom scenes can transform geometry without modifying the renderer.

## Distance estimate and shading

The escaped orbit maintains a derivative estimate, updated as `dr <- power * r^(power-1) * dr + 1`. Its exterior distance estimate is `0.5 * log(r) * r / dr`, rescaled to world units and multiplied by the step factor. Far from the model, a radius-2 bounding sphere gives a safer exterior step than the logarithmic estimate alone.

This is an approximate exterior estimator, **not an exact signed distance or a proven conservative bound** near the fractal. Non-escaping points return zero. It works with the current hit test and union, but does not provide the negative interior values needed for general signed subtraction/intersection operations. A finite iteration budget can classify some eventually escaping points as solid; finer images may need additional iterations and a smaller hit tolerance.

The scene uses a hit tolerance of 0.0015 world units and a normal-sampling step of 0.008. Wider normal sampling smooths tiny variations that caused speckled lighting in early previews; it also softens very fine details. Increase render resolution before reducing those values substantially. There is no antialiasing yet.

The material is pale gray with directional diffuse lighting. The reference's ground plane, soft contact shadows, and ambient occlusion are not implemented in this scene. The geometry is reference-inspired rather than a pixel-for-pixel recreation of its shading.

## References

- [McGraw and Herring, Shape Modeling with Fractals, section 2.1](https://web.ics.purdue.edu/~tmcgraw/papers/mcgraw_fractal_2014.pdf): spherical-power Mandelbulb and distance-estimation equations.
- [Mandelbulb course notes](https://www.prinmath.com/csci5229/Sp12/handouts/mandelbulb.pdf): escape iteration and distance estimation.
