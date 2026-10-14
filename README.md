# Kvant

A clean, lightweight audio player for Linux with a Material 3 look: the squiggly
progress line, a morphing play button and a full color scheme generated from one accent color.

## Features

- Play a single file, or open a folder (it's scanned recursively) as a queue
- Queue panel you can put on the left or right, or collapse
- Squiggly progress bar that settles into a flat line when paused. You can set its height, wavelength and speed, or switch it off
- Light / Dark / follow system theme, with any accent color
- Drag & drop files and folders onto the window to add them to the queue
- MPRIS support: media keys, desktop media widgets, `playerctl`
- Single instance: "Open with Kvant" in a file manager reuses the open window
- Embedded cover art, or `cover.jpg` / `folder.jpg` next to the files

## Building

Dependencies: Qt ≥ 6.8 (base, declarative, multimedia + a backend such as
`qt6-multimedia-ffmpeg`), TagLib ≥ 2.0, CMake.

```sh
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr
cmake --build build
sudo cmake --install build
```

Run without installing: `./build/kvant [files or folders…]`

## Keyboard shortcuts

| Key | Action |
| --- | --- |
| Space | Play / pause |
| ← / → | Seek 5 s |
| Ctrl+← / Ctrl+→ | Previous / next track |
| Ctrl+O | Open files |
| Ctrl+Shift+O | Open folder |
| Ctrl+, | Settings |
| Ctrl+Q | Quit |

## Fonts

Kvant uses *Google Sans Flex*, *Google Sans*, *Roboto Flex* or *Roboto* if one of them is
installed, and otherwise falls back to your system font.

## License

GPL-3.0-or-later. Icons are from Google's Material Icons (Apache-2.0).
