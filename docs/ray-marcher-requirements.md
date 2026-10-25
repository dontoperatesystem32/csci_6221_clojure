# Ray Marcher Project Requirements

This document records the project specifications from slide 15, “Project - Ray Marcher,” and its speaker notes in the [final presentation](../../clojure/presentation/Clojure.pptx).

## Objective

Build a ray marcher in Clojure that renders shapes and scenes defined by mathematical functions. Examples mentioned in the proposal include spheres, planes, and gyroids.

## Features

- Render functions supplied to the ray marcher.
- Support optimized parallel rendering using multiple threads.

## Constraints and Technology

- Use Clojure for the main implementation.
- Use only Clojure and Java libraries, with Java libraries supporting the Clojure code.
- Run rendering on the CPU without using a GPU or shaders.
- Use concurrency to help address rendering performance.

## Visual Goal

The speaker notes state a goal of rendering scenes with complexity similar to the “Happy Jumping” scene shown in the presentation. No exact scene requirements or performance targets are specified.

## Possible Applications

The proposal identifies manufacturing, medical imaging, physics, and other fields that use functions to represent regions of space as possible applications.

## Reference Included in the Proposal

- [Inigo Quilez: Raymarching Distance Fields](https://iquilezles.org/articles/raymarchingdf/)
