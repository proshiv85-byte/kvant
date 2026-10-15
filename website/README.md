# Kvant website

The site for https://kvant.sigeon.space. It's plain static files with no build step:

- `index.html`: the whole page (HTML, CSS and JavaScript in one file)
- `kvant.svg`: the app icon (a copy of `data/kvant.svg`)

## Publishing

Upload the contents of this folder to the web root of `kvant.sigeon.space`.
To try it locally, open `index.html` in a browser or run `python3 -m http.server` in this folder.

The page loads the Roboto Flex and Roboto Mono fonts from Google Fonts. Everything else is in `index.html`.

## Download links

All download buttons point to `https://github.com/proshiv85-byte/kvant/releases/latest`,
so they keep working after every new release without changes to the site.

## Adding screenshots

The player on the page is a working HTML mockup built from the app's own code: the color
scheme is a port of `src/theme.cpp`, and the squiggly bar is a port of `src/wavyprogress.cpp`.
To show real screenshots instead:

1. Put the images in a `screenshots/` folder next to `index.html`.
2. In `index.html`, find the comment that starts with `SCREENSHOTS:` and replace the
   `<div class="device" …> … </div>` block below it with an `<img>`, as shown in that comment.

If you replace the mockup, the settings panel next to it will only recolor the page; it
won't change the screenshot.
