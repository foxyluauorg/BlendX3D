#!/usr/bin/env node
/* BlendX3D MCP server - zero dependencies (Node 18+).
 *   node server.js            -> HTTP: MCP at http://localhost:3333/mcp  (Claude Code, Cursor, ChatGPT via tunnel)
 *   node server.js --stdio    -> MCP over stdio (Claude Desktop) + the editor bridge on the same HTTP port
 * The editor (blendx3d.html > Window > AI / MCP Connect) long-polls /bridge/poll for jobs.
 * Env: PORT (3333), BLENDX_TOKEN (optional shared secret), HOST (0.0.0.0) */
const http = require("http"), fs = require("fs"), path = require("path");
const PORT = +process.env.PORT || 3333, HOST = process.env.HOST || "0.0.0.0", TOKEN = process.env.BLENDX_TOKEN || "";
const STDIO = process.argv.includes("--stdio"), OUT = path.join(__dirname, "exports");
const log = (...a) => console.error("[blendx3d-mcp]", ...a);

/* ---------------- bridge to the browser editor ---------------- */
const queue = [], waiters = [], pending = new Map();
let lastPoll = 0, seq = 0, publicTunnelUrl = "";
const connected = () => Date.now() - lastPoll < 45000;
function callEditor(cmd, args) {
  if (!connected()) return Promise.reject(new Error("The BlendX3D editor is not connected. Open blendx3d.html, then Window > AI / MCP Connect > Connect."));
  return new Promise((resolve, reject) => {
    const id = ++seq, job = { id, cmd, args: args || {} };
    const timer = setTimeout(() => { pending.delete(id); reject(new Error("Editor did not answer in 60s (is the tab in the foreground?)")); }, 60000);
    pending.set(id, { resolve, reject, timer });
    const w = waiters.shift();
    if (w) { clearTimeout(w.t); w.res.writeHead(200, { "content-type": "application/json" }); w.res.end(JSON.stringify(job)); } else queue.push(job);
  });
}

/* ---------------- tool definitions ---------------- */
const vec = { type: "array", items: { type: "number" }, minItems: 3, maxItems: 3 };
const TYPES = ["Cube", "UV Sphere", "Icosphere", "Cylinder", "Cone", "Torus", "Plane"];
const common = {
  position: { ...vec, description: "[x, y, z] in meters. Blender convention: Z is up, X right, Y forward." },
  rotation_deg: { ...vec, description: "[x, y, z] Euler rotation in degrees." },
  scale: { ...vec, description: "[x, y, z] scale factors. Primitives are 2 units across at scale 1 (radius/half-size 1)." },
  color: { type: "string", description: "Hex colour such as #ff8800." },
  roughness: { type: "number", description: "0 (glossy) to 1 (matte)." },
  metalness: { type: "number", description: "0 (plastic) to 1 (metal)." },
  visible: { type: "boolean" }, wireframe: { type: "boolean" }, flat_shading: { type: "boolean" }
};
const obj = (properties, required = []) => ({ type: "object", properties, required, additionalProperties: false });
const TOOLS = [
  { name: "get_scene", description: "List every object in the BlendX3D scene with type, position, rotation, scale and material, plus the selected object. Call this first.", inputSchema: obj({}) },
  { name: "add_object", description: "Add a mesh primitive. Returns the created object (its name may get a .001 suffix if taken).", inputSchema: obj({ type: { type: "string", enum: TYPES }, name: { type: "string" }, ...common }, ["type"]) },
  { name: "update_object", description: "Change an object's transform, material, visibility or name. Only the given fields change.", inputSchema: obj({ name: { type: "string", description: "Existing object name." }, new_name: { type: "string" }, ...common }, ["name"]) },
  { name: "delete_object", description: "Delete an object by name.", inputSchema: obj({ name: { type: "string" } }, ["name"]) },
  { name: "duplicate_object", description: "Duplicate a mesh. The copy is offset 1m on X unless position is given.", inputSchema: obj({ name: { type: "string" }, new_name: { type: "string" }, position: common.position }, ["name"]) },
  { name: "select_object", description: "Select an object (shows it in the editor's properties).", inputSchema: obj({ name: { type: "string" } }, ["name"]) },
  { name: "clear_scene", description: "Delete every mesh in the scene (camera and light stay).", inputSchema: obj({}) },
  { name: "undo", description: "Undo the last change in the editor.", inputSchema: obj({}) },
  { name: "redo", description: "Redo the last undone change.", inputSchema: obj({}) },
  { name: "set_camera", description: "Move the editor's orbit camera. azimuth_deg rotates around the vertical axis; polar_deg is the angle down from straight up (0 = top view, 90 = side view).", inputSchema: obj({ target: common.position, distance: { type: "number" }, azimuth_deg: { type: "number" }, polar_deg: { type: "number" } }) },
  { name: "set_view", description: "Snap the editor camera to a standard Blender view, keeping the current target and distance. front = looking along +Y, right = looking along -X, top = straight down, bottom = straight up. Take a screenshot afterwards to check.", inputSchema: obj({ view: { type: "string", enum: ["front", "back", "right", "left", "top", "bottom"] } }, ["view"]) },
  { name: "frame_selected", description: "Move the camera target to the selected object and zoom so it fills the view (like Blender's View Selected / numpad period). Select an object first with select_object.", inputSchema: obj({}) },
  { name: "screenshot", description: "Render the viewport and return it as a PNG image so you can check your work visually.", inputSchema: obj({ max_width: { type: "number", description: "Default 800." } }) },
  { name: "export_model", description: "Export the scene to a file on the machine running this server (folder: exports/). Formats: fbx (Blender/Unity/Unreal/Maya), glb, gltf, obj, stl, ply.", inputSchema: obj({ format: { type: "string", enum: ["fbx", "glb", "gltf", "obj", "stl", "ply"] }, selection_only: { type: "boolean" }, filename: { type: "string", description: "Without extension." } }, ["format"]) }
];
const INSTRUCTIONS = "BlendX3D is a Blender-style 3D editor. Coordinates are meters, Z is up. Call get_scene first, build with add_object/update_object, check progress with screenshot (use set_view for front/top/right views and frame_selected to zoom to an object), and finish with export_model.";

