# Spoken notifications

Open **Settings → Notifications → Spoken notifications** in Django.

| Source | Default readout |
| --- | --- |
| Django / OpenAI Dots | Sender and full notification message |
| WhatsApp / WhatsApp Business | Sender name only |
| Texts | Sender and full SMS/RCS notification from the default messaging app |
| Incoming calls | Caller details supplied by an incoming CallStyle notification |
| Other apps | Off until individually selected |

Dots and texts also offer sender-only mode. WhatsApp offers Off or Sender only;
its body never becomes part of the speech request. A separate switch controls
the short tone before Finn speaks. Disabling call announcements leaves normal
phone ringing in place. Choices are included in settings backups.

Enable **Settings → Notifications → Only while phone is locked** to keep
notifications on the phone while you're using it. The switch is off by default.
When enabled, Django delivers notifications only with the lock screen showing
or the screen off. The switch applies to Finn and to native notifications on
glasses with a display.

The **Test Finn notification** button plays a local preview. Wear the connected
glasses and finish any assistant conversation first. The preview works while
the phone is unlocked, even with the lock-only switch enabled, if Do Not Disturb is off.

## Delivery behavior

- Notifications use the configured cloud speech service directly. They are not
  treated as instructions for the assistant.
- Long messages are split into speech-sized chunks without dropping words.
- Announcements wait for the entire normal assistant conversation, Dots calls,
  Live AI and video recording to end. Queued messages expire after two minutes;
  incoming call notices expire after twelve seconds.
- Wake-up, disconnection, removal, audio-focus loss or a source being disabled
  stops a pending readout. The player checks the paired glasses' Bluetooth route
  during a silent prefix and monitors it during playback.
- Only new posts are handled; opening Django does not replay the existing inbox.
- Outgoing messages, group summaries, ongoing progress, low-importance notices
  and notifications filtered by Android's interruption rules are excluded.
  Do Not Disturb allows the exceptions you set on your phone. Django checks the
  current lock state and interruption rules again before delivery and during
  Finn playback. Skipped notifications aren't replayed.
- Content and PCM are kept in memory. This feature's diagnostic logs contain
  source and routing metadata, without sender names or message bodies.

On non-display glasses this replaces Django's old native notification mirror.
The speech companion must be running and online for Finn synthesis. Other
notification readers or native phone/glasses announcements are separate settings.

## Source matching and validation

Dots selection accepts ChatGPT's agent/Codex notification channels, excluding
ongoing voice/session controls. Response, completion and messaging channels
require Django or Dot in the title/sender metadata. Ordinary ChatGPT promotions,
recommendations and unrelated channels are excluded. A real incoming Dots
notification is still needed to validate this account-specific delivery path.

Incoming calls require explicit incoming CallStyle metadata and an available
glasses audio route. Legacy notifications without those details are skipped.

The local tone/Finn preview and a normal conversation with a follow-up passed
hardware checks on 16 November 2026. Unit tests cover the lock and Do Not Disturb
rules. Real incoming Dots, WhatsApp, SMS and ringing-call tests are separate
from the synthetic notification checks.

The default-SMS lookup uses the package query described in the
[Android Telephony.Sms reference](https://developer.android.com/reference/android/provider/Telephony.Sms).
