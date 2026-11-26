# Coach food matching: counting kinds of a food without wrong matches

26 Nov 2026. Temporary design note: see «When this is done» at the end.

## The problem

When you ask the coach about «queso», it does not count «Quesillo», although quesillo is a
cheese. The obvious general rule that would catch it also makes «torta» count «Tortilla», so
this is a design choice, not a one-line fix. Nothing in the app was changed.

Decided so far (26 Nov 2026): «Arroz con queso» and «Empanada de queso» must count for «queso»
(they already do), and «Quesillo» should count too.

## How matching works today

Names are compared without accents or capitals, and every searched word must start a word of
the logged name, wherever that word is. The model chooses the search words in step 1: the
question's words in the singular, synonyms, and the name in the user's language and in English
(at most 8). The answer lists every name it counted, so a wrong match is visible.

| Logged name | Counts for «queso» | Why |
| --- | --- | --- |
| Queso fresco | Yes | «queso» starts the first word |
| Arroz con queso | Yes | «queso» starts the third word |
| Empanada de queso | Yes | «queso» starts the third word |
| Pan con quesos | Yes | «quesos» starts with «queso» |
| Cheeseburger | Only if the model also searches «cheese» | «cheese» starts «cheeseburger» |
| Quesillo | No | «quesillo» does not start with «queso» |
| Queque | No | different word |

## What was tested

A general diminutive rule gets 6 of 9 pairs right and 3 wrong, so it was not adopted. The rule
tried: a searched word ending in a vowel also counts a name word made of its stem plus -illo,
-illa, -ito or -ita (with or without -s). This was a scratch test only, not app code.

| Searched | Logged name | Rule counts it | Right? |
| --- | --- | --- | --- |
| queso | Quesillo | Yes | Yes |
| queso | Quesito | Yes | Yes |
| carne | Carnita | Yes | Yes |
| pollo | Pollito | Yes | Yes |
| galleta | Galletita | Yes | Yes |
| papa | Papita | Yes | Yes |
| torta | Tortilla | Yes | **No**: a flatbread, not a cake |
| pasta | Pastilla | Yes | **No** |
| bomba | Bombilla | Yes | **No** |
| leche | Lechuga | No | Yes |

Tortilla is logged often in one of the real exports, so a general rule would put wrong numbers
in the coach's answers without anyone noticing.

## Options to consider

The first proposal was option 1, an explicit list of kinds, because it cannot produce a wrong
match. It is not decided.

| Option | How it works | For | Against |
| --- | --- | --- | --- |
| 1. List of kinds, one way | A short list in `food-questions.json`: «queso» also counts «quesillo», «quesito»; asking «quesillo» counts only quesillo | Same on web and Android; no wrong matches; shows in «Logged names counted» | Has to be written and kept up; covers only listed foods; per language |
| 2. Spanish synonym table | Add the pair to `SPANISH_SYNONYMS` (`foods.ts`), already used by the food search | Exists already; also helps Add food | Two way: asking «quesillo» would count every cheese; changes the food search too |
| 3. The model adds kinds in step 1 | The prompt asks for common kinds the user may have logged | Nothing to keep up; any food, any language | Not guaranteed; the small phone model may miss them; can vary between runs |
| 4. Diminutive rule with exceptions | The rule tested above, minus tortilla, pastilla, bombilla… | Covers many foods at once | The exceptions never end; one missed exception gives wrong numbers silently |
| 5. Options 1 + 3 | The model suggests kinds; the list guarantees the common ones | Broad and safe for the listed foods | Two places to check when a match is wrong |

## Open questions

- [ ] One way or both ways: should asking about «quesillo» also count every «queso»?
- [ ] Which foods beyond cheese need kinds (carne → carnita, pollo → pollito…)? Start from the
      names in real logs?
- [ ] Which languages: the app has five, and logged names are usually in the user's language.
- [ ] Should the Add food search share the same list?
- [ ] Should the coach say what it added, e.g. «also counted: quesillo (a kind of queso)»?
- [ ] Tests: which wrong matches must stay out (torta/tortilla, pasta/pastilla, leche/lechuga)
      in the shared cases?

## Where things are

The repository is as committed on `dev`: no test case or docs line about «Quesillo» yet. The code
to change lives here:

| What | Web | Android |
| --- | --- | --- |
| Matching (`searchTerms`, `matchesTerms`) | `src/lib/core/food-questions/log.ts` | `android/core/.../foodquestions/Log.kt` |
| Where matching is used | `src/lib/core/food-questions/answer.ts` | `foodquestions/Answer.kt` |
| Step-1 prompt, rules, field texts | `src/lib/core/food-questions/food-questions.json` (shared) | same file, bundled |
| Food search synonyms (`SPANISH_SYNONYMS`, `queryAlternatives`) | `src/lib/core/foods.ts` | `core/Foods.kt` |
| Shared test cases (`basics.matches`) | `tests/fixtures/food-questions/cases.json`, read by `tests/unit/food-questions.test.ts` | read by `FoodQuestionsTest.kt` |
| Docs | `docs/METHODOLOGY.md` (coach food questions, «Matching»), `docs/TECHNICAL.md` §8.7 | same |

## When this is done

This file is temporary: it is for iterating in the next session, then it goes away.

- [ ] Write the decided rule, with its examples, into `docs/TECHNICAL.md` (and the «Matching»
      line of `docs/METHODOLOGY.md`).
- [ ] Add the shared test cases, including the wrong matches that must stay out.
- [ ] Delete this file and its entry in `docs/DEVELOPMENT.md`.
