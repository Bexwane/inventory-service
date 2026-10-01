import urllib.request
import urllib.error
import json
import uuid
import random

API_BASE = 'http://localhost:8080/api/v1'

CATEGORIES = {
    'LAPTOP': ['MACBOOK-PRO-16', 'MACBOOK-AIR-M3', 'DELL-XPS-15', 'THINKPAD-T14', 'SURFACE-PRO-9'],
    'PHONE': ['IPHONE-15-PRO', 'IPHONE-14', 'GALAXY-S24', 'PIXEL-8-PRO', 'ONEPLUS-12'],
    'MONITOR': ['DELL-ULTRASHARP-27', 'LG-OLED-42', 'SAMSUNG-ODYSSEY-G7', 'ASUS-PROART-32'],
    'AUDIO': ['AIRPODS-PRO-2', 'SONY-WH1000XM5', 'BOSE-QC-ULTRA', 'JABRA-ELITE-10'],
    'ACC': ['MAGIC-MOUSE', 'MX-MASTER-3S', 'KEYCHRON-Q1', 'APPLE-PENCIL-2', 'ANKER-737-PB']
}

def get_token():
    try:
        data = json.dumps({"username": "admin", "password": "adminpassword"}).encode('utf-8')
        req = urllib.request.Request(f"{API_BASE}/auth/login", data=data, headers={'Content-Type': 'application/json', 'X-Idempotency-Key': str(uuid.uuid4())})
        with urllib.request.urlopen(req) as res:
            body = json.loads(res.read().decode('utf-8'))
            return body.get('accessToken')
    except Exception as e:
        print(f"Failed to login: {e}")
        return None

def seed_inventory(token, num_entries=200):
    locations = [str(uuid.uuid4()) for _ in range(50)]
    success_count = 0
    print(f"Starting to seed {num_entries} inventory records...")
    
    for i in range(num_entries):
        category = random.choice(list(CATEGORIES.keys()))
        sku = random.choice(CATEGORIES[category])
        loc_id = random.choice(locations)
        qty = random.randint(5, 500)
        
        payload = {
            "sku": sku,
            "locationId": loc_id,
            "containerId": str(uuid.uuid4()),
            "qty": qty,
            "taskId": f"TSK-{random.randint(1000, 9999)}",
            "referenceId": f"PO-2026-{random.randint(1000, 9999)}"
        }
        
        headers = {
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json',
            'X-Idempotency-Key': str(uuid.uuid4())
        }
        
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(f"{API_BASE}/inventory/receive", data=data, headers=headers)
        
        try:
            with urllib.request.urlopen(req) as res:
                if res.status == 200:
                    success_count += 1
                    if success_count % 20 == 0:
                        print(f"Seeded {success_count}/{num_entries}...")
        except urllib.error.HTTPError as e:
            print(f"Failed to seed {sku}: {e.code} - {e.read().decode('utf-8')}")
        except Exception as e:
            print(f"Error seeding {sku}: {e}")
            
    print(f"Finished! Successfully seeded {success_count} records.")

if __name__ == "__main__":
    token = get_token()
    if token:
        seed_inventory(token, 150)
    else:
        print("Could not obtain auth token. Is the backend running?")
