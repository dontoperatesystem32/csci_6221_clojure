# Basic diffuse lighting

The renderer shades the sphere with one white directional light and a small ambient fill. The light is fixed in world space, so moving the camera does not move it. Shadows, reflections, and light bouncing are not implemented yet.

## The two directions

`n` is the unit outward surface normal at the hit point. `l` is the unit direction **from that point toward the light**. It is a direction vector, not a surface normal of the light. Using the direction in which light travels would reverse the sign.

For a directional light, `l` is constant everywhere, like the approximately parallel rays of sunlight. For a future point light it would be `normalize(light-position - hit-position)`, with distance falloff handled separately.

The dot product of these unit vectors equals the cosine of their angle:

```text
cosine = dot(n, l)
```

It is 1 when the surface faces the light, 0 when the surface is edge-on, and -1 when it faces directly away.

## Default: Lambert-style diffuse plus ambient

```text
diffuse = clamp(dot(n, l), 0, 1)
brightness = 0.08 + 0.92 * diffuse
linear-color = linear-base-color * brightness
```

The diffuse term represents the angular response of a matte surface: incoming light is spread over more surface area as the angle becomes more oblique. Back-facing surfaces receive no direct contribution from this light. The 0.08 ambient term is a deliberate approximation to surrounding illumination; it is not simulated indirect light.

This keeps direct illumination and the artistic dark-side fill independently adjustable. The model is a useful starting point for future shadows because visibility can multiply the direct term while the ambient term remains separate. It is not a complete physically based renderer; light intensity, material reflectance, and the Lambertian normalization are simplified into the chosen color and strength.

## Alternative: the proposed half-Lambert-style remap

```text
diffuse = clamp(dot(n, l) * 0.5 + 0.5, 0, 1)
```

In `dot(n, l)`, the second symbol is a lowercase L denoting the light vector. A literal number `1` is not that direction.

| Dot product | Default diffuse | Proposed remap |
|---|---:|---:|
| 1 | 1 | 1 |
| 0.5 | 0.5 | 0.75 |
| 0 | 0 | 0.5 |
| -0.5 | 0 | 0.25 |
| -1 | 0 | 0 |

These values exclude ambient fill and display encoding. The remap wraps illumination around the object, giving softer, brighter dark regions. That can be a useful style choice, but a surface facing away from the light can still receive this term. The implemented alternative is exactly the supplied linear remap, without an additional square sometimes used in half-Lambert variants.

Compare the modes in the window or PNG exports:

```sh
clojure -M:run
clojure -M:run --lighting half-lambert
clojure -M:run --lighting valve-half-lambert
clojure -M:run --lighting flat
clojure -M:run --lighting half-lambert --output comparison.png
```

The `valve-half-lambert` option uses Valve's squared variant:

```text
wrapped = clamp(dot(n, l) * 0.5 + 0.5, 0, 1)
diffuse = wrapped * wrapped
```

For an edge-on surface this produces 0.25 instead of the unsquared variant's 0.5, before ambient fill. See [Valve's rendering paper, section 8.4.4](https://cdn.fastly.steamstatic.com/apps/valve/2006/SIGGRAPH06_Course_ShadingInValvesSourceEngine.pdf).

All three lit modes use the same light, base color, ambient fill, and diffuse strength. The setting is selected at launch; there is no live lighting toggle yet.

## Normals from the distance field

For a sphere we could use `normalize(point - center)`, but the renderer instead samples the scene distance function so other shapes can use the same lighting code.

At hit point `p`, sample slightly forward and backward on each world axis:

```text
gx = distance(p + [h,0,0]) - distance(p - [h,0,0])
gy = distance(p + [0,h,0]) - distance(p - [0,h,0])
gz = distance(p + [0,0,h]) - distance(p - [0,0,h])
n = normalize([gx, gy, gz])
```

This estimates the gradient: the direction in which signed distance increases fastest. For an ordinary signed distance field that points outward. Division by `2*h` cancels when the vector is normalized.

The default `h` is 0.001 world units. It is a separate setting from the marcher's hit tolerance. It may need tuning for much smaller geometry, fine fractal detail, or different world scales. Normals are not uniquely defined at some corners or joins. If the sampled gradient is zero, the implementation uses ambient fill only; this also avoids a failure when the camera reaches the sphere's center under the existing inside-hit policy.

## Color and configuration

Lighting is calculated in linear RGB, then converted back to sRGB for display. Scaling encoded display bytes directly would make intermediate illumination levels too dark. The base color is decoded once per image; final channels are clamped to the displayable range. There is no HDR/tone-mapping pipeline yet.

`ray-marcher.lighting/default-settings` contains the light direction, base color, ambient amount, diffuse strength, normal-sampling step, and model. Pass overrides through `render-image`'s `:lighting` map. An explicit `:color-fn` still overrides default lighting, preserving silhouette rendering for geometry checks.

The lighting module has no camera or Swing dependencies. The marcher still only finds hit information. Each shaded hit adds six scene-distance evaluations for its normal; it does not cast shadow rays.

## References

- [Physically Based Rendering: Diffuse Reflection](https://pbr-book.org/4ed/Reflection_Models/Diffuse_Reflection): matte reflection and the cosine factor in light transport.
- [W3C sample color conversions](https://www.w3.org/TR/css-color-4/#color-conversion-code): linear-light and sRGB conversion formulas.
