import { Vehicle } from "./vehicle.js";

(async function() {

  const position = { latitude: 37.33757937, longitude: -122.04068128999999 };

  mapboxgl.accessToken = 'pk.eyJ1IjoidGhpc2lzam9mcmFuayIsImEiOiJjazl0dTkzZGIwMGY0M2ZwYXlidzBqc2VqIn0._NdPXGNS5xrGsepZgesYWQ';

  var map = new mapboxgl.Map({
    container: 'map',
    style: 'mapbox://styles/mapbox/streets-v11',
    center: [position.longitude, position.latitude],
    zoom: 15
  });

  // End of map setup.
 
  const ably = new Ably.Realtime.Promise({ authUrl: '/api/createTokenRequest' });
  const channelId = `agent002.delivery223.locations`;  
  const channel = ably.channels.get(channelId);

  await channel.attach();

  const agents = new Map();

  channel.subscribe((message) => {
    const vehicle = JSON.parse(message.data);
    const agentId = vehicle.id;

    if (!agents.has(agentId)) {
      const newAgent = new Vehicle(agentId, { 
        latitude: vehicle.Lat,
        longitude: vehicle.Lon
      }, true, map);

      agents.set(agentId, newAgent);      
      map.flyTo({center: [newAgent.position.longitude, newAgent.position.latitude], essential: true});
    }

    const agent = agents.get(agentId);
    
    agent.move({ 
      latitude: vehicle.Lat,
      longitude: vehicle.Lon
    });
  });

    // Start sliders setup

    const speed = document.getElementById("speed");
    const animation = document.getElementById("animation");
    let smooth = animation.checked;
  
    animation.onchange = function(el) {
      smooth = el.target.checked;
    }
  
    speed.onchange = function(el) {
      if(el.target.checked){
        channel.publish("update", "{ \"speed\": 2 }");
      }else{
        channel.publish("update", "{ \"speed\": 10 }");
      }
    }
})();
