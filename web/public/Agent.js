export class Agent {
    constructor(id, latLong, follow, map, key) {
        this.id = id;
        this.follow = follow || false;

        this.position = latLong;
        this.lastDirection = 'left';
        this.map = map;
        this.key = key;

        this.el = document.createElement('div');
        this.el.className = 'marker';

        this.marker = new mapboxgl.Marker(this.el)
            .setLngLat([latLong.longitude, latLong.latitude])
            .addTo(this.map);
    }

    async move(destinationLatLong) {

        // Plot a route between the place the agent was last located, and the updated destinationLatLong
        const routeResponse = await TryGetRoute(this.position, destinationLatLong, this.key);

        // If there's no route, we can't really do anything at all.
        if (routeResponse == null) {
            console.log("Couldn't find a path.");
            return;
        }

        // Retrieve a list of coordinates from the mapBox route finding response
        const pathAsLatLongs = routeResponse.geometry.coordinates;

        // If there's only one point, we can't really follow a route!
        if (pathAsLatLongs.length <= 1) {
            console.log("Path didn't contain enough points to follow.");
            return;
        }

        // Uses the turf.js mapbox plugin to plot the route coordinates as a path
        let path = turf.linestring([...pathAsLatLongs]);        
        let pathLength = turf.lineDistance(path, 'miles');

        // Configure the number of "steps" we're going to take down that line
        // We're preparing to play an animation where each frame is a step.

        let step = 0;
        const numSteps = 500; //Change this to set animation resolution
        const timePerStep = 20; //Change this to alter animation speed
        
        // Configure a setInterval callback that will run every 20ms
        // Each invocation is a step.

        let interval = setInterval(() => {
            step += 1;
            
            // We've completed all our steps, so stop running our animation
            if (step > numSteps) {
                clearInterval(interval);
                return;
            } 
            
            // Calculate the distance along our line we should be, based on which step we're on
            const curDistance = step / numSteps * pathLength;
            const targetLocation = turf.along(path, curDistance, 'miles');
            const target = targetLocation.geometry.coordinates;

            // Target now contains the latLong of the location at that percentage along our line

            const currentLngLat = this.marker.getLngLat();
            const targetLngLat = { lng: target[0], lat: target[1] };

            // Given the current target location, we'll work out if we've moved to the left/right/up/down based vs where we just came from
            // We need to know this to make sure we draw our agent facing the correct direction
            // We set a data-direction HTML attribute so we can style this up in the UI.

            const direction = this.getDirectionOfTravel(currentLngLat, targetLngLat);
            this.el.setAttribute("data-direction", direction);

            // Update the location of the marker and paint it on the map using mapbox
            this.marker.setLngLat(target);
            this.marker.addTo(this.map);

        }, timePerStep);

        this.position = destinationLatLong;
    }

    getDirectionOfTravel(currentLngLat, targetLngLat) {
        // First we're going to add a bit of a GPS dead-zone.
        // If multiple messages occur and the total distance is tiny, we're just going to presume
        // the agent hasn't actually moved or changed direction.
        // If we react to the subtle changes in GPS location, the UI might end up flickering as we
        // interpret the small changes in coordinates as larger changes in agent direction.
        
        const distanceInMeters = this.calculateDistanceBetweenPoints(currentLngLat.lat, currentLngLat.lng, targetLngLat.lat, targetLngLat.lng);        
        if (distanceInMeters < 0.1) { // This prevents flickering when GPS coordinates subtly shift
            return this.lastDirection;
        }

        // Calculate the difference between the last known location and the current one
        // We're going to take the larger of the two, and use that as the direction the agent is travelling in
        
        const directionLng = targetLngLat.lng <= currentLngLat.lng ? 'left' : 'right';
        const directionLat = targetLngLat.lat <= currentLngLat.lat ? 'down' : 'up';    
        const diffLng = Math.abs(targetLngLat.lng - currentLngLat.lng);
        const diffLat = Math.abs(targetLngLat.lat - currentLngLat.lat);

        // We're also going to save the direction we pick, so we can use it in the case of small GPS changes.
        this.lastDirection = diffLng >= diffLat ? directionLng : directionLat;
        return this.lastDirection;
    }  
    
    // https://en.wikipedia.org/wiki/Haversine_formula
    // https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
    calculateDistanceBetweenPoints(lat1, lon1, lat2, lon2) {
        const R = 6378.137; // Radius of earth in KM
        let dLat = lat2 * Math.PI / 180 - lat1 * Math.PI / 180;
        let dLon = lon2 * Math.PI / 180 - lon1 * Math.PI / 180;
        let a = Math.sin(dLat/2) * Math.sin(dLat/2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLon/2) * Math.sin(dLon/2);
        let c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        let d = R * c;
        return d * 1000; // meters
    }
}

async function TryGetRoute(start, end, key) {
    try {
        return await GetRoute(start, end, key);
    } catch (exception) {
        console.log('There was an error routefinding.', exception);
        return null;
    }
}

async function GetRoute(start, end, key) {
    const directionsApi = `https://api.mapbox.com/directions/v5/mapbox/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?geometries=geojson&access_token=${key}`;

    const response = await fetch(directionsApi);
    const responseJson = await response.json();

    return responseJson.routes[0];
}
