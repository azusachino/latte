# yande.re fixtures

These fixtures are minimal, synthetic examples of the legacy-array shape used
by the anonymous yande.re post endpoint. They are checked in for deterministic
decoder and contract tests; they are not copied response bodies and contain no
cookies, credentials, or raw headers.

| File | Provenance | Review |
| --- | --- | --- |
| `post-page.json` | Shape reviewed against `https://yande.re/post.json` on 2026-09-20; values are synthetic fixture data | Safe for tests; no remote media is fetched |
