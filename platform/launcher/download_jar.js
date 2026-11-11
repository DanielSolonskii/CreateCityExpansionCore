import fs from 'fs';
import fetch from 'node-fetch';

const url = 'https://api.recreated.ru/releases/objects/fe00d88e048d7100fe7d9649a3439097c01d0ee7b24cfb9fa0ae52e5750fcac0';
const response = await fetch(url);
const buffer = await response.arrayBuffer();
fs.writeFileSync('instances/alpha/mods/createcityexpansioncore-1.0.2.jar', Buffer.from(buffer));
console.log('JAR file downloaded successfully');