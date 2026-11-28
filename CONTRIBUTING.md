# Contributing to Django

Django is an independent derivative of Visor. See [NOTICE.md](NOTICE.md)
for the original project, source commit and credits. Contributions use the
existing [AGPL-3.0 license](LICENSE).

Open issues and pull requests in
[marouane53/django-glass](https://github.com/marouane53/django-glass), so Django-specific
reports do not go to the Visor maintainers. Describe what happened, the expected
behavior, glasses model/firmware and Android version. Remove account details,
serial numbers, Bluetooth addresses and personal messages from diagnostics.

Before submitting code, run the relevant checks in [BUILDING.md](BUILDING.md).
Protocol, audio-route, microphone and gesture changes need hardware validation.
State clearly which parts were tested on glasses and which were checked only
with unit tests or synthetic Android notifications.

Preserve upstream copyright, license and attribution. Keep changes small and
avoid formatting unrelated source. Credentials, captured media, logs and signing
keys belong outside commits. The original protocol notes remain in [CLAUDE.md](CLAUDE.md),
and upstream contributor guidance is archived under [docs/upstream](docs/upstream).
