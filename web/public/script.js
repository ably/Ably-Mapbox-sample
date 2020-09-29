import { Agent } from './Agent.js';

(async function() {

  // Request our configuration from our server side node app
  // This means we can keep our mapbox API key in a .env file
  // and not have it checked into our repository.
  // There might be a better way of doing token authentication here.
  const configRequest = await fetch('/config');
  const config = await configRequest.json();
  
  // Here, we're setting up the mapboxgl library with our API key.
  mapboxgl.accessToken = config.mapBoxApiKey;

  // and creating an instance of the mapboxgl.Map type, zoomed out to the entire world.
  var map = new mapboxgl.Map({
    container: 'map',
    style: 'mapbox://styles/mapbox/streets-v11',
    center: [0, 0],
    zoom: 1
  });

  // Now, let's create our Ably SDK instance, and connect to our agents delivery channel 
  const ably = new Ably.Realtime.Promise({ authUrl: '/api/createTokenRequest' });
  const channelId = 'agent002.delivery223.locations';  
  const channel = ably.channels.get(channelId);
  await channel.attach();

  let agent = null;

  // This is most of the logic involved in tracking an agent.
  // We start off with a variable - agent - set to null.
  // This signals that we haven't received any messages from that agent yet.

  channel.subscribe((message) => {

    // This callback is invoked each time an ably message appears on the agents channel
    // First, we're parsing the message.data property, and using it to access the agentId, to track individual agents

    const parsedMessageData = JSON.parse(message.data);
    const agentId = parsedMessageData.id;

    // In this demo, we're only tracking one agent, so this id isn't important, but if you were to expand this to
    // multiple agents sending data on the same channel, we'd use the id to differentiate the location of multiple agents.

    // If we've received a message, and our agent is still null, it's the first time we've got a message
    // So let's create an instance of the "Agent" object - which encapsulates all the logic we use to move
    // the agent icon around our map.

    if (agent == null) {
      
      const followAgent = true;
      const agentLocation =  { 
        latitude: parsedMessageData.Lat,
        longitude: parsedMessageData.Lon
      };

      agent = new Agent(agentId, agentLocation, followAgent, map, config.mapBoxApiKey);

      // Zoom the map in on the agent after we've created it      
      map.flyTo({center: [agent.position.longitude, agent.position.latitude], zoom: 15, essential: true});
    }
    
    // Move the agent on the map.
    // This agent.move function contains all the path-finding and routing logic
    // along with movement animation code.
    
    agent.move({ latitude: parsedMessageData.Lat, longitude: parsedMessageData.Lon });
  });

  // Here are some debug controls for the demo.

  const speed = document.getElementById('speed');
  const animation = document.getElementById('animation');
  let smooth = animation.checked;

  animation.onchange = function(el) {
    smooth = el.target.checked;
  }

  speed.onchange = function(el) {
    if (el.target.checked) {
      channel.publish('update', { 'speed': 2 });
    } else {
      channel.publish('update', { 'speed': 10 });
    }
  }
  
})();
