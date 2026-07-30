// ==========================================
// 远程双人画布 - WebSocket 服务器
// 部署方式：node server.js 或 部署到 fly.io / render.com
// ==========================================
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });

// 房间: roomId -> Set<{ws, userId}>
const rooms = new Map();

console.log(`🎨 双人画布服务器启动: ws://0.0.0.0:${PORT}`);

wss.on('connection', (ws, req) => {
  let userId = '';
  let room = '';

  ws.on('message', (raw) => {
    try {
      const msg = JSON.parse(raw.toString());

      // 加入房间
      if (msg.type === 'join') {
        room = msg.room;
        userId = msg.userId;
        if (!rooms.has(room)) rooms.set(room, new Map());
        rooms.get(room).set(userId, ws);
        console.log(`👤 ${userId} 加入房间 ${room} (${rooms.get(room).size}人)`);
        // 广播给同房间其他人（除了自己）
        broadcast(room, userId, { type: 'join', userId, room });
      }

      // 转发的所有消息类型
      const forwardTypes = [
        'stroke_start', 'stroke_move', 'stroke_end', 'stroke_full',
        'undo', 'clear', 'cursor'
      ];

      if (forwardTypes.includes(msg.type) && room) {
        broadcast(room, userId, msg);
      }

    } catch (e) {
      // 忽略无效消息
    }
  });

  ws.on('close', () => {
    if (room && userId && rooms.has(room)) {
      rooms.get(room).delete(userId);
      console.log(`👋 ${userId} 离开房间 ${room} (${rooms.get(room).size}人)`);
      if (rooms.get(room).size === 0) rooms.delete(room);
    }
  });

  ws.on('error', () => {});
});

function broadcast(room, excludeUserId, msg) {
  if (!rooms.has(room)) return;
  const data = JSON.stringify(msg);
  rooms.get(room).forEach((ws, uid) => {
    if (uid !== excludeUserId && ws.readyState === 1) {
      ws.send(data);
    }
  });
}

// 健康检查
const http = require('http');
http.createServer((req, res) => {
  res.writeHead(200);
  res.end('DualDraw Server OK\n');
}).listen(PORT + 1 || 8081);