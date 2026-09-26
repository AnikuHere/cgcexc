# CGCStreamExtractor — self-hosted Android Mirror

Mirror your stream as m3u8

## Local URL

Inside the Android VM, the stream is:

    http://127.0.0.1:8080/stream/index.m3u8

Another device on the same reachable network may be able to use:

    http://<ANDROID_VM_IP>:8080/stream/index.m3u8

depending on the emulator's networking rules.

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
