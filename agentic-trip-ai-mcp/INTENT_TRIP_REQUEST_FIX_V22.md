# V22 — Natural trip request intent fix

Fixed a backend routing regression where a clear request such as:

`From Bengaluru to Tokyo for 7 days under ₹2 lakh — family-friendly with good food.`

was classified by the final deterministic guardrail as GENERAL because it did not contain the literal words `plan`, `itinerary`, or `trip plan`.

Changes:
- Recognize `from X to Y for N days` as explicit trip planning.
- Recognize `need/want/going to travel from X to Y` as explicit trip planning.
- Recognize common `under/below/within <amount>` budget expressions.
- Run the deterministic explicit-intent guardrail for all requests, including trip requests.
- Do not infer flights/hotels merely from a route; only explicit flight/hotel requests activate those capabilities.
- Preserve the existing weather + travel-context => weather + knowledge behavior.
