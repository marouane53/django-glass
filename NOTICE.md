# Attribution and modification notice

**Django Glass is derived from [Visor](https://github.com/macedonga/visor), created by macedonga and the Visor contributors.**

The source snapshot used for this project is Visor v3.0.4, commit
[`5bb7141524832b4a8c5dba552d50b8e6e385919e`](https://github.com/macedonga/visor/commit/5bb7141524832b4a8c5dba552d50b8e6e385919e).
Visor supplies the Android client, glasses protocol, pairing, media import, settings,
assistant foundation and development-kit integration. Its authors retain credit
for that work and all existing copyright notices are preserved.

This independent repository begins with a source snapshot rather than GitHub's
fork mechanism. [README-VISOR.md](README-VISOR.md) preserves the original project
README. Original contribution and reporting policies are archived under
[docs/upstream](docs/upstream).

Modifications by Marouane / [marouane53](https://github.com/marouane53), November 2026:

- Separate Android application identity and Django branding.
- Companion-based cloud transcription and Finn speech synthesis.
- English, French and Moroccan Darija recognition guidance.
- Conversation ownership, timeout, follow-up and touch-stop handling.
- Explicit handoff to an existing OpenAI dot through the native ChatGPT app.
- Additional call aliases and voice camera routing.
- Explicit photo-analysis requests and reuse of the reference image on follow-ups.
- Selectable spoken notifications, including WhatsApp sender-only announcements.
- Regression tests, setup documentation and build tooling for these changes.

Selected changes from [Visor v3.1.0](https://github.com/macedonga/visor/releases/tag/v3.1.0)
are also included, with credit to their upstream contributors: lock-only
notifications ([f189f860](https://github.com/macedonga/visor/commit/f189f860))
and Do Not Disturb filtering ([12e3821a](https://github.com/macedonga/visor/commit/12e3821a)),
adapted for Django's Finn reader, and the contact number encoding fix
([79b475a7](https://github.com/macedonga/visor/commit/79b475a7),
[b89e0b3f](https://github.com/macedonga/visor/commit/b89e0b3f)).

The complete modified project, including the speech companion, is distributed
under the GNU Affero General Public License version 3. See [LICENSE](LICENSE).
The license file is retained verbatim from the upstream snapshot. Third-party
components retain their respective licenses and existing attribution.

Django Glass is independent of the Visor maintainers, Meta, EssilorLuxottica,
OpenAI, Google and Microsoft.
