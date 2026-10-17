# LargeJSON Viewer

An Android JSON viewer designed to search very large JSON files using a streaming parser.

## Current features

- Opens local or cloud-provider files through Android's document picker.
- Scans JSON tokens progressively instead of loading the complete file into memory.
- Searches field names and scalar values without case sensitivity.
- Shows matching JSON paths and values, with a cap of 200 displayed matches.
- Ignores individual scalar values above 1,000,000 characters to reduce memory risk.
- Does not require Google Play Services.

## Build

The GitHub Actions workflow builds a debug APK on every push to `main` and on manual runs. Download it from the workflow run's Artifacts section.

## Important limitations

- A search on a 15 GB file may take a long time because it scans the file sequentially.
- Memory use is bounded by the parser and the current token, not by the entire file; unusually large individual JSON values can still be expensive, and values over the configured threshold are skipped.
- The first version searches for text in field names and scalar values. It is not yet a full interactive tree browser or an index-backed search engine.
- JSON must be valid. The app reports parser errors instead of repairing malformed input.
- The selected file is read in place and is not copied into the app's storage.

## Install on Meta Quest

Download the debug APK from GitHub Actions, then install it on your Quest using your usual sideloading method. Compatibility still needs to be tested on the target Quest OS version.
