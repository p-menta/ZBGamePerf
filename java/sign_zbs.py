import os
import hashlib
from cryptography.hazmat.primitives.asymmetric import ed25519
from cryptography.hazmat.primitives import serialization

STEAM_ID = '76561198257681880'
KEY_DIR = os.path.expanduser('~/.zombiebuddy')
PRIV_KEY_PATH = os.path.join(KEY_DIR, 'zbs_ed25519_private.pem')

if not os.path.exists(PRIV_KEY_PATH):
    raise FileNotFoundError(f'Private key not found at {PRIV_KEY_PATH}')

with open(PRIV_KEY_PATH, 'rb') as f:
    private_key = serialization.load_pem_private_key(f.read(), password=None)

jar_targets = [
    os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '42', 'media', 'java', 'client', 'ZBGamePerf.jar')),
    os.path.expanduser('~/Zomboid [unstable]/mods/ZBGamePerf/42/media/java/client/ZBGamePerf.jar'),
    os.path.expanduser('~/Zomboid [unstable]/Workshop/ZBGamePerf/Contents/mods/ZBGamePerf/42/media/java/client/ZBGamePerf.jar'),
]

for jar_path in jar_targets:
    if os.path.exists(jar_path):
        with open(jar_path, 'rb') as f:
            data = f.read()
        jar_sha256 = hashlib.sha256(data).hexdigest().lower()
        msg_str = f'ZBS:{STEAM_ID}:{jar_sha256}'
        signature = private_key.sign(msg_str.encode('utf-8'))
        sig_hex = signature.hex().lower()
        zbs_content = f'ZBS\nSteamID64:{STEAM_ID}\nSignature:{sig_hex}\n'
        zbs_path = jar_path + '.zbs'
        with open(zbs_path, 'w', encoding='utf-8') as f:
            f.write(zbs_content)
        print(f'[ZBS] Signed: {zbs_path}')
