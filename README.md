# CGCStreamExtractor — self-hosted Android Mirror

This version has **no relay server**.

The Android emulator itself:

1. asks Android for screen capture permission;
2. waits 5 seconds;
3. captures its display with `MediaProjection`;
4. encodes H.264 using Android `MediaCodec`;
5. packages the video as MPEG-TS/HLS;
6. runs an HTTP server on port `8080`;
7. serves:
   `/stream/index.m3u8`
8. checks that the playlist actually exists;
9. sends a notification containing the local URL and, when configured and
   reachable, the public URL.

## Important: the online emulator must expose a public TCP/HTTP port

The app cannot invent a public address.

The online Android emulator/provider needs to give your Android VM something
like:

    https://YOUR-PROVIDER-HOST/

and forward a public port/path to the Android VM's port 8080.

Then put this in the app:

    https://YOUR-PROVIDER-HOST

The app will use:

    https://YOUR-PROVIDER-HOST/stream/index.m3u8

If the provider uses a port:

    http://YOUR-PROVIDER-HOST:8080

then enter that exact base address instead.

If there is no public incoming-port/reverse-proxy feature at the emulator
provider, the app can still host the stream locally, but nobody on the public
internet will be able to reach it.

## Local URL

Inside the Android VM, the stream is:

    http://127.0.0.1:8080/stream/index.m3u8

Another device on the same reachable network may be able to use:

    http://<ANDROID_VM_IP>:8080/stream/index.m3u8

depending on the emulator's networking rules.

## Build online

This is a normal Gradle Android project. You can upload it to a GitHub repo
and use the included GitHub Actions workflow from the previous version, or
import it into an online Android build service that supports Gradle/Kotlin.

## Current implementation

The project uses only Android platform APIs plus AndroidX Core:

- MediaProjection
- MediaCodec
- VirtualDisplay
- ServerSocket
- a small MPEG-TS/HLS writer
- a small HTTP server

No external streaming server is required.

## Notes

This is a simple video-only HLS implementation. It has no audio track.

The screen mirror is the Android emulator's display, not a specific app window.

For smooth public playback, the online emulator needs enough CPU/network
capacity for H.264 encoding and HLS serving.

Only stream/mirror content you are authorized to publish.


## Public URL behavior

The app does not create a public DNS name itself. In an online Android
emulator, use the provider's port-forward/reverse-proxy address as the
**Public base URL**.

Example:

    https://abc123.provider.example

The app checks:

    https://abc123.provider.example/stream/index.m3u8

and only includes that URL in the "Mirror ready" notification when the app
can fetch an actual HLS playlist containing at least one segment entry.

If the provider blocks the emulator from reaching its own public URL (hairpin
NAT), the public check may fail even though outside clients can reach it. In
that case the app still reports the local URL; verify the public URL from an
external device/network.

## What the app does NOT provide

It does not automatically discover a provider's public hostname or port.
That information belongs to the online emulator service. The app simply hosts
HTTP on 0.0.0.0:8080 and uses the base URL you provide.
