import { createServer } from "node:http";

const server = createServer((request, response) => {
  response.writeHead(200, { "Content-Type": "application/json" });
  response.end(request.url?.includes("/ai/config") ? "{}" : "[]");
});

server.listen(18080, "127.0.0.1");