async function callTool(name, args) {
  if (!TOOLS.some(t => t.name === name)) throw new Error("Unknown tool " + name);
  const r = await callEditor(name, args);
  if (name === "screenshot") return { content: [{ type: "image", data: r.png_base64, mimeType: "image/png" }, { type: "text", text: `Viewport ${r.width}x${r.height}` }] };
  if (name === "export_model") {
    fs.mkdirSync(OUT, { recursive: true });
    const file = path.join(OUT, path.basename(r.name));
    fs.writeFileSync(file, Buffer.from(r.base64, "base64"));
    return { content: [{ type: "text", text: JSON.stringify({ saved_to: file, size_bytes: r.size }, null, 2) }] };
  }
  return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
}

/* ---------------- MCP JSON-RPC ---------------- */
async function handleRpc(m) {
  const id = m.id, ok = result => ({ jsonrpc: "2.0", id, result }), err = (code, message) => ({ jsonrpc: "2.0", id, error: { code, message } });
  if (m.id === undefined || m.id === null) return null; // notification
  try {
    switch (m.method) {
      case "initialize": return ok({ protocolVersion: (m.params && m.params.protocolVersion) || "2025-03-26", capabilities: { tools: {} }, serverInfo: { name: "blendx3d", version: "1.1.0" }, instructions: INSTRUCTIONS });
      case "ping": return ok({});
      case "tools/list": return ok({ tools: TOOLS });
      case "tools/call":
        try { return ok(await callTool(m.params.name, m.params.arguments)); }
        catch (e) { return ok({ content: [{ type: "text", text: e.message }], isError: true }); }
      case "resources/list": return ok({ resources: [] });
      case "prompts/list": return ok({ prompts: [] });
      default: return err(-32601, "Method not found: " + m.method);
    }
  } catch (e) { return err(-32603, e.message); }
}
const handleAny = async b => Array.isArray(b) ? (await Promise.all(b.map(handleRpc))).filter(Boolean) : handleRpc(b);

