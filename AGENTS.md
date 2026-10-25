# Working on this project

- Keep `docs/development-diary.md` current after each meaningful development session. Use America/New_York dates and record what changed, why, verification results, limitations, and next steps. Do not rewrite earlier entries to imply a later feature existed sooner.
- The diary is shared with William and is source material for the university report. Distinguish implemented behavior from proposed work, and record unresolved decisions clearly.
- Write diary entries as a technical progress record: describe what changed, decisions, and verification rather than emphasizing implementation assistance or narrating who requested each change.
- Keep image generation separate from Swing display. Perform expensive image calculations outside Swing's event dispatch thread; update Swing components on that thread.
- Keep the rendering logic in Clojure. Use Java libraries for supporting facilities as permitted by the project requirements.
- Language-evaluation materials live in the sibling `../clojure/` folder. Keep them separate from the renderer and preserve the existing presentations and examples.
