import asyncio
import websockets
import logging
import json
import sqlite3
import os

# Configure Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger("SecuAsistServer")

# Database Setup
DB_FILE = "secuasist.db"

def init_db():
    conn = sqlite3.connect(DB_FILE)
    try:
        c = conn.cursor()
        c.execute("PRAGMA foreign_keys = ON;") # Enable FK support
        
        # Villas Table
        c.execute('''CREATE TABLE IF NOT EXISTS villas (
            villaId INTEGER PRIMARY KEY, -- Use provided ID or generate
            villaNo INTEGER,
            villaStreet TEXT,
            villaNavigationA TEXT,
            villaNavigationB TEXT,
            villaNotes TEXT,
            isVillaUnderConstruction INTEGER,
            isVillaSpecial INTEGER,
            isVillaRental INTEGER,
            isVillaCallFromHome INTEGER,
            isVillaCallForCargo INTEGER,
            isVillaEmpty INTEGER,
            updatedAt INTEGER,
            deviceId TEXT
        )''')

        # Contacts Table (Changed contactId to TEXT for UUID)
        c.execute('''CREATE TABLE IF NOT EXISTS contacts (
            contactId TEXT PRIMARY KEY,
            villaId INTEGER,
            contactName TEXT,
            contactPhone TEXT,
            contactType TEXT,
            updatedAt INTEGER,
            deviceId TEXT,
            FOREIGN KEY(villaId) REFERENCES villas(villaId) ON DELETE SET NULL
        )''')

        # Cargo Companies Table
        c.execute('''CREATE TABLE IF NOT EXISTS companies (
            companyId INTEGER PRIMARY KEY,
            companyName TEXT,
            isCargoInOperation INTEGER,
            updatedAt INTEGER,
            deviceId TEXT
        )''')

        # Company Contacts Link Table (Many-to-Many) for Deliverers
        c.execute('''CREATE TABLE IF NOT EXISTS company_contacts (
            companyId INTEGER,
            contactId TEXT,
            role TEXT,
            isPrimaryContact INTEGER DEFAULT 0,
            updatedAt INTEGER,
            deviceId TEXT,
            FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE,
            FOREIGN KEY(contactId) REFERENCES contacts(contactId) ON DELETE CASCADE,
            PRIMARY KEY(companyId, contactId)
        )''')

        # Villa Contacts Link Table (Many-to-Many) (Changed contactId to TEXT)
        c.execute('''CREATE TABLE IF NOT EXISTS villa_contacts (
            villaId INTEGER,
            contactId TEXT,
            isRealOwner INTEGER DEFAULT 0,
            contactType TEXT,
            notes TEXT,
            updatedAt INTEGER,
            deviceId TEXT,
            FOREIGN KEY(villaId) REFERENCES villas(villaId) ON DELETE CASCADE,
            FOREIGN KEY(contactId) REFERENCES contacts(contactId) ON DELETE CASCADE,
            PRIMARY KEY(villaId, contactId)
        )''')

        # Cargos Table (Changed delivererContactId to TEXT)
        c.execute('''CREATE TABLE IF NOT EXISTS cargos (
            cargoId INTEGER PRIMARY KEY,
            companyId INTEGER,
            villaId INTEGER,
            whoCalled INTEGER,
            isCalled INTEGER DEFAULT 0,
            isMissed INTEGER DEFAULT 0,
            date TEXT,
            callDate TEXT,
            callAttemptCount INTEGER DEFAULT 0,
            callingDeviceName TEXT,
            delivererContactId TEXT,
            updatedAt INTEGER,
            deviceId TEXT,
            FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE,
            FOREIGN KEY(villaId) REFERENCES villas(villaId) ON DELETE CASCADE
        )''')
        
        # Cameras Table (Fault Tracking) - Removed villaId
        c.execute('''CREATE TABLE IF NOT EXISTS cameras (
            cameraId TEXT PRIMARY KEY,
            cameraName TEXT,
            cameraIp TEXT,
            isWorking INTEGER DEFAULT 1,
            lastChecked INTEGER,
            notes TEXT,
            updatedAt INTEGER,
            deviceId TEXT
        )''')

        # Camera Visible Villas Table (Many-to-Many)
        c.execute('''CREATE TABLE IF NOT EXISTS camera_visible_villas (
            cameraId TEXT,
            villaId INTEGER,
            updatedAt INTEGER,
            deviceId TEXT,
            FOREIGN KEY(cameraId) REFERENCES cameras(cameraId) ON DELETE CASCADE,
            FOREIGN KEY(villaId) REFERENCES villas(villaId) ON DELETE CASCADE,
            PRIMARY KEY(cameraId, villaId)
        )''')

        # Intercoms Table (Fault Tracking) - Linked to Villa (One-to-Many)
        c.execute('''CREATE TABLE IF NOT EXISTS intercoms (
            intercomId TEXT PRIMARY KEY,
            villaId INTEGER,
            intercomName TEXT,
            isWorking INTEGER DEFAULT 1,
            lastChecked INTEGER,
            notes TEXT,
            updatedAt INTEGER,
            deviceId TEXT,
            FOREIGN KEY(villaId) REFERENCES villas(villaId) ON DELETE CASCADE
        )''')
        
        conn.commit()
        logger.info("✅ Database initialized.")
    except Exception as e:
        logger.error(f"DB Init Error: {e}")
    finally:
        conn.close()

