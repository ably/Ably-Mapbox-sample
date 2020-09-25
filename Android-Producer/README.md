# Sending A Driver's GPS locations in Realtime

This demo covers how to send GPS locations of a driver as they travel. The rate of updates can be changed by requests from the client, and has the potential to fan-out to as many subscribers as needed. This demo is written in Java for Android devices, and uses the [Ably realtime Java SDK](https://github.com/ably/ably-java/), and the [GoogleApiClient](https://developers.google.com/android/reference/com/google/android/gms/common/api/GoogleApiClient).

## MainActivity.java


The main actions taken by the app are:

- Requesting permission to use the device's location
- Initializing a connection to Ably. This will be used to publish location updates, as well as subscribe to requests from the web tracker for different location update rates
- Start listening to location updates, passing these on to be published by Ably, and also displaying them on the device's screen

### Permissions

These are established in the `onCreate()` function, where we require both `ACCESS_FINE_LOCATION` and `ACCESS_COURSE_LOCATION`. If either of these are rejected by the user, they'll be re-prompted by the `onRequestPermissionsResult()` to accept.

### Ably

Ably is initialized in `initAbly()` function. For this to work, you will need to add in your Ably API key to the `API_KEY` variable. The process is:
- We connect to Ably (`AblyRealtime realtime = new AblyRealtime(clientOptions);`)
- We define the channel we're interested in (`channel = realtime.channels.get("agent002.delivery223.locations");`)
- We subscribe to the channel, listening for messages from the web interface changing the update interval (`channel.subscribe(...)`)

In the function `publishMessage(...)`, we are making use of the above `channel` to publish an update on the location of the device.

### Location updates

The Location updates come from **the LocationServices** API, which makes it trivial to check the current location of the device at a set interval. Once the device has successfully connected to the **GoogleApiClient**, the `onConnected()` callback is called, which sends the first location update, and starts the regular location updates with `startLocationUpdates()`.

`startLocationUpdates()` makes use of the dynamic `UPDATE_INTERVAL` to decide how regularly to check the device's current location. The callback `onLocationChanged()` is then called every interval of time, which uses the same function as `onConnected()`, `updateLocation()`, to update the UI with the current location and publish them with Ably.
