import socket, struct, sys
def pkt(i, t, s): b = s.encode() + b'\x00\x00'; return struct.pack('<iii', len(b) + 8, i, t) + b
def rcon(cmds, host='127.0.0.1', port=25598, pw='bttf'):
    s = socket.create_connection((host, port)); s.sendall(pkt(1, 3, pw)); s.recv(4096)
    out = []
    for c in cmds:
        s.sendall(pkt(2, 2, c)); data = s.recv(65536); out.append(data[12:-2].decode(errors='replace'))
    s.close(); return out
if __name__ == '__main__':
    for c, r in zip(sys.argv[1:], rcon(sys.argv[1:])): print('>', c, '|', r)
