import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.GRACE_BASE_URL || "http://host.docker.internal:8080";
const tenant = __ENV.GRACE_TENANT || "default";

export const options = {
  stages: [
    { duration: "15s", target: 1 },
    { duration: "30s", target: 1 },
    { duration: "15s", target: 0 }
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<750"],
    "http_req_duration{endpoint:search}": ["p(95)<1000"]
  }
};

export default function () {
  const responses = http.batch([
    ["GET", `${baseUrl}/api/v1/public/tenants/${tenant}/guides`, null, { tags: { endpoint: "guides" } }],
    ["GET", `${baseUrl}/api/v1/public/tenants/${tenant}/search?q=research`, null, { tags: { endpoint: "search" } }],
    ["GET", `${baseUrl}/api/v1/public/tenants/${tenant}/resources`, null, { tags: { endpoint: "resources" } }]
  ]);
  responses.forEach(response => check(response, { "response is successful": value => value.status === 200 }));
  sleep(1);
}
