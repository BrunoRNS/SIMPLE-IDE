# Audio and video viewers

When JavaFX media is available, audio and video files open in separate read-only
viewers. The video viewer provides playback controls and the audio player provides
play/pause, seek, and volume controls. Builds without JavaFX continue to open
media files in the hex editor.

The audio implementation uses JavaFX MediaPlayer/AudioClip only. The build exposes
three consumer and release variants:

- `nojavafx` omits OpenJFX, for systems without supported JavaFX natives and for
  applications that do not want JavaFX in their dependency graph.
- `javafx22` uses OpenJFX 22.0.2 and supports JDK 17+ on supported systems.
- `javafx23` uses OpenJFX 23.0.2 and requires JDK 21+.

See [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media) for OS, architecture,
JDK, and system library requirements, and [release artifacts](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases)
for the variants published for each platform.