/* ---------------- HTTP ---------------- */
const authed = (req, url) => !TOKEN || req.headers["x-token"] === TOKEN || req.headers.authorization === "Bearer " + TOKEN || url.searchParams.get("token") === TOKEN;
const readBody = req => new Promise(r => { let d = ""; req.on("data", c => { d += c; if (d.length > 60e6) req.destroy(); }); req.on("end", () => r(d)); });
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://x");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "content-type,x-token,authorization,mcp-session-id,mcp-protocol-version,accept");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
  res.setHeader("Access-Control-Allow-Private-Network", "true");
  res.setHeader("Access-Control-Expose-Headers", "mcp-session-id");
  if (req.method === "OPTIONS") { res.writeHead(204); return res.end(); }
  const json = (c, o) => { res.writeHead(c, { "content-type": "application/json" }); res.end(JSON.stringify(o)); };
  if (url.pathname === "/" || url.pathname === "/health") return json(200, { name: "blendx3d-mcp", editor_connected: connected(), mcp: "/mcp" });
  if (url.pathname === "/public-url" && req.method === "GET") { if (!["127.0.0.1", "::1", "::ffff:127.0.0.1"].includes(req.socket.remoteAddress)) return json(403, { error: "local access only" }); return json(200, { url: publicTunnelUrl, token: TOKEN }); }
  if (url.pathname === "/public-url" && req.method === "POST") { if (!["127.0.0.1", "::1", "::ffff:127.0.0.1"].includes(req.socket.remoteAddress)) return json(403, { error: "local access only" }); let b; try { b = JSON.parse(await readBody(req)); } catch { return json(400, { error: "invalid JSON" }); } if (typeof b.url !== "string" || !/^https:\/\/[a-z0-9.-]+\.trycloudflare\.com$/i.test(b.url)) return json(400, { error: "invalid tunnel URL" }); publicTunnelUrl = b.url; return json(200, { ok: true, url: publicTunnelUrl }); }
  if (!authed(req, url)) return json(401, { error: "bad token" });
  if (url.pathname === "/mcp") {
    if (req.method === "POST") {
      let body; try { body = JSON.parse(await readBody(req)); } catch { return json(400, { jsonrpc: "2.0", id: null, error: { code: -32700, message: "Parse error" } }); }
      const out = await handleAny(body);
      if (out === null || (Array.isArray(out) && !out.length)) { res.writeHead(202); return res.end(); }
      return json(200, out);
    }
    if (req.method === "DELETE") { res.writeHead(200); return res.end(); }
    res.writeHead(405, { allow: "POST" }); return res.end();
  }
  if (url.pathname === "/bridge/poll") {
    lastPoll = Date.now();
    const job = queue.shift();
    if (job) return json(200, job);
    const w = { res, t: setTimeout(() => { const i = waiters.indexOf(w); if (i >= 0) waiters.splice(i, 1); res.writeHead(204); res.end(); }, 20000) };
    waiters.push(w);
    req.on("close", () => { clearTimeout(w.t); const i = waiters.indexOf(w); if (i >= 0) waiters.splice(i, 1); lastPoll = Date.now(); });
    return;
  }
  if (url.pathname === "/bridge/result" && req.method === "POST") {
    let r; try { r = JSON.parse(await readBody(req)); } catch { return json(400, {}); }
    const p = pending.get(r.id);
    if (p) { clearTimeout(p.timer); pending.delete(r.id); r.ok ? p.resolve(r.result) : p.reject(new Error(r.error || "editor error")); }
    return json(200, { ok: true });
  }
  json(404, { error: "not found" });
});
server.on("error", e => log(e.code === "EADDRINUSE" ? `Port ${PORT} is busy - set PORT=another and use the same URL in the editor.` : e.message));
server.listen(PORT, HOST, () => log(`ready  MCP: http://localhost:${PORT}/mcp  editor bridge: http://localhost:${PORT}${TOKEN ? "  (token required)" : ""}`));

if (STDIO) {
  const rl = require("readline").createInterface({ input: process.stdin });
  rl.on("line", async l => { l = l.trim(); if (!l) return; let m; try { m = JSON.parse(l); } catch { return; } const r = await handleAny(m); if (r !== null && !(Array.isArray(r) && !r.length)) process.stdout.write(JSON.stringify(r) + "\n"); });
  rl.on("close", () => process.exit(0));
}
