// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
//
// Loaded into the Firebase CLI with NODE_OPTIONS="--require <this file>" (#393). The Auth and
// Storage emulators are Node servers, which close a connection idle for 5 s; the device keeps idle
// connections for minutes and reuses them, so a request sent just as the emulator closed one
// failed with "unexpected end of stream". Every server the CLI starts now keeps idle connections
// open, as Firebase's own servers keep them for longer than the device does.
//
// The timeout is turned off rather than raised above the device's: how long a client keeps an idle
// connection depends on its HTTP stack (Android's, gRPC's, the Storage SDK's), isn't ours to set,
// and can change with an update. Connections left open cost nothing on a CI runner, where the
// emulators only live as long as the job.
const http = require("http");

const listen = http.Server.prototype.listen;
http.Server.prototype.listen = function (...args) {
  this.keepAliveTimeout = 0; // 0 turns the idle timeout off
  return listen.apply(this, args);
};
console.log("keep-emulator-connections: the emulators keep idle connections open");
