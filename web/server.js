const express = require('express');
const Ably = require('ably/promises');
require('dotenv').config();

const client = new Ably.Realtime(process.env.ABLY_API_KEY);
const mapBoxApiKey = process.env.MAPBOX_API_KEY;
const port = process.env.PORT || 5000;

const app = express();
app.get("/", async (request, response) => {
    response.sendFile(__dirname + '/views/index.html');
});

app.get("/config", async (request, response) => {
    response.send({ mapBoxApiKey });
});

app.get("/api/createTokenRequest", async (request, response) => {
    const tokenRequestData = await client.auth.createTokenRequest({ 
        clientId: 'ably-client-side-api-call' 
    });
    response.send(tokenRequestData);
});
  
app.use(express.static('public'))

app.listen(port, () => console.log(`Example app listening at http://localhost:${port}`))