# Helper to run DB operations with proper closure and locking handling
def query_db(query, args=(), one=False):
    conn = sqlite3.connect(DB_FILE, timeout=10)
    try:
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        cur.execute("PRAGMA foreign_keys = ON;")
        cur.execute(query, args)
        rv = cur.fetchall()
        conn.commit() # Commit read transaction to release locks? usually verify
        return (rv[0] if rv else None) if one else rv
    finally:
        conn.close()

def insert_db(query, args=()):
    conn = sqlite3.connect(DB_FILE, timeout=10)
    try:
        cur = conn.cursor()
        cur.execute("PRAGMA foreign_keys = ON;")
        cur.execute(query, args)
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()

# Connected Clients: Map<DeviceId, WebSocket>
connected_clients = {}

async def handler(websocket):
    client_id = None
    try:
        async for message in websocket:
            logger.info(f"Received: {message}")
            
            # Simple Protocol
            if message.startswith("AUTH"):
                parts = message.split(" ")
                if len(parts) > 1:
                    client_id = parts[1]
                    connected_clients[client_id] = websocket
                    logger.info(f"✅ Client Authenticated: {client_id}")
                    await websocket.send("AUTH_OK")
            
            elif message == "PING":
                await websocket.send("PONG")

            # JSON Protocol for Sync
            else:
                try:
                    data = json.loads(message)
                    msg_type = data.get("type")
                    payload = data.get("payload")
                    
                    if msg_type == "ADD_VILLA":
                        try:
                            insert_db('''INSERT OR REPLACE INTO villas (
                                villaId, villaNo, villaStreet, villaNotes, 
                                isVillaCallForCargo, deviceId, updatedAt
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)''', (
                                payload.get("villaId"), payload.get("villaNo"), payload.get("villaStreet"), 
                                payload.get("villaNotes"), payload.get("isVillaCallForCargo", 1), 
                                payload.get("deviceId"), payload.get("updatedAt")
                            ))
                            logger.info(f"Saved Villa: {payload.get('villaNo')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_VILLA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "ADD_CARGO":
                        try:
                            insert_db('''INSERT OR REPLACE INTO cargos (
                                cargoId, companyId, villaId, whoCalled, isCalled, isMissed, 
                                date, callDate, callAttemptCount, deviceId, updatedAt
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''', (
                                payload.get("cargoId"), payload.get("companyId"), payload.get("villaId"),
                                payload.get("whoCalled"), payload.get("isCalled", 0), payload.get("isMissed", 0),
                                payload.get("date"), payload.get("callDate"), payload.get("callAttemptCount", 0),
                                payload.get("deviceId"), payload.get("updatedAt")
                            ))
                            logger.info(f"Saved Cargo: {payload.get('cargoId')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_CARGO): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "UPDATE_CARGO_STATUS":
                        try:
                            insert_db('''UPDATE cargos SET 
                                isCalled=?, isMissed=?, callDate=?, callAttemptCount=?, whoCalled=? 
                                WHERE cargoId=?''', (
                                payload.get("isCalled"), payload.get("isMissed"), payload.get("callDate"),
                                payload.get("callAttemptCount"), payload.get("whoCalled"), payload.get("cargoId")
                            ))
                            logger.info(f"Updated Cargo Status: {payload.get('cargoId')}")
                        except Exception as e:
                             logger.error(f"DB Error (UPDATE_CARGO): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)
                    
                    elif msg_type == "ADD_COMPANY":
                        try:
                            insert_db("INSERT OR REPLACE INTO companies (companyId, companyName, isCargoInOperation) VALUES (?, ?, ?)",
                                      (payload.get("companyId"), payload.get("companyName"), payload.get("isCargoInOperation", 1)))
                        except Exception as e:
                            logger.error(f"DB Error (ADD_COMPANY): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "ADD_CONTACT":
                        try:
                            # Log payload for debugging
                            logger.info(f"Processing ADD_CONTACT payload: {payload}")
                            insert_db("INSERT OR REPLACE INTO contacts (contactId, villaId, contactName, contactPhone, contactType, updatedAt, deviceId) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                      (payload.get("contactId"), payload.get("villaId"), payload.get("contactName"), 
                                       payload.get("contactPhone"), payload.get("contactType"), payload.get("updatedAt"), payload.get("deviceId")))
                            logger.info(f"Saved Contact: {payload.get('contactName')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)
                            
                    elif msg_type == "DELETE_VILLA":
                        try:
                            villa_id = payload if isinstance(payload, int) else payload.get("villaId")
                            insert_db("DELETE FROM villas WHERE villaId = ?", (villa_id,))
                            logger.info(f"Deleted Villa: {villa_id}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_VILLA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "UPDATE_VILLA":
                        try:
                            # update all non-ID fields.
                            sql = '''UPDATE villas SET 
                                villaNo=?, villaStreet=?, villaNotes=?, deviceId=?, updatedAt=?,
                                isVillaUnderConstruction=?, isVillaSpecial=?, isVillaRental=?, 
                                isVillaCallFromHome=?, isVillaCallForCargo=?, isVillaEmpty=?
                                WHERE villaId=?'''
                            
                            insert_db(sql, (
                                payload.get("villaNo"), payload.get("villaStreet"), payload.get("villaNotes"),
                                payload.get("deviceId"), payload.get("updatedAt"),
                                payload.get("isVillaUnderConstruction", 0), payload.get("isVillaSpecial", 0),
                                payload.get("isVillaRental", 0), payload.get("isVillaCallFromHome", 0),
                                payload.get("isVillaCallForCargo", 0), payload.get("isVillaEmpty", 0),
                                payload.get("villaId")
                            ))
                            logger.info(f"Updated Villa: {payload.get('villaNo')}")
                        except Exception as e:
                            logger.error(f"DB Error (UPDATE_VILLA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "DELETE_CONTACT":
                        try:
                            contact_id = payload if not isinstance(payload, dict) else payload.get("contactId")
                            insert_db("DELETE FROM contacts WHERE contactId = ?", (contact_id,))
                            logger.info(f"Deleted Contact: {contact_id}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "UPDATE_CONTACT":
                        try:
                            insert_db('''UPDATE contacts SET 
                                contactName=?, contactPhone=?, updatedAt=? 
                                WHERE contactId=?''', (
                                payload.get("contactName"), payload.get("contactPhone"), 
                                payload.get("updatedAt"), payload.get("contactId")
                            ))
                            logger.info(f"Updated Contact: {payload.get('contactName')}")
                        except Exception as e:
                             logger.error(f"DB Error (UPDATE_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "ADD_VILLA_CONTACT":
                        try:
                            insert_db('''INSERT OR REPLACE INTO villa_contacts (
                                villaId, contactId, isRealOwner, contactType, notes, updatedAt, deviceId
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)''', (
                                payload.get("villaId"), payload.get("contactId"), payload.get("isRealOwner", 0),
                                payload.get("contactType"), payload.get("notes"), payload.get("updatedAt"), payload.get("deviceId")
                            ))
                            logger.info(f"Saved Villa Contact Link: V{payload.get('villaId')}-C{payload.get('contactId')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_VILLA_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "DELETE_VILLA_CONTACT":
                        try:
                            insert_db("DELETE FROM villa_contacts WHERE villaId = ? AND contactId = ?", 
                                      (payload.get("villaId"), payload.get("contactId")))
                            logger.info(f"Deleted Villa Contact Link: V{payload.get('villaId')}-C{payload.get('contactId')}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_VILLA_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    # --- FAULT TRACKING HANDLERS ---
                    
                    elif msg_type == "ADD_CAMERA":
                        try:
                            insert_db('''INSERT OR REPLACE INTO cameras (
                                cameraId, cameraName, cameraIp, 
                                isWorking, lastChecked, notes, updatedAt, deviceId
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)''', (
                                payload.get("cameraId"), payload.get("cameraName"),
                                payload.get("cameraIp"), payload.get("isWorking", 1), payload.get("lastChecked"),
                                payload.get("notes"), payload.get("updatedAt"), payload.get("deviceId")
                            ))
                            logger.info(f"Saved Camera: {payload.get('cameraName')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_CAMERA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "UPDATE_CAMERA_STATUS":
                        try:
                            # Removed villaId from update
                            insert_db('''UPDATE cameras SET 
                                isWorking=?, lastChecked=?, notes=?, updatedAt=?, deviceId=?
                                WHERE cameraId=?''', (
                                payload.get("isWorking"), payload.get("lastChecked"), payload.get("notes"),
                                payload.get("updatedAt"), payload.get("deviceId"), payload.get("cameraId")
                            ))
                            logger.info(f"Updated Camera Status: {payload.get('cameraId')}")
                        except Exception as e:
                            logger.error(f"DB Error (UPDATE_CAMERA_STATUS): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)
                        
                    elif msg_type == "DELETE_CAMERA":
                        try:
                            id = payload if not isinstance(payload, dict) else payload.get("cameraId")
                            insert_db("DELETE FROM cameras WHERE cameraId = ?", (id,))
                            logger.info(f"Deleted Camera: {id}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_CAMERA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    # Camera-Villa Relations
                    elif msg_type == "ADD_CAMERA_VISIBLE_VILLA":
                        try:
                            insert_db('''INSERT OR REPLACE INTO camera_visible_villas (
                                cameraId, villaId, updatedAt, deviceId
                            ) VALUES (?, ?, ?, ?)''', (
                                payload.get("cameraId"), payload.get("villaId"),
                                payload.get("updatedAt"), payload.get("deviceId")
                            ))
                            logger.info(f"Linked Camera {payload.get('cameraId')} -> Villa {payload.get('villaId')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_CAMERA_VISIBLE_VILLA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)
                        
                    elif msg_type == "DELETE_CAMERA_VISIBLE_VILLA":
                        try:
                            insert_db("DELETE FROM camera_visible_villas WHERE cameraId = ? AND villaId = ?", 
                                      (payload.get("cameraId"), payload.get("villaId")))
                            logger.info(f"Unlinked Camera {payload.get('cameraId')} from Villa {payload.get('villaId')}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_CAMERA_VISIBLE_VILLA): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                        
                    elif msg_type == "ADD_INTERCOM":
                        try:
                            insert_db('''INSERT OR REPLACE INTO intercoms (
                                intercomId, villaId, intercomName, 
                                isWorking, lastChecked, notes, updatedAt, deviceId
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)''', (
                                payload.get("intercomId"), payload.get("villaId"), payload.get("intercomName"),
                                payload.get("isWorking", 1), payload.get("lastChecked"), payload.get("notes"),
                                payload.get("updatedAt"), payload.get("deviceId")
                            ))
                            logger.info(f"Saved Intercom: {payload.get('intercomName')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_INTERCOM): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "UPDATE_INTERCOM_STATUS":
                        try:
                            insert_db('''UPDATE intercoms SET 
                                isWorking=?, lastChecked=?, notes=?, updatedAt=?, deviceId=?
                                WHERE intercomId=?''', (
                                payload.get("isWorking"), payload.get("lastChecked"), payload.get("notes"),
                                payload.get("updatedAt"), payload.get("deviceId"), payload.get("intercomId")
                            ))
                            logger.info(f"Updated Intercom Status: {payload.get('intercomId')}")
                        except Exception as e:
                            logger.error(f"DB Error (UPDATE_INTERCOM_STATUS): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)
                        
                    elif msg_type == "DELETE_INTERCOM":
                        try:
                            id = payload if not isinstance(payload, dict) else payload.get("intercomId")
                            insert_db("DELETE FROM intercoms WHERE intercomId = ?", (id,))
                            logger.info(f"Deleted Intercom: {id}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_INTERCOM): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    # --- COMPANY MANAGEMENT ---

                    elif msg_type == "UPDATE_COMPANY":
                        try:
                            insert_db("UPDATE companies SET companyName=?, isCargoInOperation=?, updatedAt=?, deviceId=? WHERE companyId=?", 
                                      (payload.get("companyName"), payload.get("isCargoInOperation"), payload.get("updatedAt"), payload.get("deviceId"), payload.get("companyId")))
                            logger.info(f"Updated Company: {payload.get('companyName')}")
                        except Exception as e:
                            logger.error(f"DB Error (UPDATE_COMPANY): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "DELETE_COMPANY":
                        try:
                            id = payload if not isinstance(payload, dict) else payload.get("companyId")
                            insert_db("DELETE FROM companies WHERE companyId = ?", (id,))
                            logger.info(f"Deleted Company: {id}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_COMPANY): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    # --- COMPANY DELIVERER LINKS ---

                    elif msg_type == "ADD_COMPANY_CONTACT":
                        try:
                            insert_db('''INSERT OR REPLACE INTO company_contacts (
                                companyId, contactId, role, isPrimaryContact, updatedAt, deviceId
                            ) VALUES (?, ?, ?, ?, ?, ?)''', (
                                payload.get("companyId"), payload.get("contactId"), 
                                payload.get("role"), payload.get("isPrimaryContact", 0),
                                payload.get("updatedAt"), payload.get("deviceId")
                            ))
                            logger.info(f"Linked Company {payload.get('companyId')} -> Contact {payload.get('contactId')}")
                        except Exception as e:
                            logger.error(f"DB Error (ADD_COMPANY_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                    elif msg_type == "DELETE_COMPANY_CONTACT":
                        try:
                            insert_db("DELETE FROM company_contacts WHERE companyId = ? AND contactId = ?", 
                                      (payload.get("companyId"), payload.get("contactId")))
                            logger.info(f"Unlinked Company {payload.get('companyId')} from Contact {payload.get('contactId')}")
                        except Exception as e:
                            logger.error(f"DB Error (DELETE_COMPANY_CONTACT): {e}")
                        await broadcast_exclude(json.dumps(data), client_id)

                except json.JSONDecodeError:
                    pass

    except websockets.exceptions.ConnectionClosed:
        logger.warning(f"Connection closed for {client_id}")
    except Exception as e:
        logger.error(f"Error: {e}")
    finally:
        if client_id and client_id in connected_clients:
            del connected_clients[client_id]
            logger.info(f"❌ Client Disconnected: {client_id}")

async def broadcast_exclude(message, sender_id):
    if not connected_clients:
        return
    
    tasks = []
    for cid, client in connected_clients.items():
        if cid != sender_id: # Don't send back to sender
            tasks.append(asyncio.create_task(client.send(message)))
            
    if tasks:
        await asyncio.gather(*tasks, return_exceptions=True)

async def main():
    init_db() # Init DB on startup
    port = 8765
    logger.info(f"🚀 SecuAsist Server (Persistence Enabled) starting on port {port}...")
    
    # Listen on all interfaces
    async with websockets.serve(handler, "0.0.0.0", port):
        await asyncio.Future()  # Run forever

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Server stopped by user.")
