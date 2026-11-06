# Audio processing

The playback path is server stream → Media3 decoder → Musonic DSP → floating-point AudioTrack → Android output route.

Musonic accepts integer and floating-point PCM, processes samples with Double precision, and sends 32-bit float PCM to the audio sink. It preserves the decoded sample rate and channel count. Android, Bluetooth and the output device can still resample or encode audio. Processing precision does not restore information missing from a lossy source or guarantee bit-perfect output.

## Equalizer and gain

The ten band centers are 31, 63, 125, 250, 500 Hz, 1, 2, 4, 8 and 16 kHz. Each band supports ±15 dB with peaking biquad filters. Bands near the sample rate's Nyquist limit are bypassed to avoid unstable filters. The preamp ranges from −12 to 0 dB.

Automatic headroom uses the combined filter response to reduce clipping. A linked sample-peak limiter provides additional protection; it is not a true-peak or look-ahead limiter. Setting changes crossfade over 50 ms. Compressed passthrough and hardware offload are disabled so audio passes through the processing chain.

Selecting and applying a preset enables EQ. Flat EQ with zero preamp should sound unchanged. Boosting bass changes its level relative to other frequencies; automatic headroom can reduce overall loudness.

Channel balance independently attenuates left and right from 0 to −30 dB. It has its own enable switch. Mono has no independent left/right channels; multichannel processing affects the front pair.

## Loudness

Loudness adds a gentle tonal contour below the configured Android media-volume threshold. It is not ReplayGain, track normalization or calibrated acoustic compensation. At or above the threshold it adds no gain. Fixed-volume routes or unavailable volume readings bypass it. The displayed percentage is a system volume index, not a sound-pressure measurement.

## Streaming quality

Original quality requests `format=raw`. Compressed options request `format=mp3` and `maxBitRate` of 64, 128, 192 or 320. The server must support the requested transcoding. A quality change applies to a new queue or an explicit replay; it does not silently replace URLs in the current queue.

The player displays the selected incoming track's reported codec, average bitrate and sample rate when available. It does not turn the requested bitrate into a measured value. VBR rates may be averages and missing metadata is left unknown.

## Tests

`DspEngineTest` checks gain, frequency response, bypass, stability and transitions. Device tests exercise PCM conversion, partial output writes and settings changes in a running audio sink. These measure the app's PCM output before Android routing; headphone and vehicle listening remain separate checks.
