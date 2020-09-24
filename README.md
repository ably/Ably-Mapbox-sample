# Ably-Mapbox-sample

Example of a basic intergration to render positions from android onto a Map provided by Mapbox via Ably.

## Web Demo

### To configure `TokenRequests`

This application needs two API keys - one for Ably, and another for MapBox.

Make sure you have a `.env` file in the root of the web directory that looks something like this:

```bash
ABLY_API_KEY=yourably:apikeyhere
MAPBOX_API_KEY=yourmapboxapikey
```

### To run locally

In the web directory, first run:

```bash
npm install
```
to install the dependencies, then run:

```bash
npm run start
```
