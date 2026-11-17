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

The complete modified project, including the speech companion, is distributed
under the GNU Affero General Public License version 3. See [LICENSE](LICENSE).
The license file is retained verbatim from the upstream snapshot. Third-party
components retain their respective licenses and existing attribution.

Django Glass is independent of the Visor maintainers, Meta, EssilorLuxottica,
OpenAI, Google and Microsoft.
