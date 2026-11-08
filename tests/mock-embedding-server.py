"""本地 mock Embedding 服务:模拟"远程向量服务可用"的场景。

用途:历史遗留的远程向量 mock 服务。simple 模式已删除,当前 app.rag.mode=framework 不使用它;
说明:用字符二元组(bigram)哈希生成向量,使"字面相近"的文本余弦相似度高,
用途:历史遗留的远程向量 mock 服务。simple 模式已删除,当前 app.rag.mode=framework 不使用它;
"""
import hashlib
import json
import math
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIM = 1024


def embed(text: str):
    vec = [0.0] * DIM
    grams = [text[i:i + 2] for i in range(len(text) - 1)] or list(text)
    for g in grams:
        idx = int(hashlib.md5(g.encode("utf-8")).hexdigest(), 16) % DIM
        vec[idx] += 1.0
    norm = math.sqrt(sum(x * x for x in vec)) or 1.0
    return [x / norm for x in vec]


class Handler(BaseHTTPRequestHandler):
    # HTTP/1.1:让 Java 的 HttpURLConnection(RestTemplate 默认实现)能正常复用/关闭连接,
    # 默认的 HTTP/1.0 会导致 "An established connection was aborted" 之类的传输层报错。
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw)
        except Exception:
            payload = {}
        text = payload.get("input", "")
        if isinstance(text, list):
            text = " ".join(str(t) for t in text)
        body = json.dumps({
            "object": "list",
            "model": "mock-bge-m3",
            "data": [{"object": "embedding", "index": 0, "embedding": embed(str(text))}],
            "usage": {"prompt_tokens": 1, "total_tokens": 1},
        }).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 18099), Handler)
    print("mock embedding server listening on http://127.0.0.1:18099/v1/embeddings", flush=True)
    server.serve_forever()
